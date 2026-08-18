from __future__ import annotations

from urllib.parse import urlsplit

from app.schemas.risk import RiskLevel
from app.schemas.risk_url import (
    URLRiskAnalysisResponse,
    URLRiskSignal,
)
from app.schemas.url_redirect import (
    URLRedirectResolutionResponse,
)


def enrich_url_analysis_with_redirects(
    analysis: URLRiskAnalysisResponse,
    resolution: URLRedirectResolutionResponse,
) -> URLRiskAnalysisResponse:
    """
    将重定向链风险信号融合进原 URL 分析结果。

    当前新增两类风险：
    1. URL-013：发生多次跳转；
    2. URL-014：跳转后目标域名发生变化。
    """

    signals = list(analysis.signals)

    existing_signal_ids = {
        signal.signal_id
        for signal in signals
    }

    if (
        resolution.redirect_count >= 2
        and "URL-013" not in existing_signal_ids
    ):
        signals.append(
            URLRiskSignal(
                signal_id="URL-013",
                category="redirect",
                title="链接发生多次跳转",
                score=10,
                explanation=(
                    "该链接经过多次重定向后才到达"
                    "最终页面，复杂跳转链可能隐藏"
                    "真实访问目标。"
                ),
                details={
                    "redirect_count": (
                        resolution.redirect_count
                    ),
                },
            )
        )

    original_host = (
        analysis.host
        .rstrip(".")
        .lower()
    )

    final_host = _get_url_host(
        resolution.final_url
    )

    if (
        final_host
        and final_host != original_host
        and "URL-014" not in existing_signal_ids
    ):
        signals.append(
            URLRiskSignal(
                signal_id="URL-014",
                category="redirect",
                title="跳转后目标域名发生变化",
                score=15,
                explanation=(
                    "链接最终访问的域名与原始域名"
                    "不同，需要确认跳转后的目标网站"
                    "是否可信。"
                ),
                details={
                    "original_host": original_host,
                    "final_host": final_host,
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

    risk_level = _calculate_risk_level(
        score
    )

    summary = _build_summary(
        signals
    )

    return analysis.model_copy(
        update={
            "signals": signals,
            "score": score,
            "risk_level": risk_level,
            "summary": summary,
            "actions": _build_actions(
                risk_level
            ),
        }
    )


def _get_url_host(
    url: str,
) -> str:
    """读取 URL 主机名。"""

    host = urlsplit(url).hostname

    if host is None:
        return ""

    return host.rstrip(".").lower()


def _calculate_risk_level(
    score: int,
) -> RiskLevel:
    """根据分数计算风险等级。"""

    if score >= 60:
        return RiskLevel.HIGH

    if score >= 25:
        return RiskLevel.MEDIUM

    return RiskLevel.LOW


def _build_summary(
    signals: list[URLRiskSignal],
) -> str:
    """重新生成融合后的 URL 风险摘要。"""

    if not signals:
        return "暂未发现明显的链接结构风险信号。"

    title_text = "、".join(
        signal.title
        for signal in signals[:3]
    )

    return (
        f"检测到 {len(signals)} 项链接风险信号："
        f"{title_text}。"
    )


def _build_actions(
    risk_level: RiskLevel,
) -> list[str]:
    """生成融合后的安全建议。"""

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
            "核对原始域名和最终跳转域名是否一致。",
            "通过官方网站或官方应用确认链接真实性。",
        ]

    return [
        "暂未发现明显的高风险链接结构。",
        "打开链接前仍应核对域名和信息来源。",
        "涉及登录或付款时，优先使用官方应用或手动输入官网地址。",
    ]