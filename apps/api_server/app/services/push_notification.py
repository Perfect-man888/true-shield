from __future__ import annotations

import asyncio
import logging
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import PROJECT_ROOT, settings
from app.models.family_alert import FamilyAlert
from app.repositories.family import list_active_family_members
from app.repositories.push_device import (
    list_active_push_devices_for_users,
    revoke_push_devices_by_installation_ids,
)

logger = logging.getLogger(__name__)

FIREBASE_APP_NAME = "true-shield-push"
MAX_MULTICAST_TARGETS = 500


@dataclass(frozen=True, slots=True)
class PushDeliverySummary:
    """一次家庭推送操作的汇总。"""

    target_count: int = 0
    success_count: int = 0
    failure_count: int = 0
    revoked_count: int = 0
    skipped_reason: str | None = None


class FirebasePushNotConfiguredError(RuntimeError):
    """Firebase 推送没有完成配置。"""


def _resolve_credentials_path(
    configured_path: str,
) -> Path | None:
    normalized = configured_path.strip()

    if not normalized:
        return None

    path = Path(normalized).expanduser()

    if not path.is_absolute():
        path = PROJECT_ROOT / path

    return path.resolve()


def _get_firebase_app():
    """延迟初始化 Firebase Admin，避免未启用推送时影响后端启动。"""

    if not settings.firebase_push_enabled:
        raise FirebasePushNotConfiguredError(
            "FIREBASE_PUSH_ENABLED=false"
        )

    if not settings.firebase_project_id.strip():
        raise FirebasePushNotConfiguredError(
            "缺少 FIREBASE_PROJECT_ID"
        )

    try:
        import firebase_admin
        from firebase_admin import credentials
    except ImportError as error:
        raise FirebasePushNotConfiguredError(
            "缺少 firebase-admin 依赖"
        ) from error

    try:
        return firebase_admin.get_app(FIREBASE_APP_NAME)
    except ValueError:
        pass

    credentials_path = _resolve_credentials_path(
        settings.firebase_credentials_file
    )

    if credentials_path is not None:
        if not credentials_path.is_file():
            raise FirebasePushNotConfiguredError(
                "Firebase 服务账号文件不存在："
                f"{credentials_path}"
            )

        credential = credentials.Certificate(
            str(credentials_path)
        )
    else:
        # 生产环境可通过 GOOGLE_APPLICATION_CREDENTIALS
        # 或工作负载身份提供 Application Default Credentials。
        credential = credentials.ApplicationDefault()

    return firebase_admin.initialize_app(
        credential=credential,
        options={
            "projectId": settings.firebase_project_id.strip(),
        },
        name=FIREBASE_APP_NAME,
    )


def _send_multicast_sync(
    *,
    installation_ids: list[str],
    title: str,
    body: str,
    data: dict[str, str],
):
    """在线程中调用 Firebase Admin 的同步发送 API。"""

    from firebase_admin import messaging

    app = _get_firebase_app()

    message = messaging.MulticastMessage(
        fids=installation_ids,
        notification=messaging.Notification(
            title=title,
            body=body,
        ),
        data=data,
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(
                channel_id="family_alerts",
                sound="default",
            ),
        ),
    )

    return messaging.send_each_for_multicast(
        message,
        app=app,
    )


def _is_invalid_installation_error(
    error: BaseException | None,
) -> bool:
    if error is None:
        return False

    try:
        from firebase_admin import messaging
    except ImportError:
        return False

    return isinstance(
        error,
        (
            messaging.UnregisteredError,
            messaging.SenderIdMismatchError,
        ),
    )


def _normalize_push_data(
    data: Mapping[str, object],
) -> dict[str, str]:
    """FCM data payload 只允许字符串键值。"""

    return {
        str(key): str(value)
        for key, value in data.items()
        if value is not None
    }


