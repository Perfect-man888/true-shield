from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
    Uuid,
    func,
)
from sqlalchemy.orm import (
    Mapped,
    mapped_column,
)

from app.db.base import Base


class TrustedContact(Base):
    """家庭成员的可信联系人。"""

    __tablename__ = "trusted_contacts"

    __table_args__ = (
        CheckConstraint(
            "status IN ('active', 'disabled')",
            name="ck_trusted_contacts_status",
        ),
        CheckConstraint(
            "priority BETWEEN 1 AND 5",
            name="ck_trusted_contacts_priority",
        ),
        CheckConstraint(
            (
                "preferred_channel IN "
                "('phone', 'sms', 'email')"
            ),
            name="ck_trusted_contacts_channel",
        ),
        CheckConstraint(
            "phone IS NOT NULL OR email IS NOT NULL",
            name="ck_trusted_contacts_contact_method",
        ),
        CheckConstraint(
            (
                "(preferred_channel = 'email' "
                "AND email IS NOT NULL) "
                "OR "
                "(preferred_channel IN ('phone', 'sms') "
                "AND phone IS NOT NULL)"
            ),
            name="ck_trusted_contacts_channel_method",
        ),
        UniqueConstraint(
            "family_id",
            "subject_user_id",
            "phone",
            name="uq_trusted_contacts_subject_phone",
        ),
        UniqueConstraint(
            "family_id",
            "subject_user_id",
            "email",
            name="uq_trusted_contacts_subject_email",
        ),
        Index(
            "ix_trusted_contacts_family_subject_status",
            "family_id",
            "subject_user_id",
            "status",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    family_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "families.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    subject_user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    linked_user_id: Mapped[
        uuid.UUID | None
    ] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "users.id",
            ondelete="SET NULL",
        ),
        nullable=True,
        index=True,
    )

    created_by_user_id: Mapped[
        uuid.UUID
    ] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    display_name: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
    )

    relationship_label: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
    )

    phone: Mapped[str | None] = mapped_column(
        String(32),
        nullable=True,
    )

    email: Mapped[str | None] = mapped_column(
        String(320),
        nullable=True,
    )

    preferred_channel: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="phone",
        server_default="phone",
    )

    priority: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=3,
        server_default="3",
    )

    can_receive_alerts: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        default=True,
        server_default="true",
    )

    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="active",
        server_default="active",
        index=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now(),
    )