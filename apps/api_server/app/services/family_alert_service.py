from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import UTC, datetime

from app.models.family_alert import (
    FamilyAlertRecipient,
)
from app.models.risk import RiskEvent
from app.models.trusted_contact import TrustedContact
from app.services.notification_delivery import (
    NotificationDeliveryRequest,
    NotificationDeliveryService,
    NotificationProvider,
    SimulatedNotificationProvider,
)

SOURCE_TYPE_LABELS = {
    "text": "文本",
    "image": "图片",
    "url": "链接",
}


def build_family_alert_title(
    event: RiskEvent,
) -> str:
    """根据风险事件类型生成告警标题。"""

    source_label = SOURCE_TYPE_LABELS.get(
        event.source_type,
        "内容",
    )

    return f"发现{source_label}高风险诈骗事件"


def build_family_alert_summary(
    event: RiskEvent,
) -> str:
    """生成家庭告警摘要。"""

    if event.summary:
        return event.summary

    return (
        "检测到高风险事件，请立即联系家庭成员"
        "核实情况并停止可疑操作。"
    )


def get_contact_destination(
    contact: TrustedContact,
) -> str | None:
    """根据首选渠道取得接收地址。"""

    if contact.preferred_channel in {
        "phone",
        "sms",
    }:
        return contact.phone

    if contact.preferred_channel == "email":
        return contact.email

    return None


def build_alert_recipients(
    contacts: list[TrustedContact],
) -> list[dict[str, object]]:
    """将可信联系人转换为告警接收人数据。"""

    return [
        {
            "trusted_contact_id": contact.id,
            "linked_user_id": (
                contact.linked_user_id
            ),
            "recipient_name": (
                contact.display_name
            ),
            "channel": (
                contact.preferred_channel
            ),
            "destination": (
                get_contact_destination(contact)
            ),
        }
        for contact in contacts
    ]

@dataclass(slots=True)
class AlertDispatchSummary:
    """一次告警发送操作的统计结果。"""

    attempted_count: int = 0
    sent_count: int = 0
    failed_count: int = 0
    skipped_count: int = 0
    already_completed_count: int = 0


def apply_simulated_alert_delivery(
    recipients: list[FamilyAlertRecipient],
    *,
    simulated_failure_recipient_ids: set[
        uuid.UUID
    ],
) -> AlertDispatchSummary:
    """
    对告警接收人执行模拟发送。

    规则：
    1. 已发送或已跳过的记录不重复发送；
    2. 非站内渠道缺少接收地址时标记 skipped；
    3. 位于模拟失败集合中的接收人标记 failed；
    4. 其余接收人标记 sent。
    """

    summary = AlertDispatchSummary()

    now = datetime.now(UTC)

    for recipient in recipients:
        if recipient.delivery_status in {
            "sent",
            "skipped",
        }:
            summary.already_completed_count += 1
            continue

        summary.attempted_count += 1

        requires_destination = (
            recipient.channel != "in_app"
        )

        if (
            requires_destination
            and not recipient.destination
        ):
            recipient.delivery_status = "skipped"
            recipient.failure_reason = (
                "缺少该发送渠道需要的接收地址。"
            )
            recipient.sent_at = None

            summary.skipped_count += 1
            continue

        if (
            recipient.id
            in simulated_failure_recipient_ids
        ):
            recipient.delivery_status = "failed"
            recipient.failure_reason = (
                "模拟发送失败。"
            )
            recipient.sent_at = None

            summary.failed_count += 1
            continue

        recipient.delivery_status = "sent"
        recipient.failure_reason = None
        recipient.sent_at = now

        summary.sent_count += 1

    return summary

async def deliver_family_alert_notifications(
    recipients: list[FamilyAlertRecipient],
    *,
    title: str,
    message: str,
    simulated_failure_recipient_ids: (
        set[uuid.UUID] | None
    ) = None,
    providers: list[NotificationProvider] | None = None,
) -> AlertDispatchSummary:
    """
    通过统一通知服务发送家庭告警。

    已发送或已跳过的接收人不会重复发送；
    failed 和 pending 状态可以再次尝试。
    """

    failure_ids = (
        simulated_failure_recipient_ids
        or set()
    )

    delivery_service = NotificationDeliveryService(
        providers=(
            providers
            if providers is not None
            else [
                SimulatedNotificationProvider(
                    failed_recipient_ids=(
                        failure_ids
                    ),
                )
            ]
        ),
    )

    summary = AlertDispatchSummary()

    pending_recipients: list[
        FamilyAlertRecipient
    ] = []

    requests: list[
        NotificationDeliveryRequest
    ] = []

    for recipient in recipients:
        if recipient.delivery_status in {
            "sent",
            "skipped",
        }:
            summary.already_completed_count += 1
            continue

        summary.attempted_count += 1

        pending_recipients.append(recipient)

        requests.append(
            NotificationDeliveryRequest(
                recipient_id=recipient.id,
                channel=recipient.channel,
                destination=recipient.destination,
                title=title,
                message=message,
            )
        )

    results = await delivery_service.deliver_many(
        requests
    )

    now = datetime.now(UTC)

    for recipient, result in zip(
        pending_recipients,
        results,
        strict=True,
    ):
        recipient.delivery_provider = result.provider
        recipient.external_message_id = (
        result.external_message_id
        )
        if result.status == "sent":
            recipient.delivery_status = "sent"
            recipient.failure_reason = None
            recipient.sent_at = now

            summary.sent_count += 1
            continue

        if result.status == "failed":
            recipient.delivery_status = "failed"
            recipient.sent_at = None

            if (
                result.failure_reason
                == "simulated_delivery_failure"
            ):
                recipient.failure_reason = (
                    "模拟发送失败。"
                )
            else:
                recipient.failure_reason = (
                    result.failure_reason
                    or "通知发送失败。"
                )

            summary.failed_count += 1
            continue

        recipient.delivery_status = "skipped"
        recipient.sent_at = None

        if (
            result.failure_reason
            == "missing_destination"
        ):
            recipient.failure_reason = (
                "缺少该发送渠道需要的接收地址。"
            )
        elif (
            result.failure_reason
            == "no_provider_for_channel"
        ):
            recipient.failure_reason = (
                "没有可用的通知发送服务商。"
            )
        else:
            recipient.failure_reason = (
                result.failure_reason
                or "通知发送已跳过。"
            )

        summary.skipped_count += 1

    return summary