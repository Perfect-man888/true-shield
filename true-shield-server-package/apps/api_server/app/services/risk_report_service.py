from __future__ import annotations

import re
import uuid
from collections import Counter
from datetime import UTC, date, datetime, time, timedelta
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.family import Family, FamilyMember
from app.models.family_alert import FamilyAlert
from app.models.risk import RiskEvent, RiskSignal
from app.models.user import User
from app.schemas.risk_dashboard import (
    FamilyAlertStatusDistribution,
    RiskDailyTrendPoint,
    RiskDashboardScope,
    RiskLevelDistribution,
    RiskSourceDistribution,
)
from app.schemas.risk_report import (
    RiskReportAlertItem,
    RiskReportEventItem,
    RiskReportOverview,
    RiskReportSummaryResponse,
    RiskReportTopSignal,
)


class FamilyRiskReportAccessError(Exception):
    """当前用户无权查看指定家庭风险报告。"""


PHONE_PATTERN = re.compile(r"(?<!\d)(1\d{2})\d{4}(\d{4})(?!\d)")
ID_CARD_PATTERN = re.compile(r"(?<!\d)(\d{6})\d{8}(\d{3}[0-9Xx])(?!\d)")
BANK_CARD_PATTERN = re.compile(r"(?<!\d)(\d{4})\d{8,11}(\d{4})(?!\d)")
EMAIL_PATTERN = re.compile(
    r"([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\.[A-Za-z]{2,})"
)


def mask_sensitive_text(text: str | None) -> str:
    """对报告中的常见敏感标识进行最低限度脱敏。"""

    value = (text or "").strip()
    if not value:
        return ""

    value = PHONE_PATTERN.sub(r"\1****\2", value)
    value = ID_CARD_PATTERN.sub(r"\1********\2", value)
    value = BANK_CARD_PATTERN.sub(r"\1********\2", value)
    value = EMAIL_PATTERN.sub(r"\1***\3", value)
    return value


def build_masked_excerpt(text: str | None, *, max_length: int = 180) -> str:
    """生成可写入报告的短脱敏片段，避免暴露完整原始检测内容。"""

    compact = re.sub(r"\s+", " ", (text or "")).strip()
    if not compact:
        return ""

    masked = mask_sensitive_text(compact)
    if len(masked) <= max_length:
        return masked
    return f"{masked[:max_length].rstrip()}…"


