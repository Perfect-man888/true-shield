from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.risk import RiskEvent, RiskFeedback
from app.schemas.risk_feedback import RiskFeedbackCreate


async def get_owned_risk_event(
    session: AsyncSession,
    *,
    event_id: uuid.UUID,
    user_id: uuid.UUID,
) -> RiskEvent | None:
    """
    查询属于指定用户的风险事件。

    同时按 event_id 和 user_id 查询，可以避免用户读取或操作
    其他用户的风险事件。
    """

    result = await session.execute(
        select(RiskEvent).where(
            RiskEvent.id == event_id,
            RiskEvent.user_id == user_id,
        )
    )

    return result.scalar_one_or_none()


async def get_risk_feedback(
    session: AsyncSession,
    *,
    event_id: uuid.UUID,
    user_id: uuid.UUID,
) -> RiskFeedback | None:
    """查询用户对指定风险事件提交的反馈。"""

    result = await session.execute(
        select(RiskFeedback).where(
            RiskFeedback.event_id == event_id,
            RiskFeedback.user_id == user_id,
        )
    )

    return result.scalar_one_or_none()


async def upsert_risk_feedback(
    session: AsyncSession,
    *,
    event_id: uuid.UUID,
    user_id: uuid.UUID,
    payload: RiskFeedbackCreate,
) -> RiskFeedback | None:
    """
    创建或更新风险反馈。

    返回 None 表示风险事件不存在，或者不属于当前用户。
    同一用户对同一风险事件再次提交时更新原记录。
    """

    risk_event = await get_owned_risk_event(
        session,
        event_id=event_id,
        user_id=user_id,
    )

    if risk_event is None:
        return None

    feedback = await get_risk_feedback(
        session,
        event_id=event_id,
        user_id=user_id,
    )

    expected_risk_level = (
        payload.expected_risk_level.value
        if payload.expected_risk_level is not None
        else None
    )

    if feedback is None:
        feedback = RiskFeedback(
            event_id=event_id,
            user_id=user_id,
            feedback_type=payload.feedback_type.value,
            expected_risk_level=expected_risk_level,
            comment=payload.comment,
        )

        session.add(feedback)

    else:
        feedback.feedback_type = payload.feedback_type.value
        feedback.expected_risk_level = expected_risk_level
        feedback.comment = payload.comment

    # 服务层只执行 flush，不负责 commit。
    # 是否提交事务由接口层统一控制。
    await session.flush()
    await session.refresh(feedback)

    return feedback