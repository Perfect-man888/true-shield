from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import (
    JSON,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    func,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import (
    Mapped,
    mapped_column,
    relationship,
)

from app.db.base import Base


class RiskEvent(Base):
    """一次完整的风险分析事件。"""

    __tablename__ = "risk_events"

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

    source_type: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        default="text",
        server_default="text",
    )

    source_text: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    risk_level: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
        index=True,
    )

    risk_score: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    summary: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    actions: Mapped[list[str]] = mapped_column(
        JSON,
        nullable=False,
        default=list,
    )

    disclaimer: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    rule_version: Mapped[str] = mapped_column(
        String(32),
        nullable=False,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        index=True,
    )

    signals: Mapped[list[RiskSignal]] = relationship(
        back_populates="event",
        cascade="all, delete-orphan",
        passive_deletes=True,
        lazy="selectin",
    )

    def __repr__(self) -> str:
        return (
            "RiskEvent("
            f"id={self.id!r}, "
            f"user_id={self.user_id!r}, "
            f"risk_level={self.risk_level!r}, "
            f"risk_score={self.risk_score!r}"
            ")"
        )


class RiskSignal(Base):
    """风险事件中命中的一条具体规则证据。"""

    __tablename__ = "risk_signals"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    event_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "risk_events.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    rule_id: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        index=True,
    )

    signal_type: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        index=True,
    )

    title: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
    )

    matched_terms: Mapped[list[str]] = mapped_column(
        JSON,
        nullable=False,
        default=list,
    )

    tags: Mapped[list[str]] = mapped_column(
        JSON,
        nullable=False,
        default=list,
    )

    match_positions: Mapped[
        list[dict[str, object]]
    ] = mapped_column(
        JSON,
        nullable=False,
        default=list,
    )

    signal_weight: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    explanation: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    event: Mapped[RiskEvent] = relationship(
        back_populates="signals",
    )

    def __repr__(self) -> str:
        return (
            "RiskSignal("
            f"id={self.id!r}, "
            f"event_id={self.event_id!r}, "
            f"rule_id={self.rule_id!r}"
            ")"
        )

class RiskFeedback(Base):
    """
    用户对风险分析事件的反馈。

    同一个用户对同一个风险事件只保留一条反馈。
    """

    __tablename__ = "risk_feedbacks"

    __table_args__ = (
        UniqueConstraint(
            "event_id",
            "user_id",
            name="uq_risk_feedbacks_event_user",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    event_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey(
            "risk_events.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        nullable=False,
        index=True,
    )

    feedback_type: Mapped[str] = mapped_column(
        String(32),
        nullable=False,
    )

    expected_risk_level: Mapped[str | None] = mapped_column(
        String(16),
        nullable=True,
    )

    comment: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )