from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import hash_password, verify_password
from app.models.user import User
from app.repositories.push_device import (
    revoke_all_push_devices_for_user,
)
from app.schemas.user import PasswordChangeRequest


class CurrentPasswordIncorrectError(Exception):
    """用户提交的当前密码不正确。"""


async def change_user_password(
    session: AsyncSession,
    current_user: User,
    data: PasswordChangeRequest,
) -> None:
    """校验当前密码并保存新的密码哈希。"""

    if not verify_password(
        data.current_password,
        current_user.password_hash,
    ):
        raise CurrentPasswordIncorrectError

    current_user.password_hash = hash_password(
        data.new_password,
    )
    current_user.auth_version += 1

    # 改密会使当前 JWT 立即失效，因此必须在同一事务中
    # 停用全部推送设备，避免旧设备在退出后继续接收告警。
    await revoke_all_push_devices_for_user(
        session,
        user_id=current_user.id,
        commit=False,
    )

    await session.commit()
