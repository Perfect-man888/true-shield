from __future__ import annotations

import uuid
from email.message import EmailMessage

import pytest

from app.services.email_notification_provider import (
    SMTPEmailNotificationProvider,
)
from app.services.notification_delivery import (
    NotificationDeliveryRequest,
    build_notification_delivery_service,
)

pytestmark = pytest.mark.asyncio


def build_email_request(
) -> NotificationDeliveryRequest:
    """构造邮件通知测试请求。"""

    return NotificationDeliveryRequest(
        recipient_id=uuid.uuid4(),
        channel="email",
        destination="family@example.com",
        title="真信盾风险告警",
        message="检测到高风险诈骗事件。",
    )


async def test_smtp_provider_supports_email(
) -> None:
    """SMTP 提供商仅支持 email 渠道。"""

    provider = SMTPEmailNotificationProvider(
        host="smtp.example.com",
        port=587,
        username="",
        password="",
        from_email="alerts@example.com",
        use_tls=False,
    )

    assert provider.supports("email") is True
    assert provider.supports(" EMAIL ") is True
    assert provider.supports("sms") is False


async def test_smtp_provider_sends_email(
) -> None:
    """使用测试传输函数验证邮件内容。"""

    captured_messages: list[
        EmailMessage
    ] = []

    def fake_transport(
        message: EmailMessage,
    ) -> None:
        captured_messages.append(message)

    provider = SMTPEmailNotificationProvider(
        host="smtp.example.com",
        port=587,
        username="",
        password="",
        from_email="alerts@example.com",
        use_tls=False,
        transport=fake_transport,
    )

    result = await provider.send(
        build_email_request()
    )

    assert result.status == "sent"
    assert result.provider == "smtp"
    assert result.external_message_id
    assert result.failure_reason is None

    assert len(captured_messages) == 1

    sent_message = captured_messages[0]

    assert sent_message["Subject"] == (
        "真信盾风险告警"
    )
    assert sent_message["From"] == (
        "alerts@example.com"
    )
    assert sent_message["To"] == (
        "family@example.com"
    )


async def test_smtp_provider_handles_failure(
) -> None:
    """SMTP 异常应转换为标准失败结果。"""

    def failed_transport(
        message: EmailMessage,
    ) -> None:
        raise OSError("SMTP unavailable")

    provider = SMTPEmailNotificationProvider(
        host="smtp.example.com",
        port=587,
        username="",
        password="",
        from_email="alerts@example.com",
        use_tls=False,
        transport=failed_transport,
    )

    result = await provider.send(
        build_email_request()
    )

    assert result.status == "failed"
    assert result.provider == "smtp"
    assert (
        result.failure_reason
        == "smtp_delivery_failure"
    )
    assert result.external_message_id is None


async def test_factory_builds_smtp_provider(
) -> None:
    """通知工厂能够创建 SMTP 提供商。"""

    service = build_notification_delivery_service(
        provider_name="email",
    )

    assert len(service.providers) == 1
    assert service.providers[0].name == "smtp"