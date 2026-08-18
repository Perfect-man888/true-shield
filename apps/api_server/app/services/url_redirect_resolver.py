from __future__ import annotations

import asyncio
import ipaddress
import socket
from urllib.parse import (
    urljoin,
    urlsplit,
)

import httpx

from app.schemas.url_redirect import (
    URLRedirectHop,
    URLRedirectResolutionResponse,
)


class URLRedirectResolutionError(ValueError):
    """链接重定向解析失败。"""


class UnsafeURLTargetError(
    URLRedirectResolutionError
):
    """链接目标地址不允许访问。"""


class URLRedirectResolver:
    """安全解析 HTTP/HTTPS 链接的重定向链。"""

    REDIRECT_STATUS_CODES = {
        301,
        302,
        303,
        307,
        308,
    }

    ALLOWED_SCHEMES = {
        "http",
        "https",
    }

    ALLOWED_PORTS = {
        80,
        443,
    }

    BLOCKED_HOST_NAMES = {
        "localhost",
        "localhost.localdomain",
    }

    def __init__(
        self,
        *,
        max_redirects: int = 5,
        timeout_seconds: float = 5.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        """初始化重定向解析器。"""

        if max_redirects < 0:
            raise ValueError(
                "max_redirects 不能小于 0。"
            )

        if timeout_seconds <= 0:
            raise ValueError(
                "timeout_seconds 必须大于 0。"
            )

        self.max_redirects = max_redirects
        self.timeout_seconds = timeout_seconds
        self.transport = transport

    async def resolve(
        self,
        url: str,
    ) -> URLRedirectResolutionResponse:
        """安全地逐跳解析链接重定向。"""

        original_url = url.strip()

        if not original_url:
            raise URLRedirectResolutionError(
                "URL 不能为空。"
            )

        current_url = self._prepare_url(
            original_url
        )

        hops: list[URLRedirectHop] = []
        redirect_count = 0

        timeout = httpx.Timeout(
            self.timeout_seconds
        )

        limits = httpx.Limits(
            max_connections=5,
            max_keepalive_connections=2,
        )

        async with httpx.AsyncClient(
            follow_redirects=False,
            timeout=timeout,
            limits=limits,
            transport=self.transport,
            trust_env=False,
            headers={
                "User-Agent": (
                    "TrueShield-LinkAnalyzer/1.0"
                ),
                "Accept": (
                    "text/html,"
                    "application/xhtml+xml,"
                    "application/json;q=0.9,"
                    "*/*;q=0.1"
                ),
            },
        ) as client:
            while True:
                parsed = urlsplit(current_url)

                await self._validate_target(
                    parsed
                )

                host = parsed.hostname

                if host is None:
                    raise URLRedirectResolutionError(
                        "URL 格式无效，无法识别主机名。"
                    )

                try:
                    async with client.stream(
                        "GET",
                        current_url,
                    ) as response:
                        status_code = (
                            response.status_code
                        )

                        location = (
                            response.headers.get(
                                "location"
                            )
                        )

                except httpx.TimeoutException as exc:
                    raise URLRedirectResolutionError(
                        "链接请求超时。"
                    ) from exc

                except httpx.RequestError as exc:
                    raise URLRedirectResolutionError(
                        "无法连接到目标链接。"
                    ) from exc

                hops.append(
                    URLRedirectHop(
                        index=len(hops),
                        url=current_url,
                        host=host.lower(),
                        status_code=status_code,
                        location=location,
                    )
                )

                is_redirect = (
                    status_code
                    in self.REDIRECT_STATUS_CODES
                    and location is not None
                )

                if not is_redirect:
                    return (
                        URLRedirectResolutionResponse(
                            original_url=original_url,
                            final_url=current_url,
                            final_status_code=(
                                status_code
                            ),
                            redirect_count=(
                                redirect_count
                            ),
                            completed=True,
                            hops=hops,
                        )
                    )

                if (
                    redirect_count
                    >= self.max_redirects
                ):
                    raise URLRedirectResolutionError(
                        "链接跳转次数超过允许上限。"
                    )

                next_url = urljoin(
                    current_url,
                    location,
                )

                redirect_count += 1
                current_url = next_url

    @staticmethod
    def _prepare_url(
        url: str,
    ) -> str:
        """为缺失协议的链接补全 HTTPS。"""

        parsed = urlsplit(url)

        if parsed.scheme:
            return url

        return f"https://{url}"

    async def _validate_target(
        self,
        parsed,
    ) -> None:
        """检查每一次请求目标是否允许访问。"""

        scheme = parsed.scheme.lower()

        if scheme not in self.ALLOWED_SCHEMES:
            raise UnsafeURLTargetError(
                "只允许解析 HTTP 或 HTTPS 链接。"
            )

        host = parsed.hostname

        if not host:
            raise URLRedirectResolutionError(
                "URL 格式无效，无法识别主机名。"
            )

        normalized_host = host.rstrip(".").lower()

        if (
            normalized_host
            in self.BLOCKED_HOST_NAMES
            or normalized_host.endswith(
                ".localhost"
            )
        ):
            raise UnsafeURLTargetError(
                "不允许访问本机或内部服务地址。"
            )

        if (
            parsed.username is not None
            or parsed.password is not None
        ):
            raise UnsafeURLTargetError(
                "重定向解析不允许包含用户名"
                "或密码的链接。"
            )

        try:
            explicit_port = parsed.port
        except ValueError as exc:
            raise URLRedirectResolutionError(
                "URL 端口格式无效。"
            ) from exc

        effective_port = explicit_port

        if effective_port is None:
            effective_port = (
                443
                if scheme == "https"
                else 80
            )

        if (
            effective_port
            not in self.ALLOWED_PORTS
        ):
            raise UnsafeURLTargetError(
                "重定向解析仅允许使用 "
                "80 或 443 端口。"
            )

        addresses = await self._resolve_addresses(
            normalized_host,
            effective_port,
        )

        if not addresses:
            raise URLRedirectResolutionError(
                "无法解析目标域名的 IP 地址。"
            )

        for raw_address in addresses:
            self._validate_ip_address(
                raw_address
            )

    @staticmethod
    async def _resolve_addresses(
        host: str,
        port: int,
    ) -> set[str]:
        """解析目标主机的全部 IP 地址。"""

        try:
            direct_address = (
                ipaddress.ip_address(host)
            )
        except ValueError:
            direct_address = None

        if direct_address is not None:
            return {
                str(direct_address),
            }

        loop = asyncio.get_running_loop()

        try:
            address_info = await loop.getaddrinfo(
                host,
                port,
                family=socket.AF_UNSPEC,
                type=socket.SOCK_STREAM,
            )
        except socket.gaierror as exc:
            raise URLRedirectResolutionError(
                "目标域名解析失败。"
            ) from exc

        return {
            item[4][0]
            for item in address_info
        }

    @staticmethod
    def _validate_ip_address(
        raw_address: str,
    ) -> None:
        """拒绝本机、内网和特殊用途 IP 地址。"""

        try:
            address = ipaddress.ip_address(
                raw_address
            )
        except ValueError as exc:
            raise URLRedirectResolutionError(
                "目标域名返回了无效 IP 地址。"
            ) from exc

        unsafe = (
            address.is_private
            or address.is_loopback
            or address.is_link_local
            or address.is_multicast
            or address.is_reserved
            or address.is_unspecified
            or not address.is_global
        )

        if unsafe:
            raise UnsafeURLTargetError(
                "不允许访问本机、内网或"
                f"特殊用途地址：{address}。"
            )