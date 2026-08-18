from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator

from app.schemas.risk import PersistedTextRiskAnalysisResponse


class VoiceRiskAnalysisRequest(BaseModel):
    """人工确认或修正后的语音转写文本风险分析请求。"""

    transcript: str = Field(
        min_length=1,
        max_length=10_000,
        description="经用户确认或修正的语音转写文本。",
    )
    language: str = Field(default="zh-CN", min_length=2, max_length=20)
    recognition_provider: str = Field(
        default="manual_transcript",
        min_length=1,
        max_length=64,
    )
    recognition_confidence: float | None = Field(default=None, ge=0.0, le=1.0)
    duration_seconds: float | None = Field(default=None, ge=0.0, le=3_600.0)
    transcription_model: str | None = Field(default=None, max_length=128)

    @field_validator("transcript")
    @classmethod
    def validate_transcript(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("transcript cannot be blank")
        return cleaned

    @field_validator("language", "recognition_provider")
    @classmethod
    def strip_short_text(cls, value: str) -> str:
        return value.strip()


class VoiceRiskAnalysisResponse(PersistedTextRiskAnalysisResponse):
    """语音转写与风险分析联合响应。"""

    source_type: Literal["voice"] = "voice"
    transcript: str
    language: str
    recognition_provider: str
    recognition_confidence: float | None = None
    language_probability: float | None = None
    duration_seconds: float | None = None
    transcription_model: str | None = None
    audio_size_bytes: int | None = None
