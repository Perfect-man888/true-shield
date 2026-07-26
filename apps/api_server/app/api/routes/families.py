from __future__ import annotations

import uuid

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    status,
)
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.db.session import get_db
from app.models.family import (
    Family,
    FamilyMember,
)
from app.models.user import User
from app.repositories.family import (
    create_family_with_owner,
    get_active_family_membership,
    list_active_family_members,
    list_user_families,
)
from app.schemas.family import (
    FamilyCreate,
    FamilyMemberListItem,
    FamilyMemberListResponse,
    FamilyMemberStatus,
    FamilyRole,
    FamilyStatus,
    MyFamilyItem,
    MyFamilyListResponse,
)

router = APIRouter(
    prefix="/families",
    tags=["families"],
)


def family_to_my_item(
    family: Family,
    membership: FamilyMember,
) -> MyFamilyItem:
    """将家庭及当前用户成员身份转换为响应。"""

    return MyFamilyItem(
        id=family.id,
        name=family.name,
        owner_user_id=family.owner_user_id,
        status=FamilyStatus(family.status),
        my_role=FamilyRole(membership.role),
        created_at=family.created_at,
        updated_at=family.updated_at,
    )


def member_to_list_item(
    member: FamilyMember,
    user: User,
) -> FamilyMemberListItem:
    """将成员和用户信息转换为列表项。"""

    return FamilyMemberListItem(
        id=member.id,
        family_id=member.family_id,
        user_id=member.user_id,
        display_name=user.display_name,
        email=user.email,
        phone=user.phone,
        role=FamilyRole(member.role),
        status=FamilyMemberStatus(
            member.status
        ),
        joined_at=member.joined_at,
    )


@router.post(
    "",
    response_model=MyFamilyItem,
    status_code=status.HTTP_201_CREATED,
    summary="创建家庭组",
    description=(
        "创建一个新的家庭组，并自动将当前用户"
        "设置为家庭所有者。"
    ),
)
async def create_family(
    payload: FamilyCreate,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> MyFamilyItem:
    """创建家庭组。"""

    family, owner_membership = (
        await create_family_with_owner(
            db,
            name=payload.name,
            owner_user_id=current_user.id,
        )
    )

    return family_to_my_item(
        family,
        owner_membership,
    )


@router.get(
    "",
    response_model=MyFamilyListResponse,
    status_code=status.HTTP_200_OK,
    summary="查询我的家庭组",
    description=(
        "查询当前用户已加入且仍然有效的家庭组。"
    ),
)
async def get_my_families(
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> MyFamilyListResponse:
    """查询当前用户加入的家庭。"""

    rows = await list_user_families(
        db,
        user_id=current_user.id,
    )

    items = [
        family_to_my_item(
            family,
            membership,
        )
        for family, membership in rows
    ]

    return MyFamilyListResponse(
        items=items,
        total=len(items),
    )


@router.get(
    "/{family_id}/members",
    response_model=FamilyMemberListResponse,
    status_code=status.HTTP_200_OK,
    summary="查询家庭成员",
    description=(
        "只有该家庭组的有效成员可以查看"
        "家庭成员列表。"
    ),
)
async def get_family_members(
    family_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyMemberListResponse:
    """查询指定家庭组的成员。"""

    membership = (
        await get_active_family_membership(
            db,
            family_id=family_id,
            user_id=current_user.id,
        )
    )

    if membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭不存在或无权访问。",
        )

    rows = await list_active_family_members(
        db,
        family_id=family_id,
    )

    items = [
        member_to_list_item(
            member,
            user,
        )
        for member, user in rows
    ]

    return FamilyMemberListResponse(
        family_id=family_id,
        items=items,
        total=len(items),
    )