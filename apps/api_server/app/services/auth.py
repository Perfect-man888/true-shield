from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import hash_password, verify_password
from app.models.user import User
from app.repositories.user import UserRepository
from app.schemas.auth import UserRegister


class EmailAlreadyRegisteredError(Exception):
    """邮箱已被注册。"""


class PhoneAlreadyRegisteredError(Exception):
    """手机号已被注册。"""


class RegistrationConflictError(Exception):
    """注册时发生唯一性冲突。"""


async def register_user(
    session: AsyncSession,
    data: UserRegister,
) -> User:
    """注册新用户。"""

    repository = UserRepository(session)

    email = str(data.email).lower()

    existing_email = await repository.get_by_email(email)

    if existing_email is not None:
        raise EmailAlreadyRegisteredError

    if data.phone is not None:
        existing_phone = await repository.get_by_phone(data.phone)

        if existing_phone is not None:
            raise PhoneAlreadyRegisteredError

    user = User(
        email=email,
        phone=data.phone,
        display_name=data.display_name,
        password_hash=hash_password(data.password),
        status="active",
    )

    try:
        await repository.add(user)
        await session.commit()
        await session.refresh(user)
    except IntegrityError as error:
        await session.rollback()
        raise RegistrationConflictError from error

    return user


async def authenticate_user(
    session: AsyncSession,
    email: str,
    password: str,
) -> User | None:
    """验证邮箱和密码。"""

    repository = UserRepository(session)

    user = await repository.get_by_email(email.strip().lower())

    if user is None:
        return None

    if not verify_password(password, user.password_hash):
        return None

    if user.status != "active":
        return None

    return user
