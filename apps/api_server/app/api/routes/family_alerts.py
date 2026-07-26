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
    list_family_alert_delivery_attempts,
    list_family_alerts,
    record_family_alert_delivery_attempts,
    resolve_family_alert,
    save_family_alert_delivery_state,
)
from app.repositories.family_alert_policy import (
    get_or_create_family_alert_policy,
    update_family_alert_policy,
)
from app.schemas.family_alert import (
    FamilyAlertDeliveryAttemptListResponse,
    FamilyAlertDispatchRequest,
    FamilyAlertDispatchResponse,
    FamilyAlertListResponse,
    FamilyAlertResolveRequest,
    FamilyAlertResponse,
)
from app.schemas.family_alert_policy import (
    FamilyAlertPolicyResponse,
    FamilyAlertPolicyUpdate,
)
from app.services.family_alert_service import (
    build_alert_recipients,
    build_family_alert_summary,
    build_family_alert_title,
    deliver_family_alert_notifications,
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
        await deliver_family_alert_notifications(
            alert.recipients,
            title=alert.title,
            message=alert.summary,
            simulated_failure_recipient_ids=(
                requested_failure_ids
            ),
        )
    )

    await record_family_alert_delivery_attempts(
        db,
        recipients=alert.recipients,
        attempted_recipient_ids=set(
            dispatch_summary
            .attempted_recipient_ids
        ),
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

@router.get(
    "/{family_id}/alert-policy",
    response_model=FamilyAlertPolicyResponse,
    status_code=status.HTTP_200_OK,
    summary="查询家庭告警自动触发策略",
)
async def get_alert_policy(
    family_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertPolicyResponse:
    """家庭成员查询家庭告警策略。"""

    await require_family_membership(
        db,
        family_id=family_id,
        user_id=current_user.id,
    )

    policy = (
        await get_or_create_family_alert_policy(
            db,
            family_id=family_id,
        )
    )

    return (
        FamilyAlertPolicyResponse.model_validate(
            policy
        )
    )


@router.patch(
    "/{family_id}/alert-policy",
    response_model=FamilyAlertPolicyResponse,
    status_code=status.HTTP_200_OK,
    summary="更新家庭告警自动触发策略",
)
async def patch_alert_policy(
    family_id: uuid.UUID,
    payload: FamilyAlertPolicyUpdate,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertPolicyResponse:
    """家庭所有者或管理员更新告警策略。"""

    membership = await require_family_membership(
        db,
        family_id=family_id,
        user_id=current_user.id,
    )

    if membership.role not in {
        "owner",
        "admin",
    }:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=(
                "只有家庭所有者或管理员"
                "可以修改告警策略。"
            ),
        )

    policy = (
        await get_or_create_family_alert_policy(
            db,
            family_id=family_id,
        )
    )

    update_data = payload.model_dump(
        exclude_unset=True,
        mode="json",
    )

    if not update_data:
        return (
            FamilyAlertPolicyResponse
            .model_validate(policy)
        )

    policy = await update_family_alert_policy(
        db,
        policy=policy,
        update_data=update_data,
    )

    return (
        FamilyAlertPolicyResponse.model_validate(
            policy
        )
    )

@router.get(
    (
        "/{family_id}/alerts/{alert_id}"
        "/delivery-attempts"
    ),
    response_model=(
        FamilyAlertDeliveryAttemptListResponse
    ),
    summary="查询家庭告警发送记录",
)
async def get_alert_delivery_attempts(
    family_id: uuid.UUID,
    alert_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertDeliveryAttemptListResponse:
    """查询指定家庭告警的全部发送尝试记录。"""

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

    attempts = (
        await list_family_alert_delivery_attempts(
            db,
            alert_id=alert.id,
        )
    )

    return (
        FamilyAlertDeliveryAttemptListResponse(
            family_id=family_id,
            alert_id=alert.id,
            items=attempts,
            total=len(attempts),
        )
    )

@router.post(
    (
        "/{family_id}/alerts/{alert_id}"
        "/retry-failed"
    ),
    response_model=FamilyAlertDispatchResponse,
    summary="重试发送失败的家庭告警通知",
)
async def retry_failed_alert_deliveries(
    family_id: uuid.UUID,
    alert_id: uuid.UUID,
    current_user: User = Depends(
        get_current_user
    ),
    db: AsyncSession = Depends(get_db),
) -> FamilyAlertDispatchResponse:
    """仅重新发送当前状态为 failed 的接收人。"""

    current_membership = (
        await require_family_membership(
            db,
            family_id=family_id,
            user_id=current_user.id,
        )
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
        alert.subject_user_id != current_user.id
        and current_membership.role
        not in {"owner", "admin"}
    ):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=(
                "只有被保护用户本人、家庭所有者"
                "或管理员可以重试家庭告警。"
            ),
        )

    if alert.status == "resolved":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="已经处理完成的家庭告警不能重试。",
        )

    if alert.status == "cancelled":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="已经取消的家庭告警不能重试。",
        )

    failed_recipients = [
        recipient
        for recipient in alert.recipients
        if recipient.delivery_status == "failed"
    ]

    if not failed_recipients:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="当前没有需要重试的失败接收人。",
        )

    dispatch_summary = (
        await deliver_family_alert_notifications(
            failed_recipients,
            title=alert.title,
            message=alert.summary,
            simulated_failure_recipient_ids=set(),
        )
    )

    await record_family_alert_delivery_attempts(
        db,
        recipients=failed_recipients,
        attempted_recipient_ids=set(
            dispatch_summary.attempted_recipient_ids
        ),
    )

    updated_alert = (
        await save_family_alert_delivery_state(
            db,
            alert=alert,
        )
    )

    return FamilyAlertDispatchResponse(
        family_id=family_id,
        alert_id=alert.id,
        attempted_count=(
            dispatch_summary.attempted_count
        ),
        sent_count=dispatch_summary.sent_count,
        failed_count=dispatch_summary.failed_count,
        skipped_count=(
            dispatch_summary.skipped_count
        ),
        already_completed_count=(
            dispatch_summary
            .already_completed_count
        ),
        alert=updated_alert,
    )