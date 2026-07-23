from __future__ import annotations

import uuid
from datetime import datetime
from enum import Enum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)

from app.schemas.risk import RiskLevel


class RiskFeedbackType(str, Enum):
    """用户可以提交的风险反馈类型。"""

    ACCURATE = "accurate"
    FALSE_POSITIVE = "false_positive"
    FALSE_NEGATIVE = "false_negative"
    RISK_TOO_HIGH = "risk_too_high"
    RISK_TOO_LOW = "risk_too_low"


class RiskFeedbackCreate(BaseModel):
    """创建或更新风险反馈时使用的请求模型。"""

    feedback_type: RiskFeedbackType

    expected_risk_level: RiskLevel | None = None

    comment: str | None = Field(
        default=None,
        max_length=1000,
    )

    @field_validator("comment")
    @classmethod
    def normalize_comment(
        cls,
        value: str | None,
    ) -> str | None:
        """去除反馈说明两侧的空白字符。"""

        if value is None:
            return None

        normalized_value = value.strip()

        if not normalized_value:
            return None

        return normalized_value

    @model_validator(mode="after")
    def validate_expected_risk_level(
        self,
    ) -> RiskFeedbackCreate:
        """
        校验用户认为正确的风险等级。

        误报默认表示正确结果应当为 low；
        漏报或风险等级不正确时，必须提供期望等级。
        """

        if (
            self.feedback_type
            == RiskFeedbackType.FALSE_POSITIVE
            and self.expected_risk_level is None
        ):
            self.expected_risk_level = RiskLevel.LOW

        feedback_types_requiring_level = {
            RiskFeedbackType.FALSE_NEGATIVE,
            RiskFeedbackType.RISK_TOO_HIGH,
            RiskFeedbackType.RISK_TOO_LOW,
        }

        if (
            self.feedback_type
            in feedback_types_requiring_level
            and self.expected_risk_level is None
        ):
            raise ValueError(
                "该反馈类型必须提供 expected_risk_level。"
            )

        if (
            self.feedback_type
            == RiskFeedbackType.FALSE_NEGATIVE
            and self.expected_risk_level
            == RiskLevel.LOW
        ):
            raise ValueError(
                "漏报反馈的 expected_risk_level "
                "不能为 low。"
            )

        return self


class RiskFeedbackResponse(BaseModel):
    """风险反馈接口返回模型。"""

    model_config = ConfigDict(
        from_attributes=True,
    )

    id: uuid.UUID
    event_id: uuid.UUID
    user_id: uuid.UUID

    feedback_type: RiskFeedbackType
    expected_risk_level: RiskLevel | None

    comment: str | None

    created_at: datetime
    updated_at: datetime