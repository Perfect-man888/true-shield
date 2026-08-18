import uuid
from types import SimpleNamespace

import pytest

import app.services.push_notification as push_service


@pytest.mark.asyncio
async def test_disabled_family_push_does_not_query_database(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """未启用 Firebase 时，告警主流程不应产生额外数据库查询。"""

    monkeypatch.setattr(
        push_service.settings,
        "firebase_push_enabled",
        False,
    )

    async def fail_if_called(*args, **kwargs):
        raise AssertionError("Firebase 关闭时不应查询家庭成员")

    monkeypatch.setattr(
        push_service,
        "list_active_family_members",
        fail_if_called,
    )

    result = await push_service.send_family_alert_push(
        object(),
        alert=SimpleNamespace(id=uuid.uuid4()),
        event_type="created",
    )

    assert result.skipped_reason == "firebase_push_disabled"
    assert result.target_count == 0


@pytest.mark.asyncio
async def test_push_uses_installation_ids_and_string_data(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """推送应使用 FID，并把 data payload 统一转换为字符串。"""

    monkeypatch.setattr(
        push_service.settings,
        "firebase_push_enabled",
        True,
    )

    installation_ids = [
        "fid-device-one-example",
        "fid-device-two-example",
    ]

    async def fake_list_devices(db, *, user_ids):
        assert user_ids
        return [
            SimpleNamespace(installation_id=value)
            for value in installation_ids
        ]

    sent_payloads: list[dict[str, object]] = []

    def fake_send_multicast_sync(**kwargs):
        sent_payloads.append(kwargs)
        return SimpleNamespace(
            success_count=2,
            failure_count=0,
            responses=[
                SimpleNamespace(
                    success=True,
                    exception=None,
                ),
                SimpleNamespace(
                    success=True,
                    exception=None,
                ),
            ],
        )

    async def fake_revoke(db, *, installation_ids):
        assert installation_ids == set()
        return 0

    monkeypatch.setattr(
        push_service,
        "list_active_push_devices_for_users",
        fake_list_devices,
    )
    monkeypatch.setattr(
        push_service,
        "_send_multicast_sync",
        fake_send_multicast_sync,
    )
    monkeypatch.setattr(
        push_service,
        "revoke_push_devices_by_installation_ids",
        fake_revoke,
    )

    family_id = uuid.uuid4()

    result = await push_service.send_push_to_users(
        object(),
        user_ids={uuid.uuid4()},
        title="家庭告警",
        body="请立即查看",
        data={
            "family_id": family_id,
            "attempt": 1,
            "optional": None,
        },
    )

    assert result.target_count == 2
    assert result.success_count == 2
    assert result.failure_count == 0
    assert sent_payloads == [
        {
            "installation_ids": installation_ids,
            "title": "家庭告警",
            "body": "请立即查看",
            "data": {
                "family_id": str(family_id),
                "attempt": "1",
            },
        }
    ]
    assert isinstance(
        sent_payloads[0]["data"]["family_id"],
        str,
    )
