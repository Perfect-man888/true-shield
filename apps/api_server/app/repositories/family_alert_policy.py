from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.family_alert_policy import (
    FamilyAlertPolicy,
)


async def get_family_alert_policy(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
) -> FamilyAlertPolicy | None:
    """查询家庭告警策略。"""

    statement = select(
        FamilyAlertPolicy
    ).where(
        FamilyAlertPolicy.family_id
        == family_id,
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def get_or_create_family_alert_policy(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
) -> FamilyAlertPolicy:
    """查询策略，不存在时创建默认策略。"""

    policy = await get_family_alert_policy(
        db,
        family_id=family_id,
    )

    if policy is not None:
        return policy

    policy = FamilyAlertPolicy(
        family_id=family_id,
        auto_create_enabled=True,
        auto_dispatch_enabled=False,
        minimum_risk_level="high",
        enabled_source_types=[
            "text",
            "image",
            "url",
        ],
        max_recipients=3,
    )

    db.add(policy)

    await db.commit()
    await db.refresh(policy)

    return policy


async def update_family_alert_policy(
    db: AsyncSession,
    *,
    policy: FamilyAlertPolicy,
    update_data: dict[str, object],
) -> FamilyAlertPolicy:
    """更新家庭告警策略。"""

    for field_name, value in update_data.items():
        setattr(
            policy,
            field_name,
            value,
        )

    await db.commit()
    await db.refresh(policy)

    return policy