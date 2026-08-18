from __future__ import annotations

import uuid
from typing import Literal

from pydantic import BaseModel, Field

from app.schemas.risk import (
    RiskEvidence,
    RiskLevel,
)


class RiskReanalysisSnapshotResponse(BaseModel):
    """一次风险分析结果快照。"""

    risk_level: RiskLevel

    score: int = Field(
        ge=0,
        le=100,
    )

    summary: str

    evidence: list[RiskEvidence] = Field(
        default_factory=list,
    )

    actions: list[str] = Field(
        default_factory=list,
    )


class RiskAnalysisComparisonResponse(BaseModel):
    """原始分析与修正后分析的差异。"""

    original_risk_level: RiskLevel
    corrected_risk_level: RiskLevel

    original_score: int = Field(
        ge=0,
        le=100,
    )

    corrected_score: int = Field(
        ge=0,
        le=100,
    )

    score_delta: int

    risk_level_changed: bool

    added_rule_ids: list[str] = Field(
        default_factory=list,
    )

    removed_rule_ids: list[str] = Field(
        default_factory=list,
    )

    retained_rule_ids: list[str] = Field(
        default_factory=list,
    )


class CorrectedRiskReanalysisResponse(BaseModel):
    """使用人工修正 OCR 文本重新分析的响应。"""

    event_id: uuid.UUID

    source_type: Literal["image"] = "image"

    original_text: str

    corrected_text: str

    original_analysis: RiskReanalysisSnapshotResponse

    corrected_analysis: RiskReanalysisSnapshotResponse

    comparison: RiskAnalysisComparisonResponse