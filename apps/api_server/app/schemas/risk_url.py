from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Literal

from pydantic import (
    BaseModel,
    Field,
    field_validator,
)

from app.schemas.risk import RiskLevel


class URLRiskAnalysisRequest(BaseModel):
    """链接风险分析请求。"""

    url: str = Field(
        min_length=1,
        max_length=2048,
        description="需要分析的网站链接",
    )

    @field_validator("url")
    @classmethod
    def normalize_url(
        cls,
        value: str,
    ) -> str:
        """去除链接首尾空白。"""

        normalized = value.strip()

        if not normalized:
            raise ValueError("URL 不能为空。")

        return normalized


class URLRiskSignal(BaseModel):
    """单项 URL 风险信号。"""

    signal_id: str

    category: str

    title: str

    score: int = Field(
        ge=0,
        le=100,
    )

    explanation: str

    details: dict[str, Any] = Field(
        default_factory=dict,
    )


class URLRiskAnalysisResponse(BaseModel):
    """链接风险分析结果。"""

    original_url: str

    normalized_url: str

    host: str

    risk_level: RiskLevel

    score: int = Field(
        ge=0,
        le=100,
    )

    summary: str

    signals: list[URLRiskSignal] = Field(
        default_factory=list,
    )

    actions: list[str] = Field(
        default_factory=list,
    )

    disclaimer: str

    rule_version: str


class PersistedURLRiskAnalysisResponse(
    URLRiskAnalysisResponse
):
    """已保存到数据库的 URL 风险分析结果。"""

    event_id: uuid.UUID

    created_at: datetime

    source_type: Literal["url"] = "url"