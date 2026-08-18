import pytest
from pydantic import ValidationError

from app.schemas.risk import RiskLevel
from app.schemas.risk_feedback import (
    RiskFeedbackCreate,
    RiskFeedbackType,
)


def test_accurate_feedback_does_not_require_expected_level() -> None:
    feedback = RiskFeedbackCreate(
        feedback_type=RiskFeedbackType.ACCURATE,
    )

    assert feedback.feedback_type == RiskFeedbackType.ACCURATE
    assert feedback.expected_risk_level is None


def test_false_positive_defaults_expected_level_to_low() -> None:
    feedback = RiskFeedbackCreate(
        feedback_type=RiskFeedbackType.FALSE_POSITIVE,
    )

    assert feedback.expected_risk_level == RiskLevel.LOW


def test_false_negative_requires_expected_level() -> None:
    with pytest.raises(
        ValidationError,
        match="必须提供 expected_risk_level",
    ):
        RiskFeedbackCreate(
            feedback_type=RiskFeedbackType.FALSE_NEGATIVE,
        )


def test_false_negative_expected_level_cannot_be_low() -> None:
    with pytest.raises(
        ValidationError,
        match="不能为 low",
    ):
        RiskFeedbackCreate(
            feedback_type=RiskFeedbackType.FALSE_NEGATIVE,
            expected_risk_level=RiskLevel.LOW,
        )


def test_feedback_comment_is_trimmed() -> None:
    feedback = RiskFeedbackCreate(
        feedback_type=RiskFeedbackType.RISK_TOO_HIGH,
        expected_risk_level=RiskLevel.MEDIUM,
        comment="  系统判断得太严重了。  ",
    )

    assert feedback.comment == "系统判断得太严重了。"