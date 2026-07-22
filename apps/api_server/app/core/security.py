from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import UUID, uuid4

import jwt
from pwdlib import PasswordHash

from app.core.config import settings

password_hash = PasswordHash.recommended()


def hash_password(password: str) -> str:
    """生成不可逆的密码哈希。"""

    return password_hash.hash(password)


def verify_password(password: str, hashed_password: str) -> bool:
    """校验用户输入的密码。"""

    return password_hash.verify(password, hashed_password)


def create_access_token(
    subject: UUID | str,
    expires_delta: timedelta | None = None,
) -> str:
    """创建 JWT 访问令牌。"""

    now = datetime.now(UTC)

    expire = now + (expires_delta or timedelta(minutes=settings.jwt_access_token_expire_minutes))

    payload = {
        "sub": str(subject),
        "type": "access",
        "iss": settings.app_name,
        "iat": now,
        "exp": expire,
        "jti": str(uuid4()),
    }

    return jwt.encode(
        payload,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )


def decode_access_token(token: str) -> dict[str, Any]:
    """验证并解析 JWT 访问令牌。"""

    return jwt.decode(
        token,
        settings.jwt_secret_key,
        algorithms=[settings.jwt_algorithm],
        issuer=settings.app_name,
        options={
            "require": [
                "sub",
                "type",
                "iss",
                "iat",
                "exp",
                "jti",
            ]
        },
    )
