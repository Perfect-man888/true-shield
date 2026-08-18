from __future__ import annotations

import uuid
from datetime import UTC, datetime

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.push_device import PushDevice


async def upsert_push_device(
    db: AsyncSession,
    *,
    user_id: uuid.UUID,
    installation_id: str,
    platform: str,
    device_name: str | None,
    app_version: str | None,
) -> PushDevice:
    """
    注册或刷新设备。

    同一 Firebase 安装可能在同一台手机上退出后登录另一个账户，
    因此以 installation_id 为唯一键，并允许重新绑定到当前用户。
    """

    statement = select(PushDevice).where(
        PushDevice.installation_id == installation_id
    )

    result = await db.execute(statement)
    device = result.scalar_one_or_none()
    now = datetime.now(UTC)

    if device is None:
        device = PushDevice(
            user_id=user_id,
            installation_id=installation_id,
            platform=platform,
            device_name=device_name,
            app_version=app_version,
            status="active",
            last_seen_at=now,
        )
        db.add(device)
    else:
        device.user_id = user_id
        device.platform = platform
        device.device_name = device_name
        device.app_version = app_version
        device.status = "active"
        device.last_seen_at = now

    await db.commit()
    await db.refresh(device)

    return device


async def revoke_push_device(
    db: AsyncSession,
    *,
    user_id: uuid.UUID,
    installation_id: str,
) -> bool:
    """停用当前用户指定的推送设备。"""

    statement = select(PushDevice).where(
        PushDevice.user_id == user_id,
        PushDevice.installation_id == installation_id,
    )

    result = await db.execute(statement)
    device = result.scalar_one_or_none()

    if device is None:
        return False

    device.status = "revoked"
    device.last_seen_at = datetime.now(UTC)

    await db.commit()

    return True


async def revoke_all_push_devices_for_user(
    db: AsyncSession,
    *,
    user_id: uuid.UUID,
    commit: bool = True,
) -> int:
    """停用用户的全部设备，适用于改密和密码找回等安全操作。"""

    statement = (
        update(PushDevice)
        .where(
            PushDevice.user_id == user_id,
            PushDevice.status == "active",
        )
        .values(
            status="revoked",
            last_seen_at=datetime.now(UTC),
        )
    )

    result = await db.execute(statement)

    if commit:
        await db.commit()

    return int(result.rowcount or 0)


async def list_active_push_devices_for_users(
    db: AsyncSession,
    *,
    user_ids: set[uuid.UUID],
) -> list[PushDevice]:
    """查询一组用户的全部有效推送设备。"""

    if not user_ids:
        return []

    statement = (
        select(PushDevice)
        .where(
            PushDevice.user_id.in_(user_ids),
            PushDevice.status == "active",
        )
        .order_by(PushDevice.last_seen_at.desc())
    )

    result = await db.execute(statement)

    return list(result.scalars().all())


async def revoke_push_devices_by_installation_ids(
    db: AsyncSession,
    *,
    installation_ids: set[str],
) -> int:
    """批量停用 Firebase 已判定无效的安装标识。"""

    if not installation_ids:
        return 0

    statement = (
        update(PushDevice)
        .where(
            PushDevice.installation_id.in_(installation_ids)
        )
        .values(
            status="revoked",
            last_seen_at=datetime.now(UTC),
        )
    )

    result = await db.execute(statement)
    await db.commit()

    return int(result.rowcount or 0)
