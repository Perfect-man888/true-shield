from __future__ import annotations

import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.risk import RiskEvent, RiskSignal
from app.schemas.risk import (
    TextRiskAnalysisRequest,
    TextRiskAnalysisResponse,
)


def build_event_summary(
    analysis: TextRiskAnalysisResponse,
) -> str:
    """根据分析结果生成简短的事件摘要。"""

    if not analysis.evidence:
        return "暂未检测到明显风险信号。"

    titles = [
        evidence.title
        for evidence in analysis.evidence[:3]
    ]

    title_text = "、".join(titles)

    return (
        f"检测到 {len(analysis.evidence)} 项风险信号："
        f"{title_text}。"
    )


async def create_risk_event(
    db: AsyncSession,
    *,
    user_id: uuid.UUID,
    request: TextRiskAnalysisRequest,
    analysis: TextRiskAnalysisResponse,
) -> RiskEvent:
    """保存一次文本风险分析事件及其全部证据。"""

    event = RiskEvent(
        user_id=user_id,
        source_type="text",
        source_text=request.text,
        risk_level=analysis.risk_level.value,
        risk_score=analysis.score,
        summary=build_event_summary(analysis),
        actions=analysis.actions,
        disclaimer=analysis.disclaimer,
        rule_version=analysis.rule_version,
    )

    event.signals = [
        RiskSignal(
            rule_id=evidence.rule_id,
            signal_type=evidence.category,
            title=evidence.title,
            matched_terms=evidence.matched_terms,
            signal_weight=evidence.score,
            explanation=evidence.explanation,
        )
        for evidence in analysis.evidence
    ]

    db.add(event)

    # 提交 RiskEvent 与其关联的 RiskSignal。
    await db.commit()

    # 重新从数据库读取，确保 created_at 等服务端字段可用。
    await db.refresh(event)

    return event