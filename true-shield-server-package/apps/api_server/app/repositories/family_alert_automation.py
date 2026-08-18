from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.family import FamilyMember


async def list_active_family_ids_for_user(
    db: AsyncSession,
    *,
    user_id: uuid.UUID,
) -> list[uuid.UUID]:
    """查询用户当前加入的全部有效家庭。"""

    statement = (
        select(FamilyMember.family_id)
        .where(
            FamilyMember.user_id == user_id,
            FamilyMember.status == "active",
        )
        .order_by(FamilyMember.joined_at.asc())
    )

    result = await db.execute(statement)

    return list(result.scalars().all())