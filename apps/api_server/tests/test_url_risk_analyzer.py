from __future__ import annotations

import pytest

from app.schemas.risk import RiskLevel
from app.schemas.risk_url import (
    URLRiskAnalysisRequest,
)
from app.services.url_risk_analyzer import (
    URLRiskAnalyzer,
)

analyzer = URLRiskAnalyzer()


def analyze(
    url: str,
):
    """分析测试链接。"""

    return analyzer.analyze(
        URLRiskAnalysisRequest(
            url=url,
        )
    )


def test_safe_https_url_is_low_risk() -> None:
    """普通 HTTPS 链接应为低风险。"""

    result = analyze(
        "https://www.example.com/news"
    )

    assert result.risk_level == RiskLevel.LOW
    assert result.score == 0
    assert result.host == "www.example.com"
    assert result.signals == []


def test_ip_address_with_credentials_is_high_risk() -> None:
    """IP 地址加账号结构应判定为高风险。"""

    result = analyze(
        "http://admin:123456@192.168.1.10/login"
    )

    assert result.risk_level == RiskLevel.HIGH
    assert result.score >= 60

    signal_ids = {
        signal.signal_id
        for signal in result.signals
    }

    assert "URL-002" in signal_ids
    assert "URL-004" in signal_ids
    assert "URL-005" in signal_ids
    assert "URL-008" in signal_ids


def test_punycode_login_link_is_high_risk() -> None:
    """Punycode 域名结合登录词应为高风险。"""

    result = analyze(
        "https://xn--paypa-4ve.com/login/verify"
    )

    assert result.risk_level == RiskLevel.HIGH
    assert result.score >= 60

    signal_ids = {
        signal.signal_id
        for signal in result.signals
    }

    assert "URL-006" in signal_ids
    assert "URL-008" in signal_ids


def test_missing_scheme_is_normalized() -> None:
    """缺少协议时应自动按 HTTPS 解析。"""

    result = analyze(
        "example.com/news"
    )

    assert result.risk_level == RiskLevel.LOW
    assert result.score == 5

    assert result.normalized_url == (
        "https://example.com/news"
    )

    assert result.signals[0].signal_id == (
        "URL-001"
    )


def test_invalid_url_raises_value_error() -> None:
    """无法识别主机名的链接应报错。"""

    with pytest.raises(
        ValueError,
        match="无法识别主机名",
    ):
        analyze(
            "https:///only-path"
        )

def test_executable_download_link_is_high_risk() -> None:
    result = analyzer.analyze(
        URLRiskAnalysisRequest(
            url="http://198.51.100.8/download/security-update.apk",
        )
    )

    assert result.risk_level == RiskLevel.HIGH
    assert "URL-013" in {
        signal.signal_id
        for signal in result.signals
    }


def test_nested_redirect_url_is_detected() -> None:
    result = analyzer.analyze(
        URLRiskAnalysisRequest(
            url=(
                "https://example.com/go?target="
                "https%3A%2F%2Funknown.example%2Flogin"
            ),
        )
    )

    assert "URL-014" in {
        signal.signal_id
        for signal in result.signals
    }
