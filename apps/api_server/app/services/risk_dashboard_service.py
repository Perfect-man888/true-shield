from __future__ import annotations

import uuid
from collections import Counter
from datetime import UTC, date, datetime, time, timedelta
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.family import Family, FamilyMember
from app.models.family_alert import FamilyAlert
from app.models.risk import RiskEvent
from app.schemas.risk_dashboard import (
    FamilyAlertStatusDistribution,
    RiskDailyTrendPoint,
    RiskDashboardOverview,
    RiskDashboardResponse,
    RiskDashboardScope,
    RiskLevelDistribution,
    RiskSourceDistribution,
)


class FamilyDashboardAccessError(Exception):
    """当前用户无权查看指定家庭仪表盘。"""


class RiskDashboardService:
    """个人与家庭风险统计仪表盘服务。"""

    async def get_dashboard(
        self,
        db: AsyncSession,
        *,
        user_id: uuid.UUID,
        period_days: int,
        family_id: uuid.UUID | None = None,
    ) -> RiskDashboardResponse:
        """生成个人或家庭风险统计。"""

        now = datetime.now(UTC)
        start_date = now.date() - timedelta(days=period_days - 1)
        start_at = datetime.combine(start_date, time.min, tzinfo=UTC)

        if family_id is None:
            scope = RiskDashboardScope.PERSONAL
            family_name = None
            member_count = 1
            event_user_ids = [user_id]
            alert_filters = [FamilyAlert.subject_user_id == user_id]
        else:
            family, event_user_ids = await self._get_family_scope(
                db,
                family_id=family_id,
                user_id=user_id,
            )
            scope = RiskDashboardScope.FAMILY
            family_name = family.name
            member_count = len(event_user_ids)
            alert_filters = [FamilyAlert.family_id == family_id]

        event_filters = [RiskEvent.user_id.in_(event_user_ids)]

        overview = await self._get_overview(
            db,
            event_filters=event_filters,
            start_at=start_at,
        )
        risk_levels = await self._get_risk_level_distribution(
            db,
            event_filters=event_filters,
        )
        source_types = await self._get_source_distribution(
            db,
            event_filters=event_filters,
        )
        daily_trend = await self._get_daily_trend(
            db,
            event_filters=event_filters,
            start_at=start_at,
            start_date=start_date,
            period_days=period_days,
        )
        alert_status = await self._get_alert_status(
            db,
            alert_filters=alert_filters,
        )

        return RiskDashboardResponse(
            scope=scope,
            family_id=family_id,
            family_name=family_name,
            member_count=member_count,
            period_days=period_days,
            generated_at=now,
            overview=overview,
            risk_levels=risk_levels,
            source_types=source_types,
            daily_trend=daily_trend,
            alert_status=alert_status,
        )

    async def _get_family_scope(
        self,
        db: AsyncSession,
        *,
        family_id: uuid.UUID,
        user_id: uuid.UUID,
    ) -> tuple[Family, list[uuid.UUID]]:
        """验证家庭访问权限并返回有效成员编号。"""

        membership_result = await db.execute(
            select(FamilyMember)
            .join(Family, Family.id == FamilyMember.family_id)
            .where(
                FamilyMember.family_id == family_id,
                FamilyMember.user_id == user_id,
                FamilyMember.status == "active",
                Family.status == "active",
            )
        )

        if membership_result.scalar_one_or_none() is None:
            raise FamilyDashboardAccessError

        family_result = await db.execute(
            select(Family).where(
                Family.id == family_id,
                Family.status == "active",
            )
        )
        family = family_result.scalar_one()

        member_result = await db.execute(
            select(FamilyMember.user_id).where(
                FamilyMember.family_id == family_id,
                FamilyMember.status == "active",
            )
        )
        member_ids = list(member_result.scalars().all())

        return family, member_ids

    async def _get_overview(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
        start_at: datetime,
    ) -> RiskDashboardOverview:
        """读取总事件数、周期事件数、均分和最近时间。"""

        overview_result = await db.execute(
            select(
                func.count(RiskEvent.id),
                func.avg(RiskEvent.risk_score),
                func.max(RiskEvent.created_at),
            ).where(*event_filters)
        )
        total_events, average_score, latest_event_at = overview_result.one()

        period_result = await db.execute(
            select(func.count(RiskEvent.id)).where(
                *event_filters,
                RiskEvent.created_at >= start_at,
            )
        )
        period_events = period_result.scalar_one()

        return RiskDashboardOverview(
            total_events=int(total_events or 0),
            period_events=int(period_events or 0),
            average_score=round(float(average_score or 0.0), 1),
            latest_event_at=latest_event_at,
        )

    async def _get_risk_level_distribution(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
    ) -> RiskLevelDistribution:
        """按风险等级分组统计。"""

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

    async def _get_source_distribution(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
    ) -> RiskSourceDistribution:
        """按文本、图片、链接等来源分组统计。"""

        result = await db.execute(
            select(
                RiskEvent.source_type,
                func.count(RiskEvent.id),
            )
            .where(*event_filters)
            .group_by(RiskEvent.source_type)
        )
        counts = Counter({str(source): int(count) for source, count in result.all()})
        known_total = (
            counts["text"]
            + counts["image"]
            + counts["url"]
            + counts["voice"]
            + counts["call"]
        )
        all_total = sum(counts.values())

        return RiskSourceDistribution(
            text=counts["text"],
            image=counts["image"],
            url=counts["url"],
            voice=counts["voice"],
            call=counts["call"],
            other=max(0, all_total - known_total),
        )

    async def _get_daily_trend(
        self,
        db: AsyncSession,
        *,
        event_filters: list[Any],
        start_at: datetime,
        start_date: date,
        period_days: int,
    ) -> list[RiskDailyTrendPoint]:
        """生成连续日期趋势，缺失日期补零。"""

        result = await db.execute(
            select(
                func.date(RiskEvent.created_at),
                RiskEvent.risk_level,
                func.count(RiskEvent.id),
            )
            .where(
                *event_filters,
                RiskEvent.created_at >= start_at,
            )
            .group_by(
                func.date(RiskEvent.created_at),
                RiskEvent.risk_level,
            )
        )

        daily_counts: dict[date, Counter[str]] = {}

        for raw_day, risk_level, count in result.all():
            parsed_day = self._parse_database_date(raw_day)
            daily_counts.setdefault(parsed_day, Counter())[str(risk_level)] = int(count)

        points: list[RiskDailyTrendPoint] = []

        for day_offset in range(period_days):
            current_day = start_date + timedelta(days=day_offset)
            counts = daily_counts.get(current_day, Counter())
            low = counts["low"]
            medium = counts["medium"]
            high = counts["high"]

            points.append(
                RiskDailyTrendPoint(
                    date=current_day,
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
        """统计个人相关或指定家庭的告警状态。"""

        result = await db.execute(
            select(
                FamilyAlert.status,
                func.count(FamilyAlert.id),
            )
            .where(*alert_filters)
            .group_by(FamilyAlert.status)
        )
        counts = Counter({str(status): int(count) for status, count in result.all()})
        total = sum(counts.values())
        resolved = counts["resolved"]
        resolution_rate = round(resolved / total, 4) if total else 0.0

        return FamilyAlertStatusDistribution(
            total=total,
            pending=counts["pending"],
            acknowledged=counts["acknowledged"],
            resolved=resolved,
            cancelled=counts["cancelled"],
            resolution_rate=resolution_rate,
        )

    @staticmethod
    def _parse_database_date(raw_value: Any) -> date:
        """兼容 PostgreSQL date 和 SQLite ISO 日期字符串。"""

        if isinstance(raw_value, date):
            return raw_value

        return date.fromisoformat(str(raw_value))
