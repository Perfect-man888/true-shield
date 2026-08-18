from __future__ import annotations

import uuid
from datetime import (
    UTC,
    datetime,
    timedelta,
)

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
from app.repositories.family_invitation import (
    accept_family_invitation,
    create_family_invitation,
    decline_family_invitation,
    expire_family_invitation,
    get_active_member,
    get_active_pending_invitation,
    get_invitation_for_recipient,
    get_user_by_email,
    list_received_pending_invitations,
)
from app.schemas.family import (
    FamilyCreate,
    FamilyInvitationAcceptResponse,
    FamilyInvitationCreate,
    FamilyInvitationListItem,
    FamilyInvitationListResponse,
    FamilyInvitationResponse,
    FamilyInvitationStatus,
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

FAMILY_INVITATION_VALID_DAYS = 7


def invitation_to_list_item(
    invitation,
    family: Family,
    inviter: User,
) -> FamilyInvitationListItem:
    """将家庭邀请转换为列表响应。"""

    return FamilyInvitationListItem(
        id=invitation.id,
        family_id=invitation.family_id,
        family_name=family.name,
        inviter_user_id=invitation.inviter_user_id,
        inviter_display_name=inviter.display_name,
        invitee_email=invitation.invitee_email,
        role=FamilyRole(invitation.role),
        status=FamilyInvitationStatus(
            invitation.status
        ),
        expires_at=invitation.expires_at,
        created_at=invitation.created_at,
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

@router.post(
    "/{family_id}/invitations",
    response_model=FamilyInvitationResponse,
    status_code=status.HTTP_201_CREATED,
    summary="发送家庭邀请",
)
async def invite_family_member(
    family_id: uuid.UUID,
    payload: FamilyInvitationCreate,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyInvitationResponse:
    """家庭所有者或管理员邀请新成员。"""

    inviter_membership = (
        await get_active_family_membership(
            db,
            family_id=family_id,
            user_id=current_user.id,
        )
    )

    if inviter_membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭不存在或无权访问。",
        )

    if inviter_membership.role not in {
        "owner",
        "admin",
    }:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="只有家庭所有者或管理员可以发送邀请。",
        )

    invitee_email = str(
        payload.invitee_email
    ).lower()

    if invitee_email == current_user.email.lower():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="不能邀请自己加入家庭。",
        )

    invitee_user = await get_user_by_email(
        db,
        email=invitee_email,
    )

    if invitee_user is not None:
        existing_member = await get_active_member(
            db,
            family_id=family_id,
            user_id=invitee_user.id,
        )

        if existing_member is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="该用户已经是家庭成员。",
            )

    now = datetime.now(UTC)

    pending_invitation = (
        await get_active_pending_invitation(
            db,
            family_id=family_id,
            invitee_email=invitee_email,
            now=now,
        )
    )

    if pending_invitation is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="该用户已经存在待处理邀请。",
        )

    invitation = await create_family_invitation(
        db,
        family_id=family_id,
        inviter_user_id=current_user.id,
        invitee_email=invitee_email,
        role=payload.role.value,
        expires_at=(
            now
            + timedelta(
                days=FAMILY_INVITATION_VALID_DAYS
            )
        ),
    )

    return FamilyInvitationResponse.model_validate(
        invitation
    )


@router.get(
    "/invitations/received",
    response_model=FamilyInvitationListResponse,
    status_code=status.HTTP_200_OK,
    summary="查询我收到的家庭邀请",
)
async def get_received_family_invitations(
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyInvitationListResponse:
    """查询当前用户邮箱收到的有效邀请。"""

    now = datetime.now(UTC)

    rows = await list_received_pending_invitations(
        db,
        invitee_email=current_user.email.lower(),
        now=now,
    )

    items = [
        invitation_to_list_item(
            invitation,
            family,
            inviter,
        )
        for invitation, family, inviter in rows
    ]

    return FamilyInvitationListResponse(
        items=items,
        total=len(items),
    )


@router.post(
    "/invitations/{invitation_id}/accept",
    response_model=FamilyInvitationAcceptResponse,
    status_code=status.HTTP_200_OK,
    summary="接受家庭邀请",
)
async def accept_received_family_invitation(
    invitation_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyInvitationAcceptResponse:
    """接受属于当前用户邮箱的家庭邀请。"""

    invitation_row = (
        await get_invitation_for_recipient(
            db,
            invitation_id=invitation_id,
            invitee_email=current_user.email.lower(),
        )
    )

    if invitation_row is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="邀请不存在或无权访问。",
        )

    invitation, family = invitation_row

    if invitation.status != "pending":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="该邀请已处理，不能重复操作。",
        )

    now = datetime.now(UTC)

    if invitation.expires_at <= now:
        await expire_family_invitation(
            db,
            invitation=invitation,
            responded_at=now,
        )

        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail="该邀请已过期。",
        )

    existing_member = await get_active_member(
        db,
        family_id=invitation.family_id,
        user_id=current_user.id,
    )

    if existing_member is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="你已经是该家庭成员。",
        )

    member = await accept_family_invitation(
        db,
        invitation=invitation,
        user_id=current_user.id,
        responded_at=now,
    )

    return FamilyInvitationAcceptResponse(
        invitation_id=invitation.id,
        family_id=family.id,
        family_name=family.name,
        role=FamilyRole(member.role),
        status=FamilyInvitationStatus.ACCEPTED,
        joined_at=member.joined_at,
    )


@router.post(
    "/invitations/{invitation_id}/decline",
    response_model=FamilyInvitationResponse,
    status_code=status.HTTP_200_OK,
    summary="拒绝家庭邀请",
)
async def decline_received_family_invitation(
    invitation_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyInvitationResponse:
    """拒绝属于当前用户邮箱的家庭邀请。"""

    invitation_row = (
        await get_invitation_for_recipient(
            db,
            invitation_id=invitation_id,
            invitee_email=current_user.email.lower(),
        )
    )

    if invitation_row is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="邀请不存在或无权访问。",
        )

    invitation, _ = invitation_row

    if invitation.status != "pending":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="该邀请已处理，不能重复操作。",
        )

    now = datetime.now(UTC)

    if invitation.expires_at <= now:
        await expire_family_invitation(
            db,
            invitation=invitation,
            responded_at=now,
        )

        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail="该邀请已过期。",
        )

    invitation = await decline_family_invitation(
        db,
        invitation=invitation,
        responded_at=now,
    )

    return FamilyInvitationResponse.model_validate(
        invitation
    )