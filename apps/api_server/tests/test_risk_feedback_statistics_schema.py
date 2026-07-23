from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.risk_feedback_statistics import (
    RiskFeedbackStatisticsResponse,
    RiskFeedbackTypeCounts,
)


def test_statistics_schema_has_zero_defaults() -> None:
    """没有反馈数据时，各项统计应默认为零。"""

    result = RiskFeedbackStatisticsResponse()

    assert result.total_feedbacks == 0

    assert result.type_counts.accurate == 0
    assert result.type_counts.false_positive == 0
    assert result.type_counts.false_negative == 0
    assert result.type_counts.risk_too_high == 0
    assert result.type_counts.risk_too_low == 0

    assert result.expected_risk_level_counts.low == 0
    assert result.expected_risk_level_counts.medium == 0
    assert result.expected_risk_level_counts.high == 0

    assert result.accurate_rate == 0.0
    assert result.correction_rate == 0.0


def test_statistics_schema_accepts_valid_data() -> None:
    """统计响应模型可以接收正常数据。"""

    result = RiskFeedbackStatisticsResponse(
        total_feedbacks=10,
        type_counts={
            "accurate": 4,
            "false_positive": 2,
            "false_negative": 1,
            "risk_too_high": 2,
            "risk_too_low": 1,
        },
        expected_risk_level_counts={
            "low": 3,
            "medium": 1,
            "high": 2,
        },
        accurate_rate=0.4,
        correction_rate=0.6,
    )

    assert result.total_feedbacks == 10
    assert result.type_counts.accurate == 4
    assert result.type_counts.false_positive == 2
    assert result.type_counts.false_negative == 1
    assert result.type_counts.risk_too_high == 2
    assert result.type_counts.risk_too_low == 1

    assert result.expected_risk_level_counts.low == 3
    assert result.expected_risk_level_counts.medium == 1
    assert result.expected_risk_level_counts.high == 2

    assert result.accurate_rate == 0.4
    assert result.correction_rate == 0.6


def test_statistics_schema_rejects_negative_counts() -> None:
    """任何统计数量都不允许为负数。"""

    with pytest.raises(ValidationError):
        RiskFeedbackTypeCounts(
            false_positive=-1,
        )


def test_statistics_schema_rejects_invalid_rates() -> None:
    """准确率和纠正率必须位于 0 到 1 之间。"""

    with pytest.raises(ValidationError):
        RiskFeedbackStatisticsResponse(
            accurate_rate=1.1,
        )

    with pytest.raises(ValidationError):
        RiskFeedbackStatisticsResponse(
            correction_rate=-0.1,
        )