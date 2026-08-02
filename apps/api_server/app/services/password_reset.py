from __future__ import annotations

import hashlib
import hmac
import logging
import secrets
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import hash_password, verify_password
from app.models.password_reset import PasswordResetCode
from app.repositories.password_reset import PasswordResetRepository
from app.repositories.push_device import (
    revoke_all_push_devices_for_user,
)
from app.repositories.user import UserRepository
from app.services.notification_delivery import (
    NotificationDeliveryRequest,
    build_notification_delivery_service,
)

logger = logging.getLogger(__name__)

GENERIC_MESSAGE = "如果该邮箱已注册，密码重置验证码将发送到该邮箱。"


class InvalidPasswordResetCodeError(Exception):
    """验证码不存在、已失效或校验失败。"""


class PasswordReuseNotAllowedError(Exception):
    """新密码与当前密码相同。"""


@dataclass(frozen=True, slots=True)
class PasswordResetRequestResult:
    """申请重置验证码后的通用结果。"""

    message: str
    expires_in: int
    debug_code: str | None = None


def _utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=UTC)
    return value.astimezone(UTC)


def _hash_code(*, user_id: object, code: str) -> str:
    payload = f"{user_id}:{code}".encode("utf-8")
    return hmac.new(
        settings.jwt_secret_key.encode("utf-8"),
        payload,
        hashlib.sha256,
    ).hexdigest()


def _generate_code() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


async def request_password_reset_code(
    session: AsyncSession,
    *,
    email: str,
) -> PasswordResetRequestResult:
    """创建验证码并通过邮件发送，响应不泄露账户是否存在。"""

    expires_in = settings.password_reset_code_expire_minutes * 60
    generic_result = PasswordResetRequestResult(
        message=GENERIC_MESSAGE,
        expires_in=expires_in,
    )

    user = await UserRepository(session).get_by_email(
        email.strip().lower()
    )

    if user is None or user.status != "active":
        return generic_result

    repository = PasswordResetRepository(session)
    now = datetime.now(UTC)
    latest = await repository.get_latest_for_user(user.id)

    if latest is not None:
        created_at = _utc(latest.created_at)
        retry_at = created_at + timedelta(
            seconds=settings.password_reset_resend_interval_seconds
        )
        if retry_at > now:
            return generic_result

    code = _generate_code()
    expires_at = now + timedelta(
        minutes=settings.password_reset_code_expire_minutes
    )

    await repository.invalidate_unused_for_user(
        user.id,
        used_at=now,
    )
    await repository.add(
        PasswordResetCode(
            user_id=user.id,
            code_hash=_hash_code(
                user_id=user.id,
                code=code,
            ),
            expires_at=expires_at,
        )
    )
    await session.commit()

    try:
        delivery_service = build_notification_delivery_service()
        delivery_result = await delivery_service.deliver(
            NotificationDeliveryRequest(
                recipient_id=user.id,
                channel="email",
                destination=user.email,
                title="真信盾密码重置验证码",
                message=(
                    f"你的真信盾密码重置验证码是：{code}\n\n"
                    f"验证码将在 {settings.password_reset_code_expire_minutes} 分钟后失效。\n"
                    "如果不是你本人操作，请忽略本邮件。"
                ),
            )
        )
        if not delivery_result.succeeded:
            logger.warning(
                "Password reset email delivery failed: provider=%s reason=%s",
                delivery_result.provider,
                delivery_result.failure_reason,
            )
    except Exception:
        logger.exception("Password reset email delivery raised an exception")

    expose_debug_code = (
        settings.debug
        and settings.app_env.lower() != "production"
        and settings.notification_provider.strip().lower() == "simulated"
    )

    return PasswordResetRequestResult(
        message=GENERIC_MESSAGE,
        expires_in=expires_in,
        debug_code=code if expose_debug_code else None,
    )


async def confirm_password_reset(
    session: AsyncSession,
    *,
    email: str,
    code: str,
    new_password: str,
) -> None:
    """校验验证码并替换密码哈希。"""

    user = await UserRepository(session).get_by_email(
        email.strip().lower()
    )
    if user is None or user.status != "active":
        raise InvalidPasswordResetCodeError

    repository = PasswordResetRepository(session)
    reset_code = await repository.get_latest_unused_for_user(user.id)
    now = datetime.now(UTC)

    if reset_code is None:
        raise InvalidPasswordResetCodeError

    if _utc(reset_code.expires_at) <= now:
        reset_code.used_at = now
        await session.commit()
        raise InvalidPasswordResetCodeError

    if reset_code.failed_attempts >= settings.password_reset_max_failed_attempts:
        reset_code.used_at = now
        await session.commit()
        raise InvalidPasswordResetCodeError

    expected_hash = _hash_code(
        user_id=user.id,
        code=code.strip(),
    )

    if not hmac.compare_digest(reset_code.code_hash, expected_hash):
        reset_code.failed_attempts += 1
        if reset_code.failed_attempts >= settings.password_reset_max_failed_attempts:
            reset_code.used_at = now
        await session.commit()
        raise InvalidPasswordResetCodeError

    if verify_password(new_password, user.password_hash):
        raise PasswordReuseNotAllowedError

    user.password_hash = hash_password(new_password)
    user.auth_version += 1
    reset_code.used_at = now
    await repository.invalidate_unused_for_user(
        user.id,
        used_at=now,
    )
    await revoke_all_push_devices_for_user(
        session,
        user_id=user.id,
        commit=False,
    )
    await session.commit()
