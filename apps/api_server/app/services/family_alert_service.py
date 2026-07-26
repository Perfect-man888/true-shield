from __future__ import annotations

from app.models.risk import RiskEvent
from app.models.trusted_contact import TrustedContact

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