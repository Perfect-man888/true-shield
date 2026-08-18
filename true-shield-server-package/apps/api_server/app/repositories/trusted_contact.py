from __future__ import annotations

import uuid

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.trusted_contact import TrustedContact
from app.models.user import User


async def get_user_by_contact_email(
    db: AsyncSession,
    *,
    email: str,
) -> User | None:
    """根据联系人邮箱查找注册用户。"""

    statement = select(User).where(
        func.lower(User.email)
        == email.lower(),
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def get_user_by_contact_phone(
    db: AsyncSession,
    *,
    phone: str,
) -> User | None:
    """根据联系人手机号查找注册用户。"""

    statement = select(User).where(
        User.phone == phone,
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def create_trusted_contact(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    subject_user_id: uuid.UUID,
    linked_user_id: uuid.UUID | None,
    created_by_user_id: uuid.UUID,
    display_name: str,
    relationship_label: str,
    phone: str | None,
    email: str | None,
    preferred_channel: str,
    priority: int,
    can_receive_alerts: bool,
) -> TrustedContact:
    """创建可信联系人。"""

    contact = TrustedContact(
        family_id=family_id,
        subject_user_id=subject_user_id,
        linked_user_id=linked_user_id,
        created_by_user_id=created_by_user_id,
        display_name=display_name,
        relationship_label=relationship_label,
        phone=phone,
        email=email,
        preferred_channel=preferred_channel,
        priority=priority,
        can_receive_alerts=can_receive_alerts,
        status="active",
    )

    db.add(contact)

    await db.commit()
    await db.refresh(contact)

    return contact


async def list_trusted_contacts(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    subject_user_id: uuid.UUID,
    include_disabled: bool,
) -> list[TrustedContact]:
    """查询指定家庭成员的可信联系人。"""

    statement = select(TrustedContact).where(
        TrustedContact.family_id == family_id,
        TrustedContact.subject_user_id
        == subject_user_id,
    )

    if not include_disabled:
        statement = statement.where(
            TrustedContact.status == "active",
        )

    statement = statement.order_by(
        TrustedContact.priority.asc(),
        TrustedContact.created_at.asc(),
    )

    result = await db.execute(statement)

    return list(result.scalars().all())


async def get_trusted_contact_by_id(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    contact_id: uuid.UUID,
) -> TrustedContact | None:
    """查询家庭内的一条可信联系人记录。"""

    statement = select(TrustedContact).where(
        TrustedContact.id == contact_id,
        TrustedContact.family_id == family_id,
    )

    result = await db.execute(statement)

    return result.scalar_one_or_none()


async def update_trusted_contact(
    db: AsyncSession,
    *,
    contact: TrustedContact,
    linked_user_id: uuid.UUID | None,
    display_name: str,
    relationship_label: str,
    phone: str | None,
    email: str | None,
    preferred_channel: str,
    priority: int,
    can_receive_alerts: bool,
) -> TrustedContact:
    """更新可信联系人。"""

    contact.linked_user_id = linked_user_id
    contact.display_name = display_name
    contact.relationship_label = relationship_label
    contact.phone = phone
    contact.email = email
    contact.preferred_channel = preferred_channel
    contact.priority = priority
    contact.can_receive_alerts = (
        can_receive_alerts
    )

    await db.commit()
    await db.refresh(contact)

    return contact


async def disable_trusted_contact(
    db: AsyncSession,
    *,
    contact: TrustedContact,
) -> TrustedContact:
    """停用可信联系人。"""

    contact.status = "disabled"

    await db.commit()
    await db.refresh(contact)

    return contact