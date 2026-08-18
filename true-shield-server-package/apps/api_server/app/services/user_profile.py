from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user import User
from app.repositories.user import UserRepository
from app.schemas.user import UserUpdateRequest


class PhoneAlreadyUsedError(Exception):
    """手机号已被其他用户使用。"""


class UserProfileConflictError(Exception):
    """保存用户资料时发生唯一性冲突。"""


async def update_user_profile(
    session: AsyncSession,
    current_user: User,
    data: UserUpdateRequest,
) -> User:
    """更新当前用户可编辑的基础资料。"""

    repository = UserRepository(session)
    submitted_fields = data.model_fields_set

    if "phone" in submitted_fields:
        new_phone = data.phone

        if (
            new_phone is not None
            and new_phone != current_user.phone
        ):
            existing_user = await repository.get_by_phone(
                new_phone,
            )

            if (
                existing_user is not None
                and existing_user.id != current_user.id
            ):
                raise PhoneAlreadyUsedError

        current_user.phone = new_phone

    if "display_name" in submitted_fields:
        # schema 已确保提交该字段时不会为 None。
        current_user.display_name = str(
            data.display_name,
        )

    try:
        await session.commit()
        await session.refresh(current_user)
    except IntegrityError as error:
        await session.rollback()
        raise UserProfileConflictError from error

    return current_user
