from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    String,
    Uuid,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class PushDevice(Base):
    """用户用于接收实时推送的移动设备。"""

    __tablename__ = "push_devices"

    __table_args__ = (
        CheckConstraint(
            "platform IN ('android', 'ios')",
            name="ck_push_devices_platform",
        ),
        CheckConstraint(
            "status IN ('active', 'revoked')",
            name="ck_push_devices_status",
        ),
        Index(
            "ix_push_devices_user_status",
            "user_id",
            "status",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    # Firebase Cloud Messaging 25.1+ 推荐使用
    # Firebase Installation ID（FID）作为单设备目标。
    installation_id: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        unique=True,
        index=True,
    )

    platform: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="android",
        server_default="android",
    )

    device_name: Mapped[str | None] = mapped_column(
        String(128),
        nullable=True,
    )

    app_version: Mapped[str | None] = mapped_column(
        String(32),
        nullable=True,
    )

    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="active",
        server_default="active",
        index=True,
    )

    last_seen_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
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
