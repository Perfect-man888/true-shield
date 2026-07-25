from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

from app.schemas.risk import RiskLevel


class RiskEvidenceLike(Protocol):
    """可参与风险对比的证据对象。"""

    rule_id: str


class RiskAnalysisLike(Protocol):
    """可参与风险对比的分析结果对象。"""

    risk_level: RiskLevel
    score: int
    evidence: Sequence[RiskEvidenceLike]


@dataclass(
    frozen=True,
    slots=True,
)
class RiskAnalysisComparisonResult:
    """修正前后风险分析结果的差异。"""

    original_risk_level: RiskLevel
    corrected_risk_level: RiskLevel

    original_score: int
    corrected_score: int
    score_delta: int

    risk_level_changed: bool

    added_rule_ids: tuple[str, ...]
    removed_rule_ids: tuple[str, ...]
    retained_rule_ids: tuple[str, ...]


def compare_risk_analyses(
    original: RiskAnalysisLike,
    corrected: RiskAnalysisLike,
) -> RiskAnalysisComparisonResult:
    """
    比较原始 OCR 文本与人工修正文本的风险分析结果。

    score_delta 的计算方式：

        修正后分数 - 原始分数

    大于 0 表示修正后风险升高；
    小于 0 表示修正后风险降低。
    """

    original_rule_ids = {
        evidence.rule_id
        for evidence in original.evidence
    }

    corrected_rule_ids = {
        evidence.rule_id
        for evidence in corrected.evidence
    }

    added_rule_ids = tuple(
        sorted(
            corrected_rule_ids
            - original_rule_ids
        )
    )

    removed_rule_ids = tuple(
        sorted(
            original_rule_ids
            - corrected_rule_ids
        )
    )

    retained_rule_ids = tuple(
        sorted(
            original_rule_ids
            & corrected_rule_ids
        )
    )

    return RiskAnalysisComparisonResult(
        original_risk_level=original.risk_level,
        corrected_risk_level=corrected.risk_level,
        original_score=original.score,
        corrected_score=corrected.score,
        score_delta=(
            corrected.score
            - original.score
        ),
        risk_level_changed=(
            original.risk_level
            != corrected.risk_level
        ),
        added_rule_ids=added_rule_ids,
        removed_rule_ids=removed_rule_ids,
        retained_rule_ids=retained_rule_ids,
    )