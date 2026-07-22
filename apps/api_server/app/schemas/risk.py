from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal
from enum import Enum

from pydantic import BaseModel, Field, field_validator


class RiskLevel(str, Enum):
    """风险等级。"""

    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class TextRiskContext(BaseModel):
    """文本分析时可选的上下文信息。"""

    sender_is_known: bool | None = None
    claimed_identity: str | None = Field(
        default=None,
        max_length=100,
    )
    amount: Decimal | None = Field(
        default=None,
        ge=0,
    )


class TextRiskAnalysisRequest(BaseModel):
    """可疑文本分析请求。"""

    text: str = Field(
        min_length=1,
        max_length=10_000,
    )
    context: TextRiskContext = Field(
        default_factory=TextRiskContext,
    )

    @field_validator("text")
    @classmethod
    def validate_text(cls, value: str) -> str:
        cleaned = value.strip()

        if not cleaned:
            raise ValueError("text cannot be blank")

        return cleaned


class RiskEvidence(BaseModel):
    """单条风险证据。"""

    rule_id: str
    category: str
    title: str
    matched_terms: list[str]
    score: int = Field(ge=0, le=100)
    explanation: str


class TextRiskAnalysisResponse(BaseModel):
    """文本风险分析结果。"""

    risk_level: RiskLevel
    score: int = Field(ge=0, le=100)
    evidence: list[RiskEvidence]
    actions: list[str]
    disclaimer: str
    rule_version: str

class PersistedTextRiskAnalysisResponse(
    TextRiskAnalysisResponse
):
    """已经保存到数据库的文本风险分析结果。"""

    event_id: uuid.UUID
    created_at: datetime