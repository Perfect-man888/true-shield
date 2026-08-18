from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.family import (
    Family,
    FamilyInvitation,
    FamilyMember,
)
from app.models.user import User


async def get_user_by_email(
    db: AsyncSession,
    *,
    email: str,
) -> User | None:
    """根据邮箱查询用户。"""

    statement = select(User).where(
        User.email == email,
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def get_active_member(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    user_id: uuid.UUID,
) -> FamilyMember | None:
    """查询用户是否已经是家庭有效成员。"""

    statement = select(FamilyMember).where(
        FamilyMember.family_id == family_id,
        FamilyMember.user_id == user_id,
        FamilyMember.status == "active",
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def get_active_pending_invitation(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    invitee_email: str,
    now: datetime,
) -> FamilyInvitation | None:
    """查询尚未过期的重复待处理邀请。"""

    statement = select(FamilyInvitation).where(
        FamilyInvitation.family_id == family_id,
        FamilyInvitation.invitee_email == invitee_email,
        FamilyInvitation.status == "pending",
        FamilyInvitation.expires_at > now,
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def create_family_invitation(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    inviter_user_id: uuid.UUID,
    invitee_email: str,
    role: str,
    expires_at: datetime,
) -> FamilyInvitation:
    """创建家庭邀请。"""

    invitation = FamilyInvitation(
        family_id=family_id,
        inviter_user_id=inviter_user_id,
        invitee_email=invitee_email,
        role=role,
        status="pending",
        expires_at=expires_at,
    )

    db.add(invitation)

    await db.commit()
    await db.refresh(invitation)

    return invitation


async def list_received_pending_invitations(
    db: AsyncSession,
    *,
    invitee_email: str,
    now: datetime,
) -> list[
    tuple[
        FamilyInvitation,
        Family,
        User,
    ]
]:
    """查询当前邮箱收到且尚未过期的邀请。"""

    statement = (
        select(
            FamilyInvitation,
            Family,
            User,
        )
        .join(
            Family,
            Family.id
            == FamilyInvitation.family_id,
        )
        .join(
            User,
            User.id
            == FamilyInvitation.inviter_user_id,
        )
        .where(
            FamilyInvitation.invitee_email
            == invitee_email,
            FamilyInvitation.status
            == "pending",
            FamilyInvitation.expires_at > now,
            Family.status == "active",
        )
        .order_by(
            FamilyInvitation.created_at.desc(),
        )
    )

    result = await db.execute(statement)

    return list(result.all())


async def get_invitation_for_recipient(
    db: AsyncSession,
    *,
    invitation_id: uuid.UUID,
    invitee_email: str,
) -> tuple[FamilyInvitation, Family] | None:
    """查询属于指定邮箱的邀请。"""

    statement = (
        select(
            FamilyInvitation,
            Family,
        )
        .join(
            Family,
            Family.id
            == FamilyInvitation.family_id,
        )
        .where(
            FamilyInvitation.id
            == invitation_id,
            FamilyInvitation.invitee_email
            == invitee_email,
        )
    )

    result = await db.execute(statement)

    return result.one_or_none()


async def accept_family_invitation(
    db: AsyncSession,
    *,
    invitation: FamilyInvitation,
    user_id: uuid.UUID,
    responded_at: datetime,
) -> FamilyMember:
    """接受邀请并创建正式成员记录。"""

    member = FamilyMember(
        family_id=invitation.family_id,
        user_id=user_id,
        role=invitation.role,
        status="active",
        joined_at=responded_at,
    )

    invitation.status = "accepted"
    invitation.responded_at = responded_at

    db.add(member)

    await db.commit()

    await db.refresh(invitation)
    await db.refresh(member)

    return member


async def decline_family_invitation(
    db: AsyncSession,
    *,
    invitation: FamilyInvitation,
    responded_at: datetime,
) -> FamilyInvitation:
    """拒绝家庭邀请。"""

    invitation.status = "declined"
    invitation.responded_at = responded_at

    await db.commit()
    await db.refresh(invitation)

    return invitation


async def expire_family_invitation(
    db: AsyncSession,
    *,
    invitation: FamilyInvitation,
    responded_at: datetime,
) -> FamilyInvitation:
    """将过期邀请更新为 expired。"""

    invitation.status = "expired"
    invitation.responded_at = responded_at

    await db.commit()
    await db.refresh(invitation)

    return invitation