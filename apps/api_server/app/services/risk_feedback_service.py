from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.risk import (
    RiskEvent,
    RiskFeedback,
)
from app.schemas.risk_feedback import (
    RiskFeedbackCreate,
)


class OCRFeedbackNotAllowedError(ValueError):
    """当前风险事件不允许提交 OCR 纠错反馈。"""


async def get_owned_risk_event(
    session: AsyncSession,
    *,
    event_id: uuid.UUID,
    user_id: uuid.UUID,
) -> RiskEvent | None:
    """查询当前用户拥有的风险事件。"""

    statement = select(RiskEvent).where(
        RiskEvent.id == event_id,
        RiskEvent.user_id == user_id,
    )

    result = await session.execute(statement)

    return result.scalar_one_or_none()


async def get_risk_feedback(
    session: AsyncSession,
    *,
    event_id: uuid.UUID,
    user_id: uuid.UUID,
) -> RiskFeedback | None:
    """查询当前用户对指定事件提交的反馈。"""

    statement = select(RiskFeedback).where(
        RiskFeedback.event_id == event_id,
        RiskFeedback.user_id == user_id,
    )

    result = await session.execute(statement)

    return result.scalar_one_or_none()


async def upsert_risk_feedback(
    session: AsyncSession,
    *,
    event_id: uuid.UUID,
    user_id: uuid.UUID,
    payload: RiskFeedbackCreate,
) -> RiskFeedback | None:
    """
    创建或更新当前用户的风险反馈。

    返回 None 表示：
    1. 风险事件不存在；
    2. 风险事件不属于当前用户。
    """

    risk_event = await get_owned_risk_event(
        session,
        event_id=event_id,
        user_id=user_id,
    )

    if risk_event is None:
        return None

    has_ocr_feedback = (
        payload.ocr_text_accurate is not None
        or payload.corrected_text is not None
    )

    # 普通文本事件没有 OCR 原文，
    # 因此不允许伪造 OCR 准确性或纠错内容。
    if (
        has_ocr_feedback
        and risk_event.source_type != "image"
    ):
        raise OCRFeedbackNotAllowedError(
            "只有图片风险事件可以提交 OCR 纠错反馈。"
        )

    feedback = await get_risk_feedback(
        session,
        event_id=event_id,
        user_id=user_id,
    )

    if feedback is None:
        feedback = RiskFeedback(
            event_id=event_id,
            user_id=user_id,
            feedback_type=payload.feedback_type.value,
            expected_risk_level=(
                payload.expected_risk_level.value
                if payload.expected_risk_level
                is not None
                else None
            ),
            comment=payload.comment,
            ocr_text_accurate=(
                payload.ocr_text_accurate
            ),
            corrected_text=payload.corrected_text,
        )

        session.add(feedback)

    else:
        feedback.feedback_type = (
            payload.feedback_type.value
        )

        feedback.expected_risk_level = (
            payload.expected_risk_level.value
            if payload.expected_risk_level
            is not None
            else None
        )

        feedback.comment = payload.comment

        # 只有本次请求明确提交 OCR 字段时，
        # 才修改原有 OCR 反馈。
        # 这样用户只更新风险判断时，
        # 不会意外清空之前的 OCR 纠错内容。
        ocr_fields_submitted = bool(
            {
                "ocr_text_accurate",
                "corrected_text",
            }
            & payload.model_fields_set
        )

        if ocr_fields_submitted:
            feedback.ocr_text_accurate = (
                payload.ocr_text_accurate
            )
            feedback.corrected_text = (
                payload.corrected_text
            )

    await session.flush()

    return feedback