from __future__ import annotations

from app.schemas.risk import RiskLevel
from app.schemas.risk_url import (
    URLRiskAnalysisRequest,
)
from app.schemas.url_redirect import (
    URLRedirectHop,
    URLRedirectResolutionResponse,
)
from app.services.url_redirect_risk_service import (
    enrich_url_analysis_with_redirects,
)
from app.services.url_risk_analyzer import (
    URLRiskAnalyzer,
)

analyzer = URLRiskAnalyzer()


def test_multiple_cross_domain_redirects_raise_risk() -> None:
    """多次跨域跳转应增加 URL 风险。"""

    analysis = analyzer.analyze(
        URLRiskAnalysisRequest(
            url="https://example.com/start",
        )
    )

    resolution = URLRedirectResolutionResponse(
        original_url="https://example.com/start",
        final_url="https://final.example/login",
        final_status_code=200,
        redirect_count=2,
        completed=True,
        hops=[
            URLRedirectHop(
                index=0,
                url="https://example.com/start",
                host="example.com",
                status_code=302,
                location="https://redirect.example/next",
            ),
            URLRedirectHop(
                index=1,
                url="https://redirect.example/next",
                host="redirect.example",
                status_code=302,
                location="https://final.example/login",
            ),
            URLRedirectHop(
                index=2,
                url="https://final.example/login",
                host="final.example",
                status_code=200,
                location=None,
            ),
        ],
    )

    result = enrich_url_analysis_with_redirects(
        analysis,
        resolution,
    )

    assert result.risk_level == RiskLevel.MEDIUM
    assert result.score == 25

    signal_ids = {
        signal.signal_id
        for signal in result.signals
    }

    assert "URL-013" in signal_ids
    assert "URL-014" in signal_ids


def test_single_same_domain_redirect_adds_no_risk() -> None:
    """同域的一次正常跳转不额外加分。"""

    analysis = analyzer.analyze(
        URLRiskAnalysisRequest(
            url="https://example.com/start",
        )
    )

    resolution = URLRedirectResolutionResponse(
        original_url="https://example.com/start",
        final_url="https://example.com/home",
        final_status_code=200,
        redirect_count=1,
        completed=True,
        hops=[
            URLRedirectHop(
                index=0,
                url="https://example.com/start",
                host="example.com",
                status_code=302,
                location="/home",
            ),
            URLRedirectHop(
                index=1,
                url="https://example.com/home",
                host="example.com",
                status_code=200,
                location=None,
            ),
        ],
    )

    result = enrich_url_analysis_with_redirects(
        analysis,
        resolution,
    )

    assert result.risk_level == RiskLevel.LOW
    assert result.score == 0
    assert result.signals == []