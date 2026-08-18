from __future__ import annotations

import asyncio
import smtplib
from collections.abc import Callable
from email.message import EmailMessage
from email.utils import make_msgid

from app.services.notification_delivery import (
    NotificationDeliveryRequest,
    NotificationDeliveryResult,
)

EmailTransport = Callable[[EmailMessage], None]


class SMTPEmailNotificationProvider:
    """通过 SMTP 发送电子邮件通知。"""

    name = "smtp"

    def __init__(
        self,
        *,
        host: str,
        port: int,
        username: str,
        password: str,
        from_email: str,
        use_tls: bool = True,
        timeout_seconds: float = 10.0,
        transport: EmailTransport | None = None,
    ) -> None:
        self.host = host.strip()
        self.port = port
        self.username = username.strip()
        self.password = password
        self.from_email = from_email.strip()
        self.use_tls = use_tls
        self.timeout_seconds = timeout_seconds
        self.transport = transport

    def supports(
        self,
        channel: str,
    ) -> bool:
        """SMTP 提供商只处理 email 渠道。"""

        return channel.strip().lower() == "email"

    def _send_message_sync(
        self,
        message: EmailMessage,
    ) -> None:
        """在线程中执行同步 SMTP 发送。"""

        if self.transport is not None:
            self.transport(message)
            return

        with smtplib.SMTP(
            host=self.host,
            port=self.port,
            timeout=self.timeout_seconds,
        ) as client:
            client.ehlo()

            if self.use_tls:
                client.starttls()
                client.ehlo()

            if self.username:
                client.login(
                    self.username,
                    self.password,
                )

            client.send_message(message)

    async def send(
        self,
        request: NotificationDeliveryRequest,
    ) -> NotificationDeliveryResult:
        """发送一封邮件通知。"""

        normalized_channel = (
            request.channel.strip().lower()
        )

        normalized_destination = (
            request.destination.strip()
            if request.destination
            else None
        )

        if not self.supports(normalized_channel):
            return NotificationDeliveryResult(
                recipient_id=request.recipient_id,
                channel=normalized_channel,
                destination=normalized_destination,
                status="skipped",
                provider=self.name,
                failure_reason=(
                    "unsupported_channel"
                ),
            )

        if not normalized_destination:
            return NotificationDeliveryResult(
                recipient_id=request.recipient_id,
                channel=normalized_channel,
                destination=None,
                status="skipped",
                provider=self.name,
                failure_reason=(
                    "missing_destination"
                ),
            )

        if not self.host or not self.from_email:
            return NotificationDeliveryResult(
                recipient_id=request.recipient_id,
                channel=normalized_channel,
                destination=normalized_destination,
                status="failed",
                provider=self.name,
                failure_reason=(
                    "smtp_not_configured"
                ),
            )

        message = EmailMessage()

        message_id = make_msgid()

        message["Message-ID"] = message_id
        message["Subject"] = request.title
        message["From"] = self.from_email
        message["To"] = normalized_destination

        message.set_content(request.message)

        try:
            await asyncio.to_thread(
                self._send_message_sync,
                message,
            )
        except Exception:
            return NotificationDeliveryResult(
                recipient_id=request.recipient_id,
                channel=normalized_channel,
                destination=normalized_destination,
                status="failed",
                provider=self.name,
                failure_reason=(
                    "smtp_delivery_failure"
                ),
            )

        return NotificationDeliveryResult(
            recipient_id=request.recipient_id,
            channel=normalized_channel,
            destination=normalized_destination,
            status="sent",
            provider=self.name,
            external_message_id=message_id,
        )