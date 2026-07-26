from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.family_alert import (
    FamilyAlert,
    FamilyAlertRecipient,
)
from app.models.risk import RiskEvent
from app.models.trusted_contact import TrustedContact


async def get_risk_event_for_family_alert(
    db: AsyncSession,
    *,
    event_id: uuid.UUID,
) -> RiskEvent | None:
    """根据事件 ID 查询风险事件。"""

    statement = select(RiskEvent).where(
        RiskEvent.id == event_id,
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def get_existing_family_alert(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    risk_event_id: uuid.UUID,
) -> FamilyAlert | None:
    """查询风险事件是否已经生成过家庭告警。"""

    statement = (
        select(FamilyAlert)
        .where(
            FamilyAlert.family_id == family_id,
            FamilyAlert.risk_event_id
            == risk_event_id,
        )
        .options(
            selectinload(FamilyAlert.recipients),
        )
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def list_eligible_trusted_contacts(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    subject_user_id: uuid.UUID,
) -> list[TrustedContact]:
    """
    查询可以接收告警的有效可信联系人。

    联系人必须：
    1. 属于指定家庭和被保护用户；
    2. 状态为 active；
    3. can_receive_alerts 为 true。
    """

    statement = (
        select(TrustedContact)
        .where(
            TrustedContact.family_id == family_id,
            TrustedContact.subject_user_id
            == subject_user_id,
            TrustedContact.status == "active",
            TrustedContact.can_receive_alerts.is_(
                True
            ),
        )
        .order_by(
            TrustedContact.priority.asc(),
            TrustedContact.created_at.asc(),
        )
    )

    result = await db.execute(statement)

    return list(result.scalars().all())


async def create_family_alert(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    risk_event_id: uuid.UUID,
    subject_user_id: uuid.UUID,
    risk_level: str,
    title: str,
    summary: str,
    recipients: list[dict[str, object]],
) -> FamilyAlert:
    """创建家庭告警及接收人记录。"""

    alert = FamilyAlert(
        family_id=family_id,
        risk_event_id=risk_event_id,
        subject_user_id=subject_user_id,
        risk_level=risk_level,
        title=title,
        summary=summary,
        status="pending",
    )

    alert.recipients = [
        FamilyAlertRecipient(
            trusted_contact_id=recipient[
                "trusted_contact_id"
            ],
            linked_user_id=recipient[
                "linked_user_id"
            ],
            recipient_name=recipient[
                "recipient_name"
            ],
            channel=recipient["channel"],
            destination=recipient["destination"],
            delivery_status="pending",
        )
        for recipient in recipients
    ]

    db.add(alert)

    await db.commit()

    statement = (
        select(FamilyAlert)
        .where(
            FamilyAlert.id == alert.id,
        )
        .options(
            selectinload(FamilyAlert.recipients),
        )
    )

    result = await db.execute(statement)

    created_alert = result.scalar_one()

    return created_alert


async def list_family_alerts(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
) -> list[FamilyAlert]:
    """查询家庭告警列表。"""

    statement = (
        select(FamilyAlert)
        .where(
            FamilyAlert.family_id == family_id,
        )
        .options(
            selectinload(FamilyAlert.recipients),
        )
        .order_by(
            FamilyAlert.created_at.desc(),
        )
    )

    result = await db.execute(statement)

    return list(
        result.scalars().unique().all()
    )


async def get_family_alert_by_id(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    alert_id: uuid.UUID,
) -> FamilyAlert | None:
    """查询家庭内的一条告警。"""

    statement = (
        select(FamilyAlert)
        .where(
            FamilyAlert.id == alert_id,
            FamilyAlert.family_id == family_id,
        )
        .options(
            selectinload(FamilyAlert.recipients),
        )
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()