from __future__ import annotations

import uuid
from datetime import UTC, date, datetime

from app.schemas.risk_dashboard import (
    FamilyAlertStatusDistribution,
    RiskDashboardScope,
    RiskLevelDistribution,
    RiskSourceDistribution,
)
from app.schemas.risk_report import (
    RiskReportEventItem,
    RiskReportOverview,
    RiskReportSummaryResponse,
)
from app.services.risk_report_pdf import RiskReportPdfRenderer


def test_pdf_renderer_handles_long_real_world_content() -> None:
    report = RiskReportSummaryResponse(
        report_code="TS-TEST-PDF",
        report_title="真信盾个人风险安全报告",
        scope=RiskDashboardScope.PERSONAL,
        subject_name="测试用户",
        period_days=90,
        period_start=date(2026, 5, 4),
        period_end=date(2026, 8, 1),
        generated_at=datetime.now(UTC),
        overview=RiskReportOverview(
            total_events=18,
            average_score=54.7,
            highest_score=100,
            high_risk_events=10,
            medium_risk_events=3,
            low_risk_events=5,
            alert_count=8,
            resolved_alert_count=5,
            resolution_rate=0.625,
        ),
        risk_levels=RiskLevelDistribution(
            high=10,
            medium=3,
            low=5,
        ),
        source_types=RiskSourceDistribution(
            text=5,
            image=3,
            url=2,
            voice=4,
            call=4,
            other=0,
        ),
        daily_trend=[],
        alert_status=FamilyAlertStatusDistribution(
            pending=1,
            acknowledged=2,
            resolved=5,
            cancelled=0,
        ),
        top_signals=[],
        recent_events=[
            RiskReportEventItem(
                event_id=uuid.uuid4(),
                source_type="text",
                risk_level="high",
                risk_score=100,
                source_excerpt=(
                    "手机号 139****9000，要求立即转账。" * 40
                ),
                summary="非常长的风险摘要" * 100,
                evidence_titles=["风险证据" * 80],
                created_at=datetime.now(UTC),
            )
        ],
        recent_alerts=[],
        privacy_notice="仅导出脱敏信息。",
        disclaimer="本报告仅用于风险提示。",
    )

    pdf_bytes = RiskReportPdfRenderer().render(report)

    assert pdf_bytes.startswith(b"%PDF-")
    assert len(pdf_bytes) > 1000
