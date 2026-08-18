from __future__ import annotations

from pydantic import BaseModel, Field


class RiskFeedbackTypeCounts(BaseModel):
    """不同反馈类型的数量统计。"""

    accurate: int = Field(default=0, ge=0)
    false_positive: int = Field(default=0, ge=0)
    false_negative: int = Field(default=0, ge=0)
    risk_too_high: int = Field(default=0, ge=0)
    risk_too_low: int = Field(default=0, ge=0)


class RiskLevelCounts(BaseModel):
    """用户期望风险等级的数量统计。"""

    low: int = Field(default=0, ge=0)
    medium: int = Field(default=0, ge=0)
    high: int = Field(default=0, ge=0)


class RiskFeedbackStatisticsResponse(BaseModel):
    """风险反馈统计接口响应。"""

    total_feedbacks: int = Field(default=0, ge=0)

    type_counts: RiskFeedbackTypeCounts = Field(
        default_factory=RiskFeedbackTypeCounts
    )

    expected_risk_level_counts: RiskLevelCounts = Field(
        default_factory=RiskLevelCounts
    )

    accurate_rate: float = Field(
        default=0.0,
        ge=0.0,
        le=1.0,
    )

    correction_rate: float = Field(
        default=0.0,
        ge=0.0,
        le=1.0,
    )