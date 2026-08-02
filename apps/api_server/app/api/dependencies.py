from typing import Annotated
from uuid import UUID

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jwt.exceptions import InvalidTokenError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import decode_access_token
from app.db.session import get_db
from app.models.user import User
from app.repositories.user import UserRepository

oauth2_scheme = OAuth2PasswordBearer(tokenUrl=f"{settings.api_v1_prefix}/auth/login")


async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    session: Annotated[AsyncSession, Depends(get_db)],
) -> User:
    """根据 Bearer Token 获取当前登录用户。"""

    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="身份认证失败，请重新登录",
        headers={"WWW-Authenticate": "Bearer"},
    )

    try:
        payload = decode_access_token(token)

        if payload.get("type") != "access":
            raise credentials_exception

        subject = payload.get("sub")

        if not isinstance(subject, str):
            raise credentials_exception

        user_id = UUID(subject)

        token_auth_version = payload.get("ver", 1)

        if not isinstance(token_auth_version, int):
            raise credentials_exception
    except (InvalidTokenError, ValueError, TypeError) as error:
        raise credentials_exception from error

    repository = UserRepository(session)
    user = await repository.get_by_id(user_id)

    if user is None:
        raise credentials_exception

    if user.auth_version != token_auth_version:
        raise credentials_exception

    if user.status != "active":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="该用户当前不可用",
        )

    return user
