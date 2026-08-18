from __future__ import annotations

import uuid

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    Query,
    status,
)
from pydantic import ValidationError
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.db.session import get_db
from app.models.family import FamilyMember
from app.models.trusted_contact import TrustedContact
from app.models.user import User
from app.repositories.family import (
    get_active_family_membership,
)
from app.repositories.trusted_contact import (
    create_trusted_contact,
    disable_trusted_contact,
    get_trusted_contact_by_id,
    get_user_by_contact_email,
    get_user_by_contact_phone,
    list_trusted_contacts,
    update_trusted_contact,
)
from app.schemas.trusted_contact import (
    TrustedContactCreate,
    TrustedContactListResponse,
    TrustedContactResponse,
    TrustedContactUpdate,
)

router = APIRouter(
    prefix="/families",
    tags=["trusted-contacts"],
)


async def resolve_subject_user_id(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    requested_subject_user_id: uuid.UUID | None,
    current_user: User,
) -> tuple[FamilyMember, uuid.UUID]:
    """
    确认当前用户有权管理指定成员的可信联系人。

    普通成员只能管理自己的联系人；
    owner 和 admin 可以管理其他家庭成员。
    """

    current_membership = (
        await get_active_family_membership(
            db,
            family_id=family_id,
            user_id=current_user.id,
        )
    )

    if current_membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭不存在或无权访问。",
        )

    subject_user_id = (
        requested_subject_user_id
        or current_user.id
    )

    if (
        subject_user_id != current_user.id
        and current_membership.role
        not in {"owner", "admin"}
    ):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=(
                "普通家庭成员只能管理"
                "自己的可信联系人。"
            ),
        )

    subject_membership = (
        await get_active_family_membership(
            db,
            family_id=family_id,
            user_id=subject_user_id,
        )
    )

    if subject_membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="被保护用户不是该家庭的有效成员。",
        )

    return current_membership, subject_user_id


async def resolve_linked_user_id(
    db: AsyncSession,
    *,
    subject_user_id: uuid.UUID,
    email: str | None,
    phone: str | None,
) -> uuid.UUID | None:
    """根据邮箱或手机号匹配已注册用户。"""

    email_user = None
    phone_user = None

    if email is not None:
        email_user = await get_user_by_contact_email(
            db,
            email=email,
        )

    if phone is not None:
        phone_user = await get_user_by_contact_phone(
            db,
            phone=phone,
        )

    if (
        email_user is not None
        and phone_user is not None
        and email_user.id != phone_user.id
    ):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "联系人邮箱和手机号分别对应"
                "不同的注册用户。"
            ),
        )

    linked_user = email_user or phone_user

    if (
        linked_user is not None
        and linked_user.id == subject_user_id
    ):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="不能将本人设置为自己的可信联系人。",
        )

    if linked_user is None:
        return None

    return linked_user.id


def merged_value(
    payload: TrustedContactUpdate,
    *,
    field_name: str,
    current_value: object,
) -> object:
    """读取更新值，未提供时保留数据库原值。"""

    if field_name in payload.model_fields_set:
        return getattr(payload, field_name)

    return current_value


async def ensure_contact_permission(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    contact: TrustedContact,
    current_user: User,
) -> None:
    """检查当前用户是否有权修改指定联系人。"""

    await resolve_subject_user_id(
        db,
        family_id=family_id,
        requested_subject_user_id=(
            contact.subject_user_id
        ),
        current_user=current_user,
    )


@router.post(
    "/{family_id}/trusted-contacts",
    response_model=TrustedContactResponse,
    status_code=status.HTTP_201_CREATED,
    summary="创建可信联系人",
)
async def create_family_trusted_contact(
    family_id: uuid.UUID,
    payload: TrustedContactCreate,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> TrustedContactResponse:
    """为家庭成员创建可信联系人。"""

    _, subject_user_id = (
        await resolve_subject_user_id(
            db,
            family_id=family_id,
            requested_subject_user_id=(
                payload.subject_user_id
            ),
            current_user=current_user,
        )
    )

    email = (
        str(payload.email)
        if payload.email is not None
        else None
    )

    linked_user_id = await resolve_linked_user_id(
        db,
        subject_user_id=subject_user_id,
        email=email,
        phone=payload.phone,
    )

    try:
        contact = await create_trusted_contact(
            db,
            family_id=family_id,
            subject_user_id=subject_user_id,
            linked_user_id=linked_user_id,
            created_by_user_id=current_user.id,
            display_name=payload.display_name,
            relationship_label=(
                payload.relationship_label
            ),
            phone=payload.phone,
            email=email,
            preferred_channel=(
                payload.preferred_channel.value
            ),
            priority=payload.priority,
            can_receive_alerts=(
                payload.can_receive_alerts
            ),
        )
    except IntegrityError as exc:
        await db.rollback()

        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "相同手机号或邮箱的可信联系人"
                "已经存在。"
            ),
        ) from exc

    return TrustedContactResponse.model_validate(
        contact
    )


