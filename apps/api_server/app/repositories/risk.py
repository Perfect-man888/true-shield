from __future__ import annotations

import uuid

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.risk import RiskEvent, RiskSignal
from app.schemas.risk import (
    TextRiskAnalysisRequest,
    TextRiskAnalysisResponse,
)


def build_event_summary(
    analysis: TextRiskAnalysisResponse,
) -> str:
    """根据分析结果生成简短事件摘要。"""

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

    await db.commit()
    await db.refresh(event)

    return event


async def list_risk_events(
    db: AsyncSession,
    *,
    user_id: uuid.UUID,
    limit: int,
    offset: int,
) -> tuple[list[RiskEvent], int]:
    """分页查询当前用户的风险事件。"""

    count_statement = (
        select(func.count())
        .select_from(RiskEvent)
        .where(RiskEvent.user_id == user_id)
    )

    total_result = await db.execute(count_statement)
    total = total_result.scalar_one()

    statement = (
        select(RiskEvent)
        .where(RiskEvent.user_id == user_id)
        .options(selectinload(RiskEvent.signals))
        .order_by(RiskEvent.created_at.desc())
        .offset(offset)
        .limit(limit)
    )

    result = await db.execute(statement)

    events = list(
        result.scalars().unique().all()
    )

    return events, total


async def get_risk_event_by_id(
    db: AsyncSession,
    *,
    event_id: uuid.UUID,
    user_id: uuid.UUID,
) -> RiskEvent | None:
    """
    查询当前用户的一条风险事件。

    同时根据 event_id 和 user_id 查询，
    从数据库层面防止用户读取其他人的记录。
    """

    statement = (
        select(RiskEvent)
        .where(
            RiskEvent.id == event_id,
            RiskEvent.user_id == user_id,
        )
        .options(selectinload(RiskEvent.signals))
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()