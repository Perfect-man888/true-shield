from __future__ import annotations

import uuid
from datetime import datetime
from enum import Enum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
)


class FamilyAlertStatus(str, Enum):
    """家庭告警处理状态。"""

    PENDING = "pending"
    ACKNOWLEDGED = "acknowledged"
    RESOLVED = "resolved"
    CANCELLED = "cancelled"


class AlertDeliveryChannel(str, Enum):
    """家庭告警发送渠道。"""

    IN_APP = "in_app"
    PHONE = "phone"
    SMS = "sms"
    EMAIL = "email"


class AlertDeliveryStatus(str, Enum):
    """家庭告警发送状态。"""

    PENDING = "pending"
    SENT = "sent"
    FAILED = "failed"
    SKIPPED = "skipped"


class FamilyAlertRecipientResponse(BaseModel):
    """家庭告警接收人响应。"""

    model_config = ConfigDict(
        from_attributes=True,
    )

    id: uuid.UUID

    alert_id: uuid.UUID

    trusted_contact_id: uuid.UUID | None

    linked_user_id: uuid.UUID | None

    recipient_name: str

    channel: AlertDeliveryChannel

    destination: str | None

    delivery_status: AlertDeliveryStatus

    failure_reason: str | None

    sent_at: datetime | None

    created_at: datetime

    updated_at: datetime


class FamilyAlertResponse(BaseModel):
    """家庭风险告警响应。"""

    model_config = ConfigDict(
        from_attributes=True,
    )

    id: uuid.UUID

    family_id: uuid.UUID

    risk_event_id: uuid.UUID

    subject_user_id: uuid.UUID

    risk_level: str

    title: str

    summary: str

    status: FamilyAlertStatus

    acknowledged_by_user_id: uuid.UUID | None

    acknowledged_at: datetime | None

    resolved_by_user_id: uuid.UUID | None

    resolved_at: datetime | None

    resolution_note: str | None

    created_at: datetime

    updated_at: datetime

    recipients: list[
        FamilyAlertRecipientResponse
    ] = Field(
        default_factory=list,
    )