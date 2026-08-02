from typing import Annotated
from urllib.parse import unquote

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.db.session import get_db
from app.models.user import User
from app.repositories.push_device import (
    revoke_push_device,
    upsert_push_device,
)
from app.schemas.push_device import (
    PushDeviceRegisterRequest,
    PushDeviceResponse,
)
from app.schemas.user import (
    PasswordChangeRequest,
    UserResponse,
    UserUpdateRequest,
)
from app.services.user_profile import (
    PhoneAlreadyUsedError,
    UserProfileConflictError,
    update_user_profile,
)
from app.services.user_security import (
    CurrentPasswordIncorrectError,
    change_user_password,
)

router = APIRouter(prefix="/users")


@router.get(
    "/me",
    response_model=UserResponse,
    summary="获取当前登录用户",
)
async def get_me(
    current_user: Annotated[User, Depends(get_current_user)],
) -> UserResponse:
    return UserResponse.model_validate(current_user)


@router.patch(
    "/me",
    response_model=UserResponse,
    summary="修改当前登录用户资料",
)
async def update_me(
    data: UserUpdateRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[AsyncSession, Depends(get_db)],
) -> UserResponse:
    try:
        updated_user = await update_user_profile(
            session=session,
            current_user=current_user,
            data=data,
        )
    except PhoneAlreadyUsedError as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="该手机号已被其他账户使用",
        ) from error
    except UserProfileConflictError as error:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="用户资料保存冲突，请检查手机号",
        ) from error

    return UserResponse.model_validate(updated_user)


@router.patch(
    "/me/password",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="修改当前登录用户密码",
)
async def change_my_password(
    data: PasswordChangeRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[AsyncSession, Depends(get_db)],
) -> Response:
    try:
        await change_user_password(
            session=session,
            current_user=current_user,
            data=data,
        )
    except CurrentPasswordIncorrectError as error:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="当前密码不正确",
        ) from error

    return Response(
        status_code=status.HTTP_204_NO_CONTENT,
    )

@router.post(
    "/me/push-devices",
    response_model=PushDeviceResponse,
    status_code=status.HTTP_200_OK,
    summary="注册或刷新当前推送设备",
)
async def register_my_push_device(
    data: PushDeviceRegisterRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[AsyncSession, Depends(get_db)],
) -> PushDeviceResponse:
    device = await upsert_push_device(
        session,
        user_id=current_user.id,
        installation_id=data.installation_id,
        platform=data.platform,
        device_name=data.device_name,
        app_version=data.app_version,
    )

    return PushDeviceResponse.model_validate(device)


@router.delete(
    "/me/push-devices/{installation_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="停用当前推送设备",
)
async def unregister_my_push_device(
    installation_id: str,
    current_user: Annotated[User, Depends(get_current_user)],
    session: Annotated[AsyncSession, Depends(get_db)],
) -> Response:
    normalized_installation_id = unquote(
        installation_id
    ).strip()

    if normalized_installation_id:
        await revoke_push_device(
            session,
            user_id=current_user.id,
            installation_id=normalized_installation_id,
        )

    # 幂等删除：设备不存在时也返回 204。
    return Response(
        status_code=status.HTTP_204_NO_CONTENT,
    )

