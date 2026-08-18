from __future__ import annotations

import httpx
import pytest

from app.services.url_redirect_resolver import (
    UnsafeURLTargetError,
    URLRedirectResolutionError,
    URLRedirectResolver,
)

pytestmark = pytest.mark.asyncio


class ScriptedAsyncTransport(
    httpx.AsyncBaseTransport
):
    """根据预设响应模拟外部 HTTP 服务。"""

    def __init__(
        self,
        routes: dict[
            str,
            tuple[
                int,
                dict[str, str],
            ],
        ],
    ) -> None:
        self.routes = routes
        self.requested_urls: list[str] = []

    async def handle_async_request(
        self,
        request: httpx.Request,
    ) -> httpx.Response:
        """返回当前 URL 对应的模拟响应。"""

        url = str(request.url)

        self.requested_urls.append(url)

        if url not in self.routes:
            return httpx.Response(
                status_code=404,
                request=request,
            )

        status_code, headers = self.routes[url]

        return httpx.Response(
            status_code=status_code,
            headers=headers,
            content=b"",
            request=request,
        )


async def test_url_without_redirect() -> None:
    """无重定向链接应直接返回最终结果。"""

    url = "https://93.184.216.34/start"

    transport = ScriptedAsyncTransport(
        {
            url: (
                200,
                {},
            ),
        }
    )

    resolver = URLRedirectResolver(
        transport=transport,
    )

    result = await resolver.resolve(url)

    assert result.original_url == url
    assert result.final_url == url
    assert result.final_status_code == 200
    assert result.redirect_count == 0
    assert len(result.hops) == 1

    assert transport.requested_urls == [
        url,
    ]


async def test_multiple_redirects_are_recorded() -> None:
    """多次重定向应完整记录跳转链。"""

    start_url = (
        "https://93.184.216.34/start"
    )

    middle_url = (
        "https://93.184.216.34/middle"
    )

    final_url = "https://1.1.1.1/final"

    transport = ScriptedAsyncTransport(
        {
            start_url: (
                302,
                {
                    "location": "/middle",
                },
            ),
            middle_url: (
                301,
                {
                    "location": final_url,
                },
            ),
            final_url: (
                200,
                {},
            ),
        }
    )

    resolver = URLRedirectResolver(
        transport=transport,
    )

    result = await resolver.resolve(
        start_url
    )

    assert result.final_url == final_url
    assert result.final_status_code == 200
    assert result.redirect_count == 2

    assert [
        hop.status_code
        for hop in result.hops
    ] == [
        302,
        301,
        200,
    ]

    assert transport.requested_urls == [
        start_url,
        middle_url,
        final_url,
    ]


async def test_private_initial_target_is_blocked() -> None:
    """初始链接指向私有地址时应直接拦截。"""

    transport = ScriptedAsyncTransport({})

    resolver = URLRedirectResolver(
        transport=transport,
    )

    with pytest.raises(
        UnsafeURLTargetError,
        match="本机、内网或特殊用途地址",
    ):
        await resolver.resolve(
            "http://127.0.0.1/admin"
        )

    assert transport.requested_urls == []


async def test_redirect_to_private_target_is_blocked() -> None:
    """公开链接跳转到私有地址时应拦截。"""

    start_url = (
        "https://93.184.216.34/start"
    )

    transport = ScriptedAsyncTransport(
        {
            start_url: (
                302,
                {
                    "location": (
                        "http://127.0.0.1/admin"
                    ),
                },
            ),
        }
    )

    resolver = URLRedirectResolver(
        transport=transport,
    )

    with pytest.raises(
        UnsafeURLTargetError,
        match="本机、内网或特殊用途地址",
    ):
        await resolver.resolve(start_url)

    # 私有地址在发出请求前已被拦截。
    assert transport.requested_urls == [
        start_url,
    ]


async def test_too_many_redirects_are_rejected() -> None:
    """跳转次数超过限制时应终止解析。"""

    start_url = (
        "https://93.184.216.34/start"
    )

    first_url = (
        "https://93.184.216.34/first"
    )

    second_url = (
        "https://93.184.216.34/second"
    )

    transport = ScriptedAsyncTransport(
        {
            start_url: (
                302,
                {
                    "location": "/first",
                },
            ),
            first_url: (
                302,
                {
                    "location": "/second",
                },
            ),
            second_url: (
                200,
                {},
            ),
        }
    )

    resolver = URLRedirectResolver(
        max_redirects=1,
        transport=transport,
    )

    with pytest.raises(
        URLRedirectResolutionError,
        match="跳转次数超过允许上限",
    ):
        await resolver.resolve(start_url)

    assert transport.requested_urls == [
        start_url,
        first_url,
    ]