from __future__ import annotations

from enum import StrEnum
from types import SimpleNamespace

from app.services.risk_feedback_statistics_service import (
    RiskFeedbackStatisticsService,
)


class ExampleFeedbackType(StrEnum):
    ACCURATE = "accurate"
    FALSE_POSITIVE = "false_positive"


class ExampleRiskLevel(StrEnum):
    LOW = "low"
    HIGH = "high"


def make_feedback(
    *,
    feedback_type: object,
    expected_risk_level: object = None,
) -> SimpleNamespace:
    """创建用于统计测试的简易反馈对象。"""

    return SimpleNamespace(
        feedback_type=feedback_type,
        expected_risk_level=expected_risk_level,
    )


def test_empty_feedback_statistics_are_zero() -> None:
    """没有反馈记录时，所有统计值都应为零。"""

    result = (
        RiskFeedbackStatisticsService
        .calculate_statistics([])
    )

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


def test_feedback_types_are_counted_correctly() -> None:
    """不同反馈类型应分别统计。"""

    feedbacks = [
        make_feedback(
            feedback_type="accurate",
        ),
        make_feedback(
            feedback_type="accurate",
        ),
        make_feedback(
            feedback_type="false_positive",
            expected_risk_level="low",
        ),
        make_feedback(
            feedback_type="false_negative",
            expected_risk_level="high",
        ),
        make_feedback(
            feedback_type="risk_too_high",
            expected_risk_level="low",
        ),
        make_feedback(
            feedback_type="risk_too_low",
            expected_risk_level="high",
        ),
    ]

    result = (
        RiskFeedbackStatisticsService
        .calculate_statistics(feedbacks)
    )

    assert result.total_feedbacks == 6

    assert result.type_counts.accurate == 2
    assert result.type_counts.false_positive == 1
    assert result.type_counts.false_negative == 1
    assert result.type_counts.risk_too_high == 1
    assert result.type_counts.risk_too_low == 1

    assert result.expected_risk_level_counts.low == 2
    assert result.expected_risk_level_counts.medium == 0
    assert result.expected_risk_level_counts.high == 2


def test_statistics_rates_are_calculated_correctly() -> None:
    """准确率和纠正率应根据反馈总数计算。"""

    feedbacks = [
        make_feedback(
            feedback_type="accurate",
        ),
        make_feedback(
            feedback_type="accurate",
        ),
        make_feedback(
            feedback_type="accurate",
        ),
        make_feedback(
            feedback_type="false_positive",
            expected_risk_level="low",
        ),
    ]

    result = (
        RiskFeedbackStatisticsService
        .calculate_statistics(feedbacks)
    )

    assert result.total_feedbacks == 4
    assert result.accurate_rate == 0.75
    assert result.correction_rate == 0.25


def test_enum_values_can_be_counted() -> None:
    """服务应同时支持枚举值和普通字符串。"""

    feedbacks = [
        make_feedback(
            feedback_type=ExampleFeedbackType.ACCURATE,
        ),
        make_feedback(
            feedback_type=(
                ExampleFeedbackType.FALSE_POSITIVE
            ),
            expected_risk_level=ExampleRiskLevel.LOW,
        ),
        make_feedback(
            feedback_type="false_negative",
            expected_risk_level=ExampleRiskLevel.HIGH,
        ),
    ]

    result = (
        RiskFeedbackStatisticsService
        .calculate_statistics(feedbacks)
    )

    assert result.total_feedbacks == 3
    assert result.type_counts.accurate == 1
    assert result.type_counts.false_positive == 1
    assert result.type_counts.false_negative == 1

    assert result.expected_risk_level_counts.low == 1
    assert result.expected_risk_level_counts.high == 1

    assert result.accurate_rate == 0.3333
    assert result.correction_rate == 0.6667