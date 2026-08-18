from datetime import datetime
from uuid import UUID

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.password_reset import PasswordResetCode


class PasswordResetRepository:
    """密码重置验证码数据访问层。"""

    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def add(
        self,
        reset_code: PasswordResetCode,
    ) -> PasswordResetCode:
        self.session.add(reset_code)
        await self.session.flush()
        return reset_code

    async def get_latest_for_user(
        self,
        user_id: UUID,
    ) -> PasswordResetCode | None:
        result = await self.session.execute(
            select(PasswordResetCode)
            .where(PasswordResetCode.user_id == user_id)
            .order_by(PasswordResetCode.created_at.desc())
            .limit(1)
        )
        return result.scalar_one_or_none()

    async def get_latest_unused_for_user(
        self,
        user_id: UUID,
    ) -> PasswordResetCode | None:
        result = await self.session.execute(
            select(PasswordResetCode)
            .where(
                PasswordResetCode.user_id == user_id,
                PasswordResetCode.used_at.is_(None),
            )
            .order_by(PasswordResetCode.created_at.desc())
            .limit(1)
        )
        return result.scalar_one_or_none()

    async def invalidate_unused_for_user(
        self,
        user_id: UUID,
        used_at: datetime,
    ) -> None:
        await self.session.execute(
            update(PasswordResetCode)
            .where(
                PasswordResetCode.user_id == user_id,
                PasswordResetCode.used_at.is_(None),
            )
            .values(used_at=used_at)
        )
