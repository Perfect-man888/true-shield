from __future__ import annotations

import uuid
from datetime import date, datetime

from pydantic import BaseModel, Field

from app.schemas.risk_dashboard import (
    FamilyAlertStatusDistribution,
    RiskDailyTrendPoint,
    RiskDashboardScope,
    RiskLevelDistribution,
    RiskSourceDistribution,
)


class RiskReportOverview(BaseModel):
    """选定报告周期内的风险概览。"""

    total_events: int = Field(default=0, ge=0)
    average_score: float = Field(default=0.0, ge=0.0, le=100.0)
    highest_score: int = Field(default=0, ge=0, le=100)
    high_risk_events: int = Field(default=0, ge=0)
    medium_risk_events: int = Field(default=0, ge=0)
    low_risk_events: int = Field(default=0, ge=0)
    alert_count: int = Field(default=0, ge=0)
    resolved_alert_count: int = Field(default=0, ge=0)
    resolution_rate: float = Field(default=0.0, ge=0.0, le=1.0)


class RiskReportTopSignal(BaseModel):
    """报告周期内命中次数较多的风险证据。"""

    signal_type: str
    title: str
    count: int = Field(ge=1)


class RiskReportEventItem(BaseModel):
    """报告中展示的脱敏风险事件摘要。"""

    event_id: uuid.UUID
    source_type: str
    risk_level: str
    risk_score: int = Field(ge=0, le=100)
    summary: str
    source_excerpt: str = ""
    evidence_titles: list[str] = Field(default_factory=list)
    created_at: datetime


class RiskReportAlertItem(BaseModel):
    """报告中展示的家庭告警摘要。"""

    alert_id: uuid.UUID
    risk_event_id: uuid.UUID
    risk_level: str
    title: str
    summary: str
    status: str
    created_at: datetime
    resolved_at: datetime | None = None
    resolution_note: str | None = None


class RiskReportSummaryResponse(BaseModel):
    """个人或家庭风险报告预览数据。"""

    report_code: str
    report_title: str
    scope: RiskDashboardScope
    subject_name: str
    family_id: uuid.UUID | None = None
    family_name: str | None = None
    member_count: int = Field(default=1, ge=1)
    period_days: int = Field(ge=1, le=365)
    period_start: date
    period_end: date
    generated_at: datetime
    overview: RiskReportOverview
    risk_levels: RiskLevelDistribution
    source_types: RiskSourceDistribution
    daily_trend: list[RiskDailyTrendPoint] = Field(default_factory=list)
    alert_status: FamilyAlertStatusDistribution
    top_signals: list[RiskReportTopSignal] = Field(default_factory=list)
    recent_events: list[RiskReportEventItem] = Field(default_factory=list)
    recent_alerts: list[RiskReportAlertItem] = Field(default_factory=list)
    privacy_notice: str
    disclaimer: str