class RiskReportService:
    """生成个人或家庭风险报告预览数据。"""

    PRIVACY_NOTICE = (
        "报告仅包含脱敏后的风险摘要、统计结果和必要处置记录，"
        "不包含原始录音、原始图片、完整号码、完整账号或系统密钥。"
    )

    DISCLAIMER = (
        "本报告用于风险提示、家庭沟通和后续核验，不构成司法、"
        "执法、金融或身份权威鉴定。重要事项请通过官方渠道再次确认。"
    )

    async def build_report(
        self,
        db: AsyncSession,
        *,
        current_user: User,
        period_days: int,
        family_id: uuid.UUID | None = None,
    ) -> RiskReportSummaryResponse:
        """按周期生成报告预览数据。"""

        safe_period_days = max(1, min(period_days, 365))
        now = datetime.now(UTC)
        period_end = now.date()
        period_start = period_end - timedelta(days=safe_period_days - 1)
        start_at = datetime.combine(period_start, time.min, tzinfo=UTC)

        if family_id is None:
            scope = RiskDashboardScope.PERSONAL
            family_name = None
            member_count = 1
            event_user_ids = [current_user.id]
            subject_name = current_user.display_name
            alert_filters = [FamilyAlert.subject_user_id == current_user.id]
            report_title = "真信盾个人风险安全报告"
        else:
            family, event_user_ids = await self._get_family_scope(
                db,
                family_id=family_id,
                user_id=current_user.id,
            )
            scope = RiskDashboardScope.FAMILY
            family_name = family.name
            member_count = len(event_user_ids)
            subject_name = family.name
            alert_filters = [FamilyAlert.family_id == family_id]
            report_title = "真信盾家庭风险安全报告"

        event_filters = [
            RiskEvent.user_id.in_(event_user_ids),
            RiskEvent.created_at >= start_at,
        ]
        period_alert_filters = [
            *alert_filters,
            FamilyAlert.created_at >= start_at,
        ]

        risk_levels = await self._get_risk_levels(
            db,
            event_filters=event_filters,
        )
        source_types = await self._get_source_types(
            db,
            event_filters=event_filters,
        )
        alert_status = await self._get_alert_status(
            db,
            alert_filters=period_alert_filters,
        )
        overview = await self._get_overview(
            db,
            event_filters=event_filters,
            risk_levels=risk_levels,
            alert_status=alert_status,
        )
        daily_trend = await self._get_daily_trend(
            db,
            event_filters=event_filters,
            period_start=period_start,
            period_days=safe_period_days,
        )
        top_signals = await self._get_top_signals(
            db,
            event_filters=event_filters,
        )
        recent_events = await self._get_recent_events(
            db,
            event_filters=event_filters,
        )
        recent_alerts = await self._get_recent_alerts(
            db,
            alert_filters=period_alert_filters,
        )

        report_code = (
            f"TS-{now:%Y%m%d}-{uuid.uuid4().hex[:8].upper()}"
        )

        return RiskReportSummaryResponse(
            report_code=report_code,
            report_title=report_title,
            scope=scope,
            subject_name=mask_sensitive_text(subject_name),
            family_id=family_id,
            family_name=mask_sensitive_text(family_name),
            member_count=member_count,
            period_days=safe_period_days,
            period_start=period_start,
            period_end=period_end,
            generated_at=now,
            overview=overview,
            risk_levels=risk_levels,
            source_types=source_types,
            daily_trend=daily_trend,
            alert_status=alert_status,
            top_signals=top_signals,
            recent_events=recent_events,
            recent_alerts=recent_alerts,
            privacy_notice=self.PRIVACY_NOTICE,
            disclaimer=self.DISCLAIMER,
        )

    async def _get_family_scope(
        self,
        db: AsyncSession,
        *,
        family_id: uuid.UUID,
        user_id: uuid.UUID,
    ) -> tuple[Family, list[uuid.UUID]]:
        membership = await db.execute(
            select(FamilyMember.id).join(
                Family,
                Family.id == FamilyMember.family_id,
            ).where(
                FamilyMember.family_id == family_id,
                FamilyMember.user_id == user_id,
                FamilyMember.status == "active",
                Family.status == "active",
            )
        )

        if membership.scalar_one_or_none() is None:
            raise FamilyRiskReportAccessError

        family_result = await db.execute(
            select(Family).where(
                Family.id == family_id,
                Family.status == "active",
            )
        )
        family = family_result.scalar_one()

        members_result = await db.execute(
            select(FamilyMember.user_id).where(
                FamilyMember.family_id == family_id,
                FamilyMember.status == "active",
            )
        )
        member_ids = list(members_result.scalars().all())
        return family, member_ids

    async def _get_overview(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
        risk_levels: RiskLevelDistribution,
        alert_status: FamilyAlertStatusDistribution,
    ) -> RiskReportOverview:
        result = await db.execute(
            select(
                func.count(RiskEvent.id),
                func.avg(RiskEvent.risk_score),
                func.max(RiskEvent.risk_score),
            ).where(*event_filters)
        )
        total_events, average_score, highest_score = result.one()

        return RiskReportOverview(
            total_events=int(total_events or 0),
            average_score=round(float(average_score or 0.0), 1),
            highest_score=int(highest_score or 0),
            high_risk_events=risk_levels.high,
            medium_risk_events=risk_levels.medium,
            low_risk_events=risk_levels.low,
            alert_count=alert_status.total,
            resolved_alert_count=alert_status.resolved,
            resolution_rate=alert_status.resolution_rate,
        )

    async def _get_risk_levels(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
    ) -> RiskLevelDistribution:
        result = await db.execute(
            select(
                RiskEvent.risk_level,
                func.count(RiskEvent.id),
            )
            .where(*event_filters)
            .group_by(RiskEvent.risk_level)
        )
        counts = Counter({str(level): int(count) for level, count in result.all()})
        return RiskLevelDistribution(
            low=counts["low"],
            medium=counts["medium"],
            high=counts["high"],
        )

    async def _get_source_types(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
    ) -> RiskSourceDistribution:
        result = await db.execute(
            select(
                RiskEvent.source_type,
                func.count(RiskEvent.id),
            )
            .where(*event_filters)
            .group_by(RiskEvent.source_type)
        )
        counts = Counter({str(source): int(count) for source, count in result.all()})
        known_total = sum(
            counts[source]
            for source in ("text", "image", "url", "voice", "call")
        )
        return RiskSourceDistribution(
            text=counts["text"],
            image=counts["image"],
            url=counts["url"],
            voice=counts["voice"],
            call=counts["call"],
            other=max(0, sum(counts.values()) - known_total),
        )

    async def _get_daily_trend(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
        period_start: date,
        period_days: int,
    ) -> list[RiskDailyTrendPoint]:
        result = await db.execute(
            select(
                func.date(RiskEvent.created_at),
                RiskEvent.risk_level,
                func.count(RiskEvent.id),
            )
            .where(*event_filters)
            .group_by(
                func.date(RiskEvent.created_at),
                RiskEvent.risk_level,
            )
        )

        daily_counts: dict[date, Counter[str]] = {}
        for raw_day, level, count in result.all():
            day = raw_day if isinstance(raw_day, date) else date.fromisoformat(str(raw_day))
            daily_counts.setdefault(day, Counter())[str(level)] = int(count)

        points: list[RiskDailyTrendPoint] = []
        for offset in range(period_days):
            day = period_start + timedelta(days=offset)
            counts = daily_counts.get(day, Counter())
            low = counts["low"]
            medium = counts["medium"]
            high = counts["high"]
            points.append(
                RiskDailyTrendPoint(
                    date=day,
                    total=low + medium + high,
                    low=low,
                    medium=medium,
                    high=high,
                )
            )
        return points

    async def _get_alert_status(
        self,
        db: AsyncSession,
        *,
        alert_filters: list[Any],
    ) -> FamilyAlertStatusDistribution:
        result = await db.execute(
            select(
                FamilyAlert.status,
                func.count(FamilyAlert.id),
            )
            .where(*alert_filters)
            .group_by(FamilyAlert.status)
        )
        counts = Counter({str(value): int(count) for value, count in result.all()})
        total = sum(counts.values())
        resolved = counts["resolved"]
        return FamilyAlertStatusDistribution(
            total=total,
            pending=counts["pending"],
            acknowledged=counts["acknowledged"],
            resolved=resolved,
            cancelled=counts["cancelled"],
            resolution_rate=round(resolved / total, 4) if total else 0.0,
        )

    async def _get_top_signals(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
    ) -> list[RiskReportTopSignal]:
        result = await db.execute(
            select(
                RiskSignal.signal_type,
                RiskSignal.title,
                func.count(RiskSignal.id).label("signal_count"),
            )
            .join(RiskEvent, RiskEvent.id == RiskSignal.event_id)
            .where(*event_filters)
            .group_by(
                RiskSignal.signal_type,
                RiskSignal.title,
            )
            .order_by(func.count(RiskSignal.id).desc(), RiskSignal.title)
            .limit(8)
        )
        return [
            RiskReportTopSignal(
                signal_type=str(signal_type),
                title=mask_sensitive_text(title),
                count=int(count),
            )
            for signal_type, title, count in result.all()
        ]

    async def _get_recent_events(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
    ) -> list[RiskReportEventItem]:
        result = await db.execute(
            select(RiskEvent)
            .options(selectinload(RiskEvent.signals))
            .where(*event_filters)
            .order_by(
                RiskEvent.risk_score.desc(),
                RiskEvent.created_at.desc(),
            )
            .limit(10)
        )
        events = list(result.scalars().unique().all())
        return [
            RiskReportEventItem(
                event_id=event.id,
                source_type=event.source_type,
                risk_level=event.risk_level,
                risk_score=event.risk_score,
                summary=mask_sensitive_text(event.summary),
                source_excerpt=build_masked_excerpt(event.source_text),
                evidence_titles=[
                    mask_sensitive_text(signal.title)
                    for signal in event.signals[:4]
                ],
                created_at=event.created_at,
            )
            for event in events
        ]

    async def _get_recent_alerts(
        self,
        db: AsyncSession,
        *,
        alert_filters: list[Any],
    ) -> list[RiskReportAlertItem]:
        result = await db.execute(
            select(FamilyAlert)
            .where(*alert_filters)
            .order_by(FamilyAlert.created_at.desc())
            .limit(8)
        )
        alerts = list(result.scalars().all())
        return [
            RiskReportAlertItem(
                alert_id=alert.id,
                risk_event_id=alert.risk_event_id,
                risk_level=alert.risk_level,
                title=mask_sensitive_text(alert.title),
                summary=mask_sensitive_text(alert.summary),
                status=alert.status,
                created_at=alert.created_at,
                resolved_at=alert.resolved_at,
                resolution_note=mask_sensitive_text(alert.resolution_note) or None,
            )
            for alert in alerts
        ]
