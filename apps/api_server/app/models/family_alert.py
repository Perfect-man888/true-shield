from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    func,
)
from sqlalchemy.orm import (
    Mapped,
    mapped_column,
    relationship,
)

from app.db.base import Base


class FamilyAlert(Base):
    """由风险事件产生的家庭风险告警。"""

    __tablename__ = "family_alerts"

    __table_args__ = (
        CheckConstraint(
            (
                "risk_level IN "
                "('low', 'medium', 'high')"
            ),
            name="ck_family_alerts_risk_level",
        ),
        CheckConstraint(
            (
                "status IN ("
                "'pending', "
                "'acknowledged', "
                "'resolved', "
                "'cancelled'"
                ")"
            ),
            name="ck_family_alerts_status",
        ),
        UniqueConstraint(
            "family_id",
            "risk_event_id",
            name="uq_family_alerts_family_event",
        ),
        Index(
            "ix_family_alerts_family_status_created",
            "family_id",
            "status",
            "created_at",
        ),
        Index(
            "ix_family_alerts_subject_created",
            "subject_user_id",
            "created_at",
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

    risk_event_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "risk_events.id",
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

    risk_level: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )

    title: Mapped[str] = mapped_column(
        String(120),
        nullable=False,
    )

    summary: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="pending",
        server_default="pending",
        index=True,
    )

    acknowledged_by_user_id: Mapped[
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

    acknowledged_at: Mapped[
        datetime | None
    ] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    resolved_by_user_id: Mapped[
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

    resolved_at: Mapped[
        datetime | None
    ] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    resolution_note: Mapped[
        str | None
    ] = mapped_column(
        Text,
        nullable=True,
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

    recipients: Mapped[
        list[FamilyAlertRecipient]
    ] = relationship(
        back_populates="alert",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )


class FamilyAlertRecipient(Base):
    """家庭风险告警的接收人及发送状态。"""

    __tablename__ = "family_alert_recipients"

    __table_args__ = (
        CheckConstraint(
            (
                "channel IN "
                "('in_app', 'phone', 'sms', 'email')"
            ),
            name="ck_family_alert_recipients_channel",
        ),
        CheckConstraint(
            (
                "delivery_status IN ("
                "'pending', "
                "'sent', "
                "'failed', "
                "'skipped'"
                ")"
            ),
            name=(
                "ck_family_alert_recipients_"
                "delivery_status"
            ),
        ),
        UniqueConstraint(
            "alert_id",
            "trusted_contact_id",
            "channel",
            name=(
                "uq_family_alert_recipients_"
                "contact_channel"
            ),
        ),
        Index(
            "ix_family_alert_recipients_delivery",
            "delivery_status",
            "created_at",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    alert_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "family_alerts.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    trusted_contact_id: Mapped[
        uuid.UUID | None
    ] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "trusted_contacts.id",
            ondelete="SET NULL",
        ),
        nullable=True,
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

    recipient_name: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
    )

    channel: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )

    destination: Mapped[
        str | None
    ] = mapped_column(
        String(320),
        nullable=True,
    )

    delivery_status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="pending",
        server_default="pending",
        index=True,
    )

    delivery_provider: Mapped[
        str | None
    ] = mapped_column(
        String(64),
        nullable=True,
    )

    external_message_id: Mapped[
        str | None
    ] = mapped_column(
        String(255),
        nullable=True,
    )
    failure_reason: Mapped[
        str | None
    ] = mapped_column(
        String(500),
        nullable=True,
    )

    sent_at: Mapped[
        datetime | None
    ] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
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

    alert: Mapped[FamilyAlert] = relationship(
        back_populates="recipients",
    )