@router.get(
    "/{family_id}/trusted-contacts",
    response_model=TrustedContactListResponse,
    status_code=status.HTTP_200_OK,
    summary="查询可信联系人",
)
async def get_family_trusted_contacts(
    family_id: uuid.UUID,
    subject_user_id: uuid.UUID | None = Query(
        default=None,
        description=(
            "要查询的家庭成员 ID；"
            "不填写时查询当前用户"
        ),
    ),
    include_disabled: bool = Query(
        default=False,
        description="是否包含已停用联系人",
    ),
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> TrustedContactListResponse:
    """查询家庭成员的可信联系人。"""

    _, resolved_subject_user_id = (
        await resolve_subject_user_id(
            db,
            family_id=family_id,
            requested_subject_user_id=(
                subject_user_id
            ),
            current_user=current_user,
        )
    )

    contacts = await list_trusted_contacts(
        db,
        family_id=family_id,
        subject_user_id=(
            resolved_subject_user_id
        ),
        include_disabled=include_disabled,
    )

    return TrustedContactListResponse(
        family_id=family_id,
        subject_user_id=(
            resolved_subject_user_id
        ),
        items=[
            TrustedContactResponse.model_validate(
                contact
            )
            for contact in contacts
        ],
        total=len(contacts),
    )


@router.patch(
    "/{family_id}/trusted-contacts/{contact_id}",
    response_model=TrustedContactResponse,
    status_code=status.HTTP_200_OK,
    summary="修改可信联系人",
)
async def patch_family_trusted_contact(
    family_id: uuid.UUID,
    contact_id: uuid.UUID,
    payload: TrustedContactUpdate,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> TrustedContactResponse:
    """修改可信联系人信息。"""

    contact = await get_trusted_contact_by_id(
        db,
        family_id=family_id,
        contact_id=contact_id,
    )

    if contact is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="可信联系人不存在或无权访问。",
        )

    await ensure_contact_permission(
        db,
        family_id=family_id,
        contact=contact,
        current_user=current_user,
    )

    merged_data = {
        "display_name": merged_value(
            payload,
            field_name="display_name",
            current_value=contact.display_name,
        ),
        "relationship_label": merged_value(
            payload,
            field_name="relationship_label",
            current_value=(
                contact.relationship_label
            ),
        ),
        "phone": merged_value(
            payload,
            field_name="phone",
            current_value=contact.phone,
        ),
        "email": merged_value(
            payload,
            field_name="email",
            current_value=contact.email,
        ),
        "preferred_channel": merged_value(
            payload,
            field_name="preferred_channel",
            current_value=(
                contact.preferred_channel
            ),
        ),
        "priority": merged_value(
            payload,
            field_name="priority",
            current_value=contact.priority,
        ),
        "can_receive_alerts": merged_value(
            payload,
            field_name="can_receive_alerts",
            current_value=(
                contact.can_receive_alerts
            ),
        ),
    }

    try:
        validated = TrustedContactCreate(
            subject_user_id=(
                contact.subject_user_id
            ),
            **merged_data,
        )
    except ValidationError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail=exc.errors(
                include_url=False,
            ),
        ) from exc

    email = (
        str(validated.email)
        if validated.email is not None
        else None
    )

    linked_user_id = await resolve_linked_user_id(
        db,
        subject_user_id=contact.subject_user_id,
        email=email,
        phone=validated.phone,
    )

    try:
        contact = await update_trusted_contact(
            db,
            contact=contact,
            linked_user_id=linked_user_id,
            display_name=validated.display_name,
            relationship_label=(
                validated.relationship_label
            ),
            phone=validated.phone,
            email=email,
            preferred_channel=(
                validated.preferred_channel.value
            ),
            priority=validated.priority,
            can_receive_alerts=(
                validated.can_receive_alerts
            ),
        )
    except IntegrityError as exc:
        await db.rollback()

        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "相同手机号或邮箱的可信联系人"
                "已经存在。"
            ),
        ) from exc

    return TrustedContactResponse.model_validate(
        contact
    )


@router.post(
    (
        "/{family_id}/trusted-contacts/"
        "{contact_id}/disable"
    ),
    response_model=TrustedContactResponse,
    status_code=status.HTTP_200_OK,
    summary="停用可信联系人",
)
async def disable_family_trusted_contact(
    family_id: uuid.UUID,
    contact_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> TrustedContactResponse:
    """软停用可信联系人。"""

    contact = await get_trusted_contact_by_id(
        db,
        family_id=family_id,
        contact_id=contact_id,
    )

    if contact is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="可信联系人不存在或无权访问。",
        )

    await ensure_contact_permission(
        db,
        family_id=family_id,
        contact=contact,
        current_user=current_user,
    )

    if contact.status != "disabled":
        contact = await disable_trusted_contact(
            db,
            contact=contact,
        )

    return TrustedContactResponse.model_validate(
        contact
    )