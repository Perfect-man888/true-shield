from __future__ import annotations

import uuid
from datetime import datetime
from enum import StrEnum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)

from app.schemas.risk import RiskLevel


class RiskFeedbackType(StrEnum):
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

    ocr_text_accurate: bool | None = Field(
        default=None,
        description="用户是否确认 OCR 文字识别准确",
    )

    corrected_text: str | None = Field(
        default=None,
        max_length=10000,
        description="用户人工修正后的 OCR 完整文字",
    )

    @field_validator("corrected_text")
    @classmethod
    def normalize_corrected_text(
        cls,
        value: str | None,
    ) -> str | None:
        """去除纠错文本首尾空白并拒绝空字符串。"""

        if value is None:
            return None

        normalized = value.strip()

        if not normalized:
            raise ValueError(
                "corrected_text 不能为空字符串。"
            )

        return normalized

    @model_validator(mode="after")
    def validate_ocr_correction(
        self,
    ) -> RiskFeedbackCreate:
        """校验 OCR 准确性与纠错文本之间的关系。"""

        if (
            self.ocr_text_accurate is False
            and self.corrected_text is None
        ):
            raise ValueError(
                "OCR 识别不准确时必须提供 corrected_text。"
            )

        if (
            self.ocr_text_accurate is True
            and self.corrected_text is not None
        ):
            raise ValueError(
                "OCR 识别准确时不应提供 corrected_text。"
            )

        if (
            self.ocr_text_accurate is None
            and self.corrected_text is not None
        ):
            raise ValueError(
                "提供 corrected_text 时必须明确 "
                "ocr_text_accurate=false。"
            )

        return self

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

    ocr_text_accurate: bool | None = None
    corrected_text: str | None = None

    created_at: datetime
    updated_at: datetime