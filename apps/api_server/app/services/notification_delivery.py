from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Final, Literal, Protocol

DeliveryStatus = Literal[
    "sent",
    "failed",
    "skipped",
]


SUPPORTED_SIMULATED_CHANNELS: Final[set[str]] = {
    "sms",
    "phone",
    "email",
    "push",
}


@dataclass(frozen=True, slots=True)
class NotificationDeliveryRequest:
    """一次通知发送请求。"""

    recipient_id: uuid.UUID

    channel: str

    destination: str

    title: str

    message: str


@dataclass(frozen=True, slots=True)
class NotificationDeliveryResult:
    """一次通知发送结果。"""

    recipient_id: uuid.UUID

    channel: str

    destination: str

    status: DeliveryStatus

    provider: str

    external_message_id: str | None = None

    failure_reason: str | None = None

    @property
    def succeeded(self) -> bool:
        """是否发送成功。"""

        return self.status == "sent"


class NotificationProvider(Protocol):
    """通知发送服务商统一协议。"""

    name: str

    def supports(
        self,
        channel: str,
    ) -> bool:
        """判断服务商是否支持指定通道。"""

    async def send(
        self,
        request: NotificationDeliveryRequest,
    ) -> NotificationDeliveryResult:
        """发送一条通知。"""


class SimulatedNotificationProvider:
    """
    本地模拟通知服务商。

    第一版不实际调用短信或邮件服务，
    但返回结构与真实服务商保持一致。
    """

    name = "simulated"

    def __init__(
        self,
        *,
        failed_recipient_ids: (
            set[uuid.UUID] | None
        ) = None,
    ) -> None:
        self.failed_recipient_ids = (
            failed_recipient_ids or set()
        )

    def supports(
        self,
        channel: str,
    ) -> bool:
        """支持本地定义的常用通知通道。"""

        return (
            channel.strip().lower()
            in SUPPORTED_SIMULATED_CHANNELS
        )

    async def send(
        self,
        request: NotificationDeliveryRequest,
    ) -> NotificationDeliveryResult:
        """模拟发送通知。"""

        normalized_channel = (
            request.channel.strip().lower()
        )

        if not self.supports(normalized_channel):
            return NotificationDeliveryResult(
                recipient_id=request.recipient_id,
                channel=normalized_channel,
                destination=request.destination,
                status="skipped",
                provider=self.name,
                failure_reason=(
                    "unsupported_channel"
                ),
            )

        if (
            request.recipient_id
            in self.failed_recipient_ids
        ):
            return NotificationDeliveryResult(
                recipient_id=request.recipient_id,
                channel=normalized_channel,
                destination=request.destination,
                status="failed",
                provider=self.name,
                failure_reason=(
                    "simulated_delivery_failure"
                ),
            )

        return NotificationDeliveryResult(
            recipient_id=request.recipient_id,
            channel=normalized_channel,
            destination=request.destination,
            status="sent",
            provider=self.name,
            external_message_id=(
                f"sim-{uuid.uuid4()}"
            ),
        )


class NotificationDeliveryService:
    """根据通知通道选择合适的发送服务商。"""

    def __init__(
        self,
        *,
        providers: list[NotificationProvider],
    ) -> None:
        self.providers = providers

    async def deliver(
        self,
        request: NotificationDeliveryRequest,
    ) -> NotificationDeliveryResult:
        """发送一条通知。"""

        normalized_channel = (
            request.channel.strip().lower()
        )

        normalized_request = (
            NotificationDeliveryRequest(
                recipient_id=request.recipient_id,
                channel=normalized_channel,
                destination=(
                    request.destination.strip()
                ),
                title=request.title,
                message=request.message,
            )
        )

        if not normalized_request.destination:
            return NotificationDeliveryResult(
                recipient_id=(
                    normalized_request.recipient_id
                ),
                channel=normalized_channel,
                destination="",
                status="skipped",
                provider="none",
                failure_reason=(
                    "missing_destination"
                ),
            )

        for provider in self.providers:
            if provider.supports(
                normalized_channel
            ):
                return await provider.send(
                    normalized_request
                )

        return NotificationDeliveryResult(
            recipient_id=(
                normalized_request.recipient_id
            ),
            channel=normalized_channel,
            destination=(
                normalized_request.destination
            ),
            status="skipped",
            provider="none",
            failure_reason=(
                "no_provider_for_channel"
            ),
        )

    async def deliver_many(
        self,
        requests: list[
            NotificationDeliveryRequest
        ],
    ) -> list[NotificationDeliveryResult]:
        """依次发送多条通知并返回全部结果。"""

        results: list[
            NotificationDeliveryResult
        ] = []

        for request in requests:
            result = await self.deliver(
                request
            )
            results.append(result)

        return results