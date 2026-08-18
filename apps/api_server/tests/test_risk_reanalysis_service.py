from __future__ import annotations

from dataclasses import dataclass

from app.schemas.risk import RiskLevel
from app.services.risk_reanalysis_service import (
    compare_risk_analyses,
)


@dataclass(
    frozen=True,
    slots=True,
)
class FakeEvidence:
    """测试使用的风险证据。"""

    rule_id: str


@dataclass(
    frozen=True,
    slots=True,
)
class FakeAnalysis:
    """测试使用的风险分析结果。"""

    risk_level: RiskLevel
    score: int
    evidence: tuple[FakeEvidence, ...]


def make_analysis(
    *,
    risk_level: RiskLevel,
    score: int,
    rule_ids: tuple[str, ...],
) -> FakeAnalysis:
    """创建测试风险分析结果。"""

    return FakeAnalysis(
        risk_level=risk_level,
        score=score,
        evidence=tuple(
            FakeEvidence(rule_id=rule_id)
            for rule_id in rule_ids
        ),
    )


def test_corrected_text_can_raise_risk() -> None:
    """人工修正文本后，风险可能升高。"""

    original = make_analysis(
        risk_level=RiskLevel.LOW,
        score=0,
        rule_ids=(),
    )

    corrected = make_analysis(
        risk_level=RiskLevel.HIGH,
        score=100,
        rule_ids=(
            "R-TEXT-007",
            "R-COMBO-001",
        ),
    )

    comparison = compare_risk_analyses(
        original,
        corrected,
    )

    assert comparison.original_risk_level == (
        RiskLevel.LOW
    )

    assert comparison.corrected_risk_level == (
        RiskLevel.HIGH
    )

    assert comparison.original_score == 0
    assert comparison.corrected_score == 100
    assert comparison.score_delta == 100
    assert comparison.risk_level_changed is True

    assert comparison.added_rule_ids == (
        "R-COMBO-001",
        "R-TEXT-007",
    )

    assert comparison.removed_rule_ids == ()
    assert comparison.retained_rule_ids == ()


def test_corrected_text_can_reduce_risk() -> None:
    """人工修正文本后，风险也可能降低。"""

    original = make_analysis(
        risk_level=RiskLevel.HIGH,
        score=100,
        rule_ids=(
            "R-TEXT-002",
            "R-TEXT-007",
            "R-COMBO-001",
        ),
    )

    corrected = make_analysis(
        risk_level=RiskLevel.MEDIUM,
        score=25,
        rule_ids=(
            "R-TEXT-002",
        ),
    )

    comparison = compare_risk_analyses(
        original,
        corrected,
    )

    assert comparison.score_delta == -75
    assert comparison.risk_level_changed is True

    assert comparison.added_rule_ids == ()

    assert comparison.removed_rule_ids == (
        "R-COMBO-001",
        "R-TEXT-007",
    )

    assert comparison.retained_rule_ids == (
        "R-TEXT-002",
    )


def test_score_can_change_without_level_change() -> None:
    """风险等级不变时，分数和命中规则仍可能变化。"""

    original = make_analysis(
        risk_level=RiskLevel.HIGH,
        score=80,
        rule_ids=(
            "R-TEXT-007",
        ),
    )

    corrected = make_analysis(
        risk_level=RiskLevel.HIGH,
        score=100,
        rule_ids=(
            "R-TEXT-007",
            "R-COMBO-001",
        ),
    )

    comparison = compare_risk_analyses(
        original,
        corrected,
    )

    assert comparison.risk_level_changed is False
    assert comparison.score_delta == 20

    assert comparison.added_rule_ids == (
        "R-COMBO-001",
    )

    assert comparison.removed_rule_ids == ()

    assert comparison.retained_rule_ids == (
        "R-TEXT-007",
    )


def test_identical_results_have_no_difference() -> None:
    """完全相同的分析结果不应产生差异。"""

    original = make_analysis(
        risk_level=RiskLevel.MEDIUM,
        score=40,
        rule_ids=(
            "R-TEXT-001",
            "R-TEXT-002",
        ),
    )

    corrected = make_analysis(
        risk_level=RiskLevel.MEDIUM,
        score=40,
        rule_ids=(
            "R-TEXT-002",
            "R-TEXT-001",
        ),
    )

    comparison = compare_risk_analyses(
        original,
        corrected,
    )

    assert comparison.score_delta == 0
    assert comparison.risk_level_changed is False
    assert comparison.added_rule_ids == ()
    assert comparison.removed_rule_ids == ()

    assert comparison.retained_rule_ids == (
        "R-TEXT-001",
        "R-TEXT-002",
    )