from __future__ import annotations

import uuid

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    status,
)
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.db.session import get_db
from app.models.user import User
from app.repositories.family import (
    get_active_family_membership,
)
from app.repositories.family_alert import (
    acknowledge_family_alert,
    create_family_alert,
    get_existing_family_alert,
    get_family_alert_by_id,
    get_risk_event_for_family_alert,
    list_eligible_trusted_contacts,
    list_family_alerts,
    resolve_family_alert,
    save_family_alert_delivery_state,
)
from app.schemas.family_alert import (
    FamilyAlertDispatchRequest,
    FamilyAlertDispatchResponse,
    FamilyAlertListResponse,
    FamilyAlertResolveRequest,
    FamilyAlertResponse,
)
from app.services.family_alert_service import (
    apply_simulated_alert_delivery,
    build_alert_recipients,
    build_family_alert_summary,
    build_family_alert_title,
)

router = APIRouter(
    prefix="/families",
    tags=["family-alerts"],
)


async def require_family_membership(
    db: AsyncSession,
    *,
    family_id: uuid.UUID,
    user_id: uuid.UUID,
):
    """确认用户是家庭有效成员。"""

    membership = (
        await get_active_family_membership(
            db,
            family_id=family_id,
            user_id=user_id,
        )
    )

    if membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭不存在或无权访问。",
        )

    return membership


@router.post(
    (
        "/{family_id}/alerts/"
        "from-events/{event_id}"
    ),
    response_model=FamilyAlertResponse,
    status_code=status.HTTP_201_CREATED,
    summary="根据高风险事件生成家庭告警",
)
async def create_alert_from_risk_event(
    family_id: uuid.UUID,
    event_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertResponse:
    """根据高风险事件创建家庭告警。"""

    current_membership = (
        await require_family_membership(
            db,
            family_id=family_id,
            user_id=current_user.id,
        )
    )

    event = await get_risk_event_for_family_alert(
        db,
        event_id=event_id,
    )

    if event is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="风险事件不存在。",
        )

    subject_membership = (
        await get_active_family_membership(
            db,
            family_id=family_id,
            user_id=event.user_id,
        )
    )

    if subject_membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=(
                "风险事件所属用户"
                "不是该家庭的有效成员。"
            ),
        )

    if (
        event.user_id != current_user.id
        and current_membership.role
        not in {"owner", "admin"}
    ):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=(
                "普通家庭成员只能为自己的"
                "风险事件生成家庭告警。"
            ),
        )

    if event.risk_level != "high":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "只有 high 风险事件"
                "可以生成家庭告警。"
            ),
        )

    existing_alert = (
        await get_existing_family_alert(
            db,
            family_id=family_id,
            risk_event_id=event.id,
        )
    )

    if existing_alert is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "该风险事件已经生成过家庭告警。"
            ),
        )

    contacts = (
        await list_eligible_trusted_contacts(
            db,
            family_id=family_id,
            subject_user_id=event.user_id,
        )
    )

    recipient_data = build_alert_recipients(
        contacts
    )

    try:
        alert = await create_family_alert(
            db,
            family_id=family_id,
            risk_event_id=event.id,
            subject_user_id=event.user_id,
            risk_level=event.risk_level,
            title=build_family_alert_title(
                event
            ),
            summary=build_family_alert_summary(
                event
            ),
            recipients=recipient_data,
        )
    except IntegrityError as exc:
        await db.rollback()

        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "该风险事件已经生成过家庭告警。"
            ),
        ) from exc

    return FamilyAlertResponse.model_validate(
        alert
    )


@router.get(
    "/{family_id}/alerts",
    response_model=FamilyAlertListResponse,
    status_code=status.HTTP_200_OK,
    summary="查询家庭告警列表",
)
async def get_family_alert_list(
    family_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertListResponse:
    """查询当前家庭的风险告警。"""

    await require_family_membership(
        db,
        family_id=family_id,
        user_id=current_user.id,
    )

    alerts = await list_family_alerts(
        db,
        family_id=family_id,
    )

    return FamilyAlertListResponse(
        family_id=family_id,
        items=[
            FamilyAlertResponse.model_validate(
                alert
            )
            for alert in alerts
        ],
        total=len(alerts),
    )


@router.get(
    "/{family_id}/alerts/{alert_id}",
    response_model=FamilyAlertResponse,
    status_code=status.HTTP_200_OK,
    summary="查询家庭告警详情",
)
async def get_family_alert_detail(
    family_id: uuid.UUID,
    alert_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertResponse:
    """查询家庭告警及其接收人。"""

    await require_family_membership(
        db,
        family_id=family_id,
        user_id=current_user.id,
    )

    alert = await get_family_alert_by_id(
        db,
        family_id=family_id,
        alert_id=alert_id,
    )

    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭告警不存在。",
        )

    return FamilyAlertResponse.model_validate(
        alert
    )

@router.post(
    "/{family_id}/alerts/{alert_id}/acknowledge",
    response_model=FamilyAlertResponse,
    status_code=status.HTTP_200_OK,
    summary="确认家庭告警",
)
async def acknowledge_alert(
    family_id: uuid.UUID,
    alert_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertResponse:
    """家庭成员确认已经看到该风险告警。"""

    await require_family_membership(
        db,
        family_id=family_id,
        user_id=current_user.id,
    )

    alert = await get_family_alert_by_id(
        db,
        family_id=family_id,
        alert_id=alert_id,
    )

    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭告警不存在或无权访问。",
        )

    if alert.status == "resolved":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="该家庭告警已经处理完成。",
        )

    if alert.status == "cancelled":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="已取消的家庭告警不能确认。",
        )

    # 重复确认时直接返回原记录，保证接口幂等。
    if alert.status == "acknowledged":
        return FamilyAlertResponse.model_validate(
            alert
        )

    alert = await acknowledge_family_alert(
        db,
        alert=alert,
        user_id=current_user.id,
    )

    return FamilyAlertResponse.model_validate(
        alert
    )


