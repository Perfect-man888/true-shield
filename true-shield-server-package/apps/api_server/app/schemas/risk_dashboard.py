from __future__ import annotations

import uuid
from datetime import date, datetime
from enum import StrEnum

from pydantic import BaseModel, Field


class RiskDashboardScope(StrEnum):
    """风险仪表盘统计范围。"""

    PERSONAL = "personal"
    FAMILY = "family"


class RiskLevelDistribution(BaseModel):
    """不同风险等级的事件数量。"""

    low: int = Field(default=0, ge=0)
    medium: int = Field(default=0, ge=0)
    high: int = Field(default=0, ge=0)


class RiskSourceDistribution(BaseModel):
    """不同检测来源的事件数量。"""

    text: int = Field(default=0, ge=0)
    image: int = Field(default=0, ge=0)
    url: int = Field(default=0, ge=0)
    voice: int = Field(default=0, ge=0)
    call: int = Field(default=0, ge=0)
    other: int = Field(default=0, ge=0)


class RiskDashboardOverview(BaseModel):
    """风险仪表盘总体概览。"""

    total_events: int = Field(default=0, ge=0)
    period_events: int = Field(default=0, ge=0)
    average_score: float = Field(default=0.0, ge=0.0, le=100.0)
    latest_event_at: datetime | None = None


class RiskDailyTrendPoint(BaseModel):
    """某一天的风险事件趋势点。"""

    date: date
    total: int = Field(default=0, ge=0)
    low: int = Field(default=0, ge=0)
    medium: int = Field(default=0, ge=0)
    high: int = Field(default=0, ge=0)


class FamilyAlertStatusDistribution(BaseModel):
    """家庭告警状态数量。"""

    total: int = Field(default=0, ge=0)
    pending: int = Field(default=0, ge=0)
    acknowledged: int = Field(default=0, ge=0)
    resolved: int = Field(default=0, ge=0)
    cancelled: int = Field(default=0, ge=0)
    resolution_rate: float = Field(default=0.0, ge=0.0, le=1.0)


class RiskDashboardResponse(BaseModel):
    """个人或家庭风险统计仪表盘响应。"""

    scope: RiskDashboardScope
    family_id: uuid.UUID | None = None
    family_name: str | None = None
    member_count: int = Field(default=1, ge=1)
    period_days: int = Field(ge=1, le=90)
    generated_at: datetime
    overview: RiskDashboardOverview
    risk_levels: RiskLevelDistribution
    source_types: RiskSourceDistribution
    daily_trend: list[RiskDailyTrendPoint] = Field(default_factory=list)
    alert_status: FamilyAlertStatusDistribution
