from __future__ import annotations

import ipaddress
from urllib.parse import (
    SplitResult,
    parse_qsl,
    unquote,
    urlsplit,
    urlunsplit,
)

from app.schemas.risk import RiskLevel
from app.schemas.risk_url import (
    URLRiskAnalysisRequest,
    URLRiskAnalysisResponse,
    URLRiskSignal,
)


class URLRiskAnalyzer:
    """基于 URL 结构特征分析链接风险。"""

    RULE_VERSION = "url-2.0.0"

    SHORTENER_DOMAINS = {
        "bit.ly",
        "t.co",
        "tinyurl.com",
        "goo.gl",
        "is.gd",
        "cutt.ly",
        "reurl.cc",
        "t.cn",
        "dwz.cn",
        "url.cn",
    }

    SUSPICIOUS_TERMS = {
        "login",
        "signin",
        "verify",
        "verification",
        "security",
        "secure",
        "account",
        "password",
        "wallet",
        "bank",
        "payment",
        "refund",
        "bonus",
        "prize",
        "gift",
        "claim",
        "客服",
        "退款",
        "验证",
        "中奖",
        "账户",
        "银行卡",
        "安全中心",
    }

    SAFE_STANDARD_PORTS = {
        None,
        80,
        443,
    }

    SUSPICIOUS_FILE_EXTENSIONS = {
        ".apk",
        ".exe",
        ".scr",
        ".msi",
        ".bat",
        ".cmd",
        ".jar",
    }

    def analyze(
        self,
        request: URLRiskAnalysisRequest,
    ) -> URLRiskAnalysisResponse:
        """分析链接并返回风险结果。"""

        original_url = request.url

        prepared_url, missing_scheme = (
            self._prepare_url(original_url)
        )

        parsed = urlsplit(prepared_url)

        host = parsed.hostname

        if not host:
            raise ValueError(
                "URL 格式无效，无法识别主机名。"
            )

        host = host.rstrip(".").lower()

        ascii_host = self._to_ascii_host(host)

        normalized_url = self._build_normalized_url(
            parsed
        )

        signals: list[URLRiskSignal] = []

        if missing_scheme:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-001",
                    category="format",
                    title="链接未明确提供协议",
                    score=5,
                    explanation=(
                        "原始链接没有明确写出 http 或 "
                        "https，系统已按 https 进行解析。"
                    ),
                    details={
                        "assumed_scheme": "https",
                    },
                )
            )

        if parsed.scheme.lower() == "http":
            signals.append(
                URLRiskSignal(
                    signal_id="URL-002",
                    category="transport",
                    title="链接未使用 HTTPS",
                    score=10,
                    explanation=(
                        "该链接使用明文 HTTP，传输内容"
                        "可能被窃听或篡改。"
                    ),
                    details={
                        "scheme": "http",
                    },
                )
            )

        if parsed.scheme.lower() not in {
            "http",
            "https",
        }:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-003",
                    category="protocol",
                    title="链接使用异常协议",
                    score=60,
                    explanation=(
                        "该链接未使用常见的 HTTP 或 "
                        "HTTPS 协议。"
                    ),
                    details={
                        "scheme": parsed.scheme,
                    },
                )
            )

        if (
            parsed.username is not None
            or parsed.password is not None
        ):
            signals.append(
                URLRiskSignal(
                    signal_id="URL-004",
                    category="obfuscation",
                    title="链接中包含账号凭据结构",
                    score=35,
                    explanation=(
                        "链接在主机名前包含用户名或密码，"
                        "可能被用于隐藏真实访问地址。"
                    ),
                    details={
                        "contains_username": (
                            parsed.username is not None
                        ),
                        "contains_password": (
                            parsed.password is not None
                        ),
                    },
                )
            )

        if self._is_ip_address(host):
            signals.append(
                URLRiskSignal(
                    signal_id="URL-005",
                    category="host",
                    title="链接直接使用 IP 地址",
                    score=25,
                    explanation=(
                        "正常公开服务通常使用可核验域名，"
                        "直接使用 IP 地址的链接需要警惕。"
                    ),
                    details={
                        "host": host,
                    },
                )
            )

        if "xn--" in ascii_host:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-006",
                    category="obfuscation",
                    title="域名包含 Punycode 编码",
                    score=45,
                    explanation=(
                        "该域名包含国际化域名编码，"
                        "可能存在仿冒相似字符风险。"
                    ),
                    details={
                        "ascii_host": ascii_host,
                    },
                )
            )

        if ascii_host in self.SHORTENER_DOMAINS:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-007",
                    category="redirect",
                    title="链接使用短网址服务",
                    score=20,
                    explanation=(
                        "短网址隐藏了最终访问地址，"
                        "打开前无法直接确认真实目标。"
                    ),
                    details={
                        "host": ascii_host,
                    },
                )
            )

        suspicious_terms = self._find_suspicious_terms(
            parsed,
            host,
        )

        if suspicious_terms:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-008",
                    category="content",
                    title="链接包含敏感诱导词",
                    score=20,
                    explanation=(
                        "链接地址中出现登录、验证、退款、"
                        "账户或中奖等高风险诱导词。"
                    ),
                    details={
                        "matched_terms": suspicious_terms,
                    },
                )
            )

        if self._has_excessive_subdomains(
            ascii_host
        ):
            signals.append(
                URLRiskSignal(
                    signal_id="URL-009",
                    category="host",
                    title="域名层级过多",
                    score=10,
                    explanation=(
                        "域名包含过多子域层级，可能通过"
                        "复杂结构掩盖真实注册域名。"
                    ),
                    details={
                        "host": ascii_host,
                    },
                )
            )

        port = self._get_port(parsed)

        if port not in self.SAFE_STANDARD_PORTS:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-010",
                    category="network",
                    title="链接使用非常用端口",
                    score=15,
                    explanation=(
                        "该链接使用非常规网络端口，"
                        "需要确认服务来源和用途。"
                    ),
                    details={
                        "port": port,
                    },
                )
            )

        if len(prepared_url) > 150:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-011",
                    category="obfuscation",
                    title="链接长度异常",
                    score=10,
                    explanation=(
                        "该链接较长，可能包含大量跟踪、"
                        "跳转或混淆参数。"
                    ),
                    details={
                        "length": len(prepared_url),
                    },
                )
            )

        if ascii_host.count("-") >= 3:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-012",
                    category="host",
                    title="域名包含大量连接符",
                    score=10,
                    explanation=(
                        "域名中包含多个连接符，可能用于"
                        "构造与正规网站相似的仿冒域名。"
                    ),
                    details={
                        "hyphen_count": (
                            ascii_host.count("-")
                        ),
                    },
                )
            )

        suspicious_extension = (
            self._find_suspicious_file_extension(
                parsed
            )
        )

        if suspicious_extension is not None:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-013",
                    category="download",
                    title="链接指向可执行安装文件",
                    score=40,
                    explanation=(
                        "链接路径或参数指向 APK、EXE 等"
                        "可执行文件，可能诱导安装恶意软件。"
                    ),
                    details={
                        "extension": suspicious_extension,
                    },
                )
            )

        nested_urls = self._find_nested_urls(parsed)

        if nested_urls:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-014",
                    category="redirect",
                    title="链接参数中嵌套了其他网址",
                    score=20,
                    explanation=(
                        "查询参数中包含另一个完整网址，"
                        "可能用于隐藏最终跳转目标。"
                    ),
                    details={
                        "nested_urls": nested_urls[:3],
                    },
                )
            )

        encoded_ratio = self._encoded_character_ratio(
            prepared_url
        )

        if encoded_ratio >= 0.12:
            signals.append(
                URLRiskSignal(
                    signal_id="URL-015",
                    category="obfuscation",
                    title="链接包含较多编码字符",
                    score=15,
                    explanation=(
                        "链接中百分号编码比例较高，可能"
                        "用于隐藏真实路径、参数或跳转地址。"
                    ),
                    details={
                        "encoded_ratio": round(
                            encoded_ratio,
                            3,
                        ),
                    },
                )
            )

        if self._contains_mixed_script_host(host):
            signals.append(
                URLRiskSignal(
                    signal_id="URL-016",
                    category="obfuscation",
                    title="域名混用不同文字字符",
                    score=30,
                    explanation=(
                        "域名同时混用拉丁字母和其他文字"
                        "字符，可能用于构造视觉相似的仿冒域名。"
                    ),
                    details={
                        "host": host,
                    },
                )
            )

        score = min(
            100,
            sum(
                signal.score
                for signal in signals
            ),
        )

        risk_level = self._calculate_level(score)

        summary = self._build_summary(
            signals
        )

        return URLRiskAnalysisResponse(
            original_url=original_url,
            normalized_url=normalized_url,
            host=host,
            risk_level=risk_level,
            score=score,
            summary=summary,
            signals=signals,
            actions=self._build_actions(
                risk_level
            ),
            disclaimer=(
                "本结果仅根据链接结构和已知风险特征"
                "进行提示，不代表已确认该网站违法或"
                "存在诈骗行为。打开链接前仍应通过"
                "官方渠道核实。"
            ),
            rule_version=self.RULE_VERSION,
        )

    @staticmethod
    def _prepare_url(
        url: str,
    ) -> tuple[str, bool]:
        """补全缺失的 URL 协议。"""

        parsed = urlsplit(url)

        if parsed.scheme:
            return url, False

        return f"https://{url}", True

    @staticmethod
    def _build_normalized_url(
        parsed: SplitResult,
    ) -> str:
        """生成去除片段标识的标准化链接。"""

        return urlunsplit(
            (
                parsed.scheme.lower(),
                parsed.netloc.lower(),
                parsed.path or "/",
                parsed.query,
                "",
            )
        )

    @staticmethod
    def _to_ascii_host(
        host: str,
    ) -> str:
        """将国际化域名转换为 ASCII 形式。"""

        try:
            return host.encode(
                "idna"
            ).decode("ascii")
        except UnicodeError:
            return host

    @staticmethod
    def _is_ip_address(
        host: str,
    ) -> bool:
        """判断主机名是否为 IP 地址。"""

        try:
            ipaddress.ip_address(host)
        except ValueError:
            return False

        return True

    def _find_suspicious_terms(
        self,
        parsed: SplitResult,
        host: str,
    ) -> list[str]:
        """查找 URL 中的敏感诱导词。"""

        searchable_text = (
            f"{host}{parsed.path}{parsed.query}"
        ).lower()

        return sorted(
            term
            for term in self.SUSPICIOUS_TERMS
            if term.lower() in searchable_text
        )

    @classmethod
    def _find_suspicious_file_extension(
        cls,
        parsed: SplitResult,
    ) -> str | None:
        searchable = unquote(
            f"{parsed.path}?{parsed.query}"
        ).lower()

        for extension in sorted(
            cls.SUSPICIOUS_FILE_EXTENSIONS
        ):
            if extension in searchable:
                return extension

        return None

    @staticmethod
    def _find_nested_urls(
        parsed: SplitResult,
    ) -> list[str]:
        nested: list[str] = []

        for _, value in parse_qsl(
            parsed.query,
            keep_blank_values=True,
        ):
            decoded = unquote(value).strip()

            if decoded.lower().startswith(
                ("http://", "https://")
            ):
                nested.append(decoded)

        return list(dict.fromkeys(nested))

    @staticmethod
    def _encoded_character_ratio(
        url: str,
    ) -> float:
        if not url:
            return 0.0

        encoded_markers = url.count("%") * 3
        return encoded_markers / len(url)

    @staticmethod
    def _contains_mixed_script_host(
        host: str,
    ) -> bool:
        has_ascii_letter = any(
            "a" <= character.lower() <= "z"
            for character in host
        )
        has_non_ascii_letter = any(
            ord(character) > 127
            and character.isalpha()
            for character in host
        )

        return (
            has_ascii_letter
            and has_non_ascii_letter
        )

    @staticmethod
    def _has_excessive_subdomains(
        host: str,
    ) -> bool:
        """判断域名层级是否过多。"""

        if URLRiskAnalyzer._is_ip_address(host):
            return False

        labels = [
            label
            for label in host.split(".")
            if label
        ]

        return len(labels) >= 5

    @staticmethod
    def _get_port(
        parsed: SplitResult,
    ) -> int | None:
        """安全读取链接端口。"""

        try:
            return parsed.port
        except ValueError:
            return None

    @staticmethod
    def _calculate_level(
        score: int,
    ) -> RiskLevel:
        """根据分数计算风险等级。"""

        if score >= 60:
            return RiskLevel.HIGH

        if score >= 25:
            return RiskLevel.MEDIUM

        return RiskLevel.LOW

    @staticmethod
    def _build_actions(
        risk_level: RiskLevel,
    ) -> list[str]:
        """根据风险等级生成操作建议。"""

        if risk_level == RiskLevel.HIGH:
            return [
                "立即停止打开或继续访问该链接。",
                "不要输入账号、密码、验证码或银行卡信息。",
                "通过官方网站或官方应用重新查找对应服务。",
                "如已提交敏感信息，请立即修改密码并联系相关机构。",
            ]

        if risk_level == RiskLevel.MEDIUM:
            return [
                "不要直接在该页面登录、付款或提交验证码。",
                "核对域名拼写，并通过官方渠道确认链接真实性。",
                "不要仅凭页面外观判断网站是否可信。",
            ]

        return [
            "暂未发现明显的高风险链接结构。",
            "打开链接前仍应核对域名和信息来源。",
            "涉及登录或付款时，优先使用官方应用或手动输入官网地址。",
        ]

    @staticmethod
    def _build_summary(
        signals: list[URLRiskSignal],
    ) -> str:
        """根据 URL 风险信号生成摘要。"""

        if not signals:
            return "暂未发现明显的链接结构风险信号。"

        titles = [
            signal.title
            for signal in signals[:3]
        ]

        title_text = "、".join(titles)

        return (
            f"检测到 {len(signals)} 项链接风险信号："
            f"{title_text}。"
        )