@router.post(
    "/{family_id}/alerts/{alert_id}/resolve",
    response_model=FamilyAlertResponse,
    status_code=status.HTTP_200_OK,
    summary="完成家庭告警处理",
)
async def resolve_alert(
    family_id: uuid.UUID,
    alert_id: uuid.UUID,
    payload: FamilyAlertResolveRequest,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertResponse:
    """
    将家庭风险告警标记为处理完成。

    被保护用户本人、家庭 owner 或 admin 可以完成处理。
    """

    membership = await require_family_membership(
        db,
        family_id=family_id,
        user_id=current_user.id,
    )

    alert = await get_family_alert_by_id(
        db,
        family_id=family_id,
        alert_id=alert_id,
    )

    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭告警不存在或无权访问。",
        )

    if (
        current_user.id != alert.subject_user_id
        and membership.role not in {"owner", "admin"}
    ):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=(
                "只有被保护用户本人、家庭所有者"
                "或管理员可以完成告警处理。"
            ),
        )

    if alert.status == "cancelled":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="已取消的家庭告警不能标记为已处理。",
        )

    # 重复请求直接返回原结果，防止网络重试产生错误。
    if alert.status == "resolved":
        return FamilyAlertResponse.model_validate(
            alert
        )

    alert = await resolve_family_alert(
        db,
        alert=alert,
        user_id=current_user.id,
        resolution_note=payload.resolution_note,
    )

    return FamilyAlertResponse.model_validate(
        alert
    )

@router.post(
    "/{family_id}/alerts/{alert_id}/dispatch",
    response_model=FamilyAlertDispatchResponse,
    status_code=status.HTTP_200_OK,
    summary="模拟发送家庭告警",
)
async def dispatch_family_alert(
    family_id: uuid.UUID,
    alert_id: uuid.UUID,
    payload: FamilyAlertDispatchRequest,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertDispatchResponse:
    """
    模拟向家庭告警接收人发送通知。

    被保护用户本人、家庭 owner 或 admin
    可以执行告警发送。
    """

    membership = await require_family_membership(
        db,
        family_id=family_id,
        user_id=current_user.id,
    )

    alert = await get_family_alert_by_id(
        db,
        family_id=family_id,
        alert_id=alert_id,
    )

    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭告警不存在或无权访问。",
        )

    if (
        current_user.id != alert.subject_user_id
        and membership.role not in {
            "owner",
            "admin",
        }
    ):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=(
                "只有被保护用户本人、家庭所有者"
                "或管理员可以发送家庭告警。"
            ),
        )

    if alert.status == "resolved":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "已经处理完成的家庭告警"
                "不能继续发送。"
            ),
        )

    if alert.status == "cancelled":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "已取消的家庭告警不能发送。"
            ),
        )

    alert_recipient_ids = {
        recipient.id
        for recipient in alert.recipients
    }

    requested_failure_ids = set(
        payload.simulated_failure_recipient_ids
    )

    unknown_recipient_ids = (
        requested_failure_ids
        - alert_recipient_ids
    )

    if unknown_recipient_ids:
        raise HTTPException(
            status_code=(
                status.HTTP_422_UNPROCESSABLE_CONTENT
            ),
            detail=(
                "模拟失败接收人不属于该家庭告警。"
            ),
        )

    dispatch_summary = (
        apply_simulated_alert_delivery(
            alert.recipients,
            simulated_failure_recipient_ids=(
                requested_failure_ids
            ),
        )
    )

    updated_alert = (
        await save_family_alert_delivery_state(
            db,
            alert=alert,
        )
    )

    return FamilyAlertDispatchResponse(
        family_id=family_id,
        alert_id=alert_id,
        attempted_count=(
            dispatch_summary.attempted_count
        ),
        sent_count=dispatch_summary.sent_count,
        failed_count=(
            dispatch_summary.failed_count
        ),
        skipped_count=(
            dispatch_summary.skipped_count
        ),
        already_completed_count=(
            dispatch_summary
            .already_completed_count
        ),
        alert=FamilyAlertResponse.model_validate(
            updated_alert
        ),
    )