async def send_push_to_users(
    db: AsyncSession,
    *,
    user_ids: set[uuid.UUID],
    title: str,
    body: str,
    data: Mapping[str, object],
) -> PushDeliverySummary:
    """向指定用户的全部有效设备发送 FCM 推送。"""

    if not settings.firebase_push_enabled:
        return PushDeliverySummary(
            skipped_reason="firebase_push_disabled",
        )

    devices = await list_active_push_devices_for_users(
        db,
        user_ids=user_ids,
    )

    if not devices:
        return PushDeliverySummary(
            skipped_reason="no_active_devices",
        )

    installation_ids = [
        device.installation_id
        for device in devices
    ]

    normalized_data = _normalize_push_data(data)
    success_count = 0
    failure_count = 0
    invalid_installation_ids: set[str] = set()

    for offset in range(
        0,
        len(installation_ids),
        MAX_MULTICAST_TARGETS,
    ):
        chunk = installation_ids[
            offset : offset + MAX_MULTICAST_TARGETS
        ]

        response = await asyncio.to_thread(
            _send_multicast_sync,
            installation_ids=chunk,
            title=title,
            body=body,
            data=normalized_data,
        )

        success_count += response.success_count
        failure_count += response.failure_count

        for installation_id, send_response in zip(
            chunk,
            response.responses,
            strict=True,
        ):
            if send_response.success:
                continue

            if _is_invalid_installation_error(
                send_response.exception
            ):
                invalid_installation_ids.add(
                    installation_id
                )

            logger.warning(
                "FCM 推送失败：installation_id=%s error=%r",
                installation_id,
                send_response.exception,
            )

    revoked_count = (
        await revoke_push_devices_by_installation_ids(
            db,
            installation_ids=invalid_installation_ids,
        )
    )

    return PushDeliverySummary(
        target_count=len(installation_ids),
        success_count=success_count,
        failure_count=failure_count,
        revoked_count=revoked_count,
    )


async def send_family_alert_push(
    db: AsyncSession,
    *,
    alert: FamilyAlert,
    event_type: str,
    actor_display_name: str | None = None,
) -> PushDeliverySummary:
    """把家庭告警创建或状态变化推送给该家庭的全部成员。"""

    # 默认关闭 FCM。关闭时应立即返回，既不访问数据库，
    # 也不要求调用方为测试桩补齐完整的 ORM 字段。
    if not settings.firebase_push_enabled:
        return PushDeliverySummary(
            skipped_reason="firebase_push_disabled",
        )

    members = await list_active_family_members(
        db,
        family_id=alert.family_id,
    )

    user_ids = {
        member.user_id
        for member, _user in members
    }

    if event_type == "created":
        title = "真信盾：新的家庭高风险告警"
        body = alert.summary
    elif event_type == "acknowledged":
        title = "家庭告警已确认"
        actor_label = actor_display_name or "家庭成员"
        body = f"{actor_label}已确认并开始处理：{alert.title}"
    elif event_type == "resolved":
        title = "家庭告警已处理完成"
        actor_label = actor_display_name or "家庭成员"
        body = f"{actor_label}已完成处理：{alert.title}"
    else:
        title = "家庭告警状态更新"
        body = alert.title

    return await send_push_to_users(
        db,
        user_ids=user_ids,
        title=title,
        body=body,
        data={
            "type": "family_alert",
            "event_type": event_type,
            "family_id": alert.family_id,
            "alert_id": alert.id,
            "status": alert.status,
            "risk_level": alert.risk_level,
        },
    )


async def send_family_alert_push_safely(
    db: AsyncSession,
    *,
    alert: FamilyAlert,
    event_type: str,
    actor_display_name: str | None = None,
) -> PushDeliverySummary | None:
    """推送失败时只记录日志，不影响家庭告警主流程。"""

    try:
        return await send_family_alert_push(
            db,
            alert=alert,
            event_type=event_type,
            actor_display_name=actor_display_name,
        )
    except Exception:
        logger.exception(
            "家庭告警 FCM 推送失败：family_id=%s alert_id=%s event_type=%s",
            getattr(alert, "family_id", None),
            getattr(alert, "id", None),
            event_type,
        )
        return None
