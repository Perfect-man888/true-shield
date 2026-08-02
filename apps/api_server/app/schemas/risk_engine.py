from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class RiskEngineStatusResponse(BaseModel):
    """当前风险分析引擎状态。"""

    analysis_mode: Literal[
        "rules_only",
        "rules_ai",
    ]
    engine_version: str
    rule_version: str
    total_rules: int = Field(ge=0)
    enabled_rules: int = Field(ge=0)
    ai_enabled: bool
    ai_provider: str
    ai_model: str
    ai_available: bool
    privacy_mode: str
