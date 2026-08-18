from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.family import (
    Family,
    FamilyMember,
)
from app.models.user import User


async def create_family_with_owner(
    db: AsyncSession,
    *,
    name: str,
    owner_user_id: uuid.UUID,
) -> tuple[Family, FamilyMember]:
    """
    创建家庭组，并自动将创建者加入为 owner。

    家庭和所有者成员记录在同一个事务中保存。
    """

    family = Family(
        name=name,
        owner_user_id=owner_user_id,
        status="active",
    )

    db.add(family)

    # 先获得 family.id，但暂时不提交事务。
    await db.flush()

    owner_member = FamilyMember(
        family_id=family.id,
        user_id=owner_user_id,
        role="owner",
        status="active",
    )

    db.add(owner_member)

    await db.commit()

    await db.refresh(family)
    await db.refresh(owner_member)

    return family, owner_member


async def list_user_families(
    db: AsyncSession,
    *,
    user_id: uuid.UUID,
) -> list[tuple[Family, FamilyMember]]:
    """查询用户当前加入的全部有效家庭。"""

    statement = (
        select(
            Family,
            FamilyMember,
        )
        .join(
            FamilyMember,
            FamilyMember.family_id == Family.id,
        )
        .where(
            FamilyMember.user_id == user_id,
            FamilyMember.status == "active",
            Family.status == "active",
        )
        .order_by(
            Family.created_at.desc(),
        )
    )

    result = await db.execute(statement)

    return list(result.all())


async def get_active_family_membership(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    user_id: uuid.UUID,
) -> FamilyMember | None:
    """
    查询用户在指定家庭中的有效成员身份。

    同时检查家庭组自身仍为 active 状态。
    """

    statement = (
        select(FamilyMember)
        .join(
            Family,
            Family.id == FamilyMember.family_id,
        )
        .where(
            FamilyMember.family_id == family_id,
            FamilyMember.user_id == user_id,
            FamilyMember.status == "active",
            Family.status == "active",
        )
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def list_active_family_members(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
) -> list[tuple[FamilyMember, User]]:
    """查询家庭组中的全部有效成员。"""

    statement = (
        select(
            FamilyMember,
            User,
        )
        .join(
            User,
            User.id == FamilyMember.user_id,
        )
        .where(
            FamilyMember.family_id == family_id,
            FamilyMember.status == "active",
        )
        .order_by(
            FamilyMember.joined_at.asc(),
        )
    )

    result = await db.execute(statement)

    return list(result.all())