from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import (
    JSON,
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Uuid,
    func,
    true,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class FamilyAlertPolicy(Base):
    """家庭风险告警自动触发与发送策略。"""

    __tablename__ = "family_alert_policies"

    __table_args__ = (
        CheckConstraint(
            "minimum_risk_level IN ('low', 'medium', 'high')",
            name=(
                "ck_family_alert_policies_"
                "minimum_risk_level"
            ),
        ),
        CheckConstraint(
            "max_recipients >= 1 AND max_recipients <= 20",
            name=(
                "ck_family_alert_policies_"
                "max_recipients"
            ),
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
        unique=True,
        index=True,
    )

    auto_create_enabled: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        default=True,
        server_default=true(),
    )

    auto_dispatch_enabled: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        default=False,
        server_default="false",
    )

    minimum_risk_level: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="high",
        server_default="high",
    )

    enabled_source_types: Mapped[list[str]] = mapped_column(
        JSON,
        nullable=False,
        default=lambda: [
            "text",
            "image",
            "url",
        ],
    )

    max_recipients: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=3,
        server_default="3",
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