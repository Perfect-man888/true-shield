from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal
from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


class RiskLevel(StrEnum):
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

class RiskTermMatch(BaseModel):
    """风险词在原始文本中的一次命中位置。"""

    term: str = Field(
        min_length=1,
        description="命中的风险词",
    )

    start: int = Field(
        ge=0,
        description="风险词起始字符位置，包含该位置",
    )

    end: int = Field(
        ge=0,
        description="风险词结束字符位置，不包含该位置",
    )

class RiskEvidence(BaseModel):
    """单条风险证据。"""

    rule_id: str
    category: str
    title: str
    matched_terms: list[str]
    score: int = Field(ge=0, le=100)
    explanation: str
    tags: list[str] = Field(
        default_factory=list,
        description="风险标签",
    )

    matches: list[RiskTermMatch] = Field(
        default_factory=list,
        description="风险词在原始文本中的命中位置",
    )


class TextRiskAnalysisResponse(BaseModel):
    """文本风险分析结果。"""

    risk_level: RiskLevel

    # score 始终表示最终对用户展示和持久化的融合分数。
    score: int = Field(ge=0, le=100)

    # 下面字段用于解释“规则分、AI 分、最终分”之间的关系。
    # 旧记录或纯规则分析允许为空，保证接口向后兼容。
    rule_score: int | None = Field(
        default=None,
        ge=0,
        le=100,
    )
    ai_score: int | None = Field(
        default=None,
        ge=0,
        le=100,
    )
    ai_risk_level: RiskLevel | None = None
    fusion_applied: bool = False
    fusion_reason: str | None = Field(
        default=None,
        max_length=500,
    )

    evidence: list[RiskEvidence]
    actions: list[str]
    disclaimer: str
    rule_version: str

    analysis_mode: Literal[
        "rules_only",
        "rules_ai",
    ] = "rules_only"

    engine_version: str = "rules-1.0.0"

    ai_model: str | None = None

    ai_confidence: float | None = Field(
        default=None,
        ge=0,
        le=1,
    )

    ai_summary: str | None = Field(
        default=None,
        max_length=1_000,
    )

class PersistedTextRiskAnalysisResponse(
    TextRiskAnalysisResponse
):
    """已经保存到数据库的文本风险分析结果。"""

    event_id: uuid.UUID
    created_at: datetime

class RiskEventListItem(BaseModel):
    """风险事件列表中的单条摘要。"""

    event_id: uuid.UUID
    source_type: str
    risk_level: RiskLevel
    score: int = Field(ge=0, le=100)
    summary: str
    evidence_count: int = Field(ge=0)
    created_at: datetime


class RiskEventListResponse(BaseModel):
    """风险事件分页列表。"""

    items: list[RiskEventListItem]
    total: int = Field(ge=0)
    limit: int = Field(ge=1)
    offset: int = Field(ge=0)


class RiskEventDetailResponse(
    PersistedTextRiskAnalysisResponse
):
    """风险事件完整详情。"""

    source_type: str
    source_text: str
    source_metadata: dict[str, Any] | None = None
    summary: str

class OCRTextLineResponse(BaseModel):
    """单行 OCR 识别结果。"""

    text: str = Field(
        min_length=1,
        description="识别出的文字",
    )

    confidence: float = Field(
        ge=0,
        le=1,
        description="OCR 识别置信度",
    )

    box: list[tuple[int, int]] = Field(
        default_factory=list,
        description="文字在原始图片中的坐标",
    )

class OCRQualityResponse(BaseModel):
    """OCR 识别质量评估响应。"""

    line_count: int = Field(
        ge=0,
        description="OCR 识别出的有效文字行数",
    )

    average_confidence: float = Field(
        ge=0,
        le=1,
        description="所有有效文字行的平均置信度",
    )

    minimum_confidence: float = Field(
        ge=0,
        le=1,
        description="有效文字行中的最低置信度",
    )

    needs_manual_review: bool = Field(
        description="是否建议用户对照原图人工核对",
    )

    review_reason: str | None = Field(
        default=None,
        description="建议人工核对的原因",
    )

class ImageRiskAnalysisResponse(
    PersistedTextRiskAnalysisResponse
):
    """图片 OCR 与风险分析联合响应。"""

    source_type: Literal["image"] = "image"

    extracted_text: str = Field(
        min_length=1,
        description="从图片中提取出的完整文字",
    )

    image_width: int = Field(
        gt=0,
        description="原始图片宽度",
    )

    image_height: int = Field(
        gt=0,
        description="原始图片高度",
    )

    ocr_lines: list[OCRTextLineResponse] = Field(
        default_factory=list,
        description="OCR 逐行识别结果",
    )

    ocr_quality: OCRQualityResponse = Field(
        description="OCR 识别质量评估结果",
    )