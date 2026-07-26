from __future__ import annotations

import uuid
from datetime import datetime
from enum import Enum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
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

    delivery_provider: str | None

    external_message_id: str | None

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

class FamilyAlertListResponse(BaseModel):
    """家庭风险告警列表响应。"""

    family_id: uuid.UUID

    items: list[FamilyAlertResponse] = Field(
        default_factory=list,
    )

    total: int = Field(
        ge=0,
    )

class FamilyAlertResolveRequest(BaseModel):
    """完成家庭告警处理时提交的数据。"""

    resolution_note: str | None = Field(
        default=None,
        max_length=2000,
        description="家庭成员对该风险事件的处置说明",
    )

    @field_validator(
        "resolution_note",
        mode="before",
    )
    @classmethod
    def normalize_resolution_note(
        cls,
        value: object,
    ) -> object:
        """清理处置说明首尾空白。"""

        if value is None:
            return None

        if not isinstance(value, str):
            return value

        normalized = value.strip()

        return normalized or None

class FamilyAlertDispatchRequest(BaseModel):
    """模拟发送家庭告警时使用的请求模型。"""

    simulated_failure_recipient_ids: list[
        uuid.UUID
    ] = Field(
        default_factory=list,
        description=(
            "需要模拟发送失败的接收人 ID。"
            "正常调用时传空数组。"
        ),
    )

    @field_validator(
        "simulated_failure_recipient_ids"
    )
    @classmethod
    def validate_unique_recipient_ids(
        cls,
        value: list[uuid.UUID],
    ) -> list[uuid.UUID]:
        """模拟失败接收人不能重复。"""

        if len(value) != len(set(value)):
            raise ValueError(
                "模拟失败接收人 ID 不能重复。"
            )

        return value


class FamilyAlertDispatchResponse(BaseModel):
    """家庭告警模拟发送结果。"""

    family_id: uuid.UUID

    alert_id: uuid.UUID

    attempted_count: int = Field(
        ge=0,
        description="本次实际尝试发送的数量",
    )

    sent_count: int = Field(
        ge=0,
        description="本次发送成功的数量",
    )

    failed_count: int = Field(
        ge=0,
        description="本次发送失败的数量",
    )

    skipped_count: int = Field(
        ge=0,
        description="本次因缺少地址而跳过的数量",
    )

    already_completed_count: int = Field(
        ge=0,
        description="之前已经发送成功或跳过的数量",
    )

    alert: FamilyAlertResponse