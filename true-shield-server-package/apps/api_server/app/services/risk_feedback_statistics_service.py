from __future__ import annotations

from collections import Counter
from collections.abc import Iterable
from enum import Enum
from typing import Any
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.risk import RiskFeedback
from app.schemas.risk_feedback_statistics import (
    RiskFeedbackStatisticsResponse,
    RiskFeedbackTypeCounts,
    RiskLevelCounts,
)


def _get_enum_value(value: Any) -> str | None:
    """将枚举值或普通字符串统一转换为字符串。"""

    if value is None:
        return None

    if isinstance(value, Enum):
        return str(value.value)

    return str(value)


class RiskFeedbackStatisticsService:
    """风险反馈统计服务。"""

    async def get_user_statistics(
        self,
        db: AsyncSession,
        *,
        user_id: UUID,
    ) -> RiskFeedbackStatisticsResponse:
        """
        读取指定用户自己的风险反馈并生成统计结果。

        查询必须带 user_id，避免不同用户之间的数据泄露。
        """

        result = await db.execute(
            select(RiskFeedback).where(
                RiskFeedback.user_id == user_id
            )
        )

        feedbacks = result.scalars().all()

        return self.calculate_statistics(feedbacks)

    async def get_global_statistics(
        self,
        db: AsyncSession,
    ) -> RiskFeedbackStatisticsResponse:
        """
        获取全站反馈统计。

        当前暂不通过普通用户接口暴露；
        后续管理员权限系统完成后再调用。
        """

        result = await db.execute(
            select(RiskFeedback)
        )

        feedbacks = result.scalars().all()

        return self.calculate_statistics(feedbacks)

    @staticmethod
    def calculate_statistics(
        feedbacks: Iterable[RiskFeedback],
    ) -> RiskFeedbackStatisticsResponse:
        """根据风险反馈集合计算统计结果。"""

        feedback_list = list(feedbacks)

        feedback_type_counter = Counter(
            feedback_type
            for feedback in feedback_list
            if (
                feedback_type := _get_enum_value(
                    feedback.feedback_type
                )
            )
            is not None
        )

        expected_level_counter = Counter(
            expected_level
            for feedback in feedback_list
            if (
                expected_level := _get_enum_value(
                    feedback.expected_risk_level
                )
            )
            is not None
        )

        total_feedbacks = len(feedback_list)

        accurate_count = feedback_type_counter[
            "accurate"
        ]

        correction_count = (
            feedback_type_counter["false_positive"]
            + feedback_type_counter["false_negative"]
            + feedback_type_counter["risk_too_high"]
            + feedback_type_counter["risk_too_low"]
        )

        if total_feedbacks == 0:
            accurate_rate = 0.0
            correction_rate = 0.0
        else:
            accurate_rate = round(
                accurate_count / total_feedbacks,
                4,
            )

            correction_rate = round(
                correction_count / total_feedbacks,
                4,
            )

        return RiskFeedbackStatisticsResponse(
            total_feedbacks=total_feedbacks,
            type_counts=RiskFeedbackTypeCounts(
                accurate=feedback_type_counter[
                    "accurate"
                ],
                false_positive=feedback_type_counter[
                    "false_positive"
                ],
                false_negative=feedback_type_counter[
                    "false_negative"
                ],
                risk_too_high=feedback_type_counter[
                    "risk_too_high"
                ],
                risk_too_low=feedback_type_counter[
                    "risk_too_low"
                ],
            ),
            expected_risk_level_counts=RiskLevelCounts(
                low=expected_level_counter["low"],
                medium=expected_level_counter[
                    "medium"
                ],
                high=expected_level_counter["high"],
            ),
            accurate_rate=accurate_rate,
            correction_rate=correction_rate,
        )