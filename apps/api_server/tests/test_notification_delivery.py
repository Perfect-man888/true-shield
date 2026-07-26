from __future__ import annotations

import uuid

import pytest

from app.services.notification_delivery import (
    NotificationDeliveryRequest,
    NotificationDeliveryService,
    SimulatedNotificationProvider,
    build_notification_delivery_service,
)

pytestmark = pytest.mark.asyncio


def build_request(
    *,
    recipient_id: uuid.UUID | None = None,
    channel: str = "sms",
    destination: str = "13800138000",
) -> NotificationDeliveryRequest:
    """构造测试发送请求。"""

    return NotificationDeliveryRequest(
        recipient_id=(
            recipient_id or uuid.uuid4()
        ),
        channel=channel,
        destination=destination,
        title="真信盾风险告警",
        message="检测到高风险诈骗事件。",
    )


async def test_simulated_provider_sends_sms() -> None:
    """模拟服务商能够发送短信通知。"""

    service = NotificationDeliveryService(
        providers=[
            SimulatedNotificationProvider()
        ],
    )

    request = build_request()

    result = await service.deliver(request)

    assert result.status == "sent"
    assert result.succeeded is True
    assert result.provider == "simulated"
    assert result.external_message_id
    assert result.failure_reason is None


async def test_channel_is_normalized() -> None:
    """通知通道应自动转换为小写。"""

    service = NotificationDeliveryService(
        providers=[
            SimulatedNotificationProvider()
        ],
    )

    result = await service.deliver(
        build_request(
            channel=" SMS ",
        )
    )

    assert result.status == "sent"
    assert result.channel == "sms"


async def test_simulated_failure() -> None:
    """指定接收人可以模拟发送失败。"""

    recipient_id = uuid.uuid4()

    service = NotificationDeliveryService(
        providers=[
            SimulatedNotificationProvider(
                failed_recipient_ids={
                    recipient_id
                },
            )
        ],
    )

    result = await service.deliver(
        build_request(
            recipient_id=recipient_id,
        )
    )

    assert result.status == "failed"
    assert result.succeeded is False
    assert (
        result.failure_reason
        == "simulated_delivery_failure"
    )


async def test_missing_destination_is_skipped() -> None:
    """缺少接收地址时不应调用服务商。"""

    service = NotificationDeliveryService(
        providers=[
            SimulatedNotificationProvider()
        ],
    )

    result = await service.deliver(
        build_request(
            destination="   ",
        )
    )

    assert result.destination is None
    assert result.status == "skipped"
    assert result.provider == "none"
    assert (
        result.failure_reason
        == "missing_destination"
    )


async def test_unsupported_channel_is_skipped() -> None:
    """没有对应服务商的通道应被跳过。"""

    service = NotificationDeliveryService(
        providers=[
            SimulatedNotificationProvider()
        ],
    )

    result = await service.deliver(
        build_request(
            channel="fax",
        )
    )

    assert result.status == "skipped"
    assert result.provider == "none"
    assert (
        result.failure_reason
        == "no_provider_for_channel"
    )


async def test_deliver_many_returns_all_results() -> None:
    """批量发送应返回每个接收人的结果。"""

    failed_recipient_id = uuid.uuid4()

    service = NotificationDeliveryService(
        providers=[
            SimulatedNotificationProvider(
                failed_recipient_ids={
                    failed_recipient_id
                },
            )
        ],
    )

    results = await service.deliver_many(
        [
            build_request(),
            build_request(
                recipient_id=(
                    failed_recipient_id
                ),
            ),
        ]
    )

    assert len(results) == 2
    assert results[0].status == "sent"
    assert results[1].status == "failed"

async def test_factory_builds_simulated_provider() -> None:
    """工厂能够创建模拟通知服务。"""

    service = build_notification_delivery_service(
        provider_name="simulated",
    )

    result = await service.deliver(
        build_request()
    )

    assert result.status == "sent"
    assert result.provider == "simulated"
    assert result.external_message_id


async def test_factory_rejects_unknown_provider() -> None:
    """不支持的通知服务商应明确报错。"""

    with pytest.raises(
        ValueError,
        match="不支持的通知服务商",
    ):
        build_notification_delivery_service(
            provider_name="unknown",
        )