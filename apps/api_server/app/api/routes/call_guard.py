from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.dependencies import get_current_user
from app.db.session import get_db
from app.models.user import User
from app.repositories.family import get_active_family_membership
from app.repositories.family_alert import (
    create_family_alert,
    list_eligible_trusted_contacts,
    record_family_alert_delivery_attempts,
    save_family_alert_delivery_state,
)
from app.repositories.risk import create_risk_event
from app.schemas.call_guard import (
    CallGuardHelpRequest,
    CallGuardHelpResponse,
    CallNumberAnalyzeRequest,
    CallNumberAnalyzeResponse,
)
from app.schemas.risk import (
    RiskEvidence,
    RiskLevel,
    TextRiskAnalysisRequest,
    TextRiskAnalysisResponse,
)
from app.services.call_guard_service import CallGuardService
from app.services.family_alert_service import (
    build_alert_recipients,
    deliver_family_alert_notifications,
)
from app.services.push_notification import send_family_alert_push_safely

router = APIRouter(
    prefix="/call-guard",
    tags=["call-guard"],
)

call_guard_service = CallGuardService()


@router.post(
    "/analyze-number",
    response_model=CallNumberAnalyzeResponse,
    status_code=status.HTTP_200_OK,
    summary="筛查当前来电或去电号码风险",
)
async def analyze_call_number(
    payload: CallNumberAnalyzeRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> CallNumberAnalyzeResponse:
    """仅分析当前号码，不保存完整通话记录。"""

    return await call_guard_service.analyze(
        db,
        user_id=current_user.id,
        request=payload,
    )


@router.post(
    "/help",
    response_model=CallGuardHelpResponse,
    status_code=status.HTTP_201_CREATED,
    summary="通话中一键创建家庭求助告警",
)
async def create_call_guard_help(
    payload: CallGuardHelpRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> CallGuardHelpResponse:
    """用户主动求助时才保存必要风险证据并通知家庭。"""

    membership = await get_active_family_membership(
        db,
        family_id=payload.family_id,
        user_id=current_user.id,
    )
    if membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭不存在或无权访问。",
        )

    normalized_number = call_guard_service.normalize_number(
        payload.phone_number
    )
    masked_number = call_guard_service.mask_number(
        normalized_number
    )

    event_text = (
        "通话安全护航一键求助："
        f"当前{payload.direction.value}号码为 {masked_number}。"
        f"{payload.summary}"
    )

    analysis = TextRiskAnalysisResponse(
        risk_level=RiskLevel.HIGH,
        score=max(85, payload.score),
        evidence=[
            RiskEvidence(
                rule_id="CALL-GUARD-HELP",
                category="call_guard",
                title="用户在通话中主动发起家庭求助",
                matched_terms=[masked_number],
                score=100,
                explanation=(
                    "用户认为当前通话可能存在诈骗风险，"
                    "已主动请求家庭成员协助核实。"
                ),
                tags=["call", "family_help", "user_initiated"],
                matches=[],
            )
        ],
        actions=[
            "立即暂停转账、屏幕共享和验证码提供。",
            "家庭成员通过可信渠道联系本人核实情况。",
            "必要时挂断电话并联系警方或银行官方客服。",
        ],
        disclaimer=(
            "该告警由用户主动发起，只保存当前号码的脱敏信息和必要风险证据，"
            "不保存通话录音或完整通话记录。"
        ),
        rule_version="call-guard-1.0.0",
    )

    event = await create_risk_event(
        db,
        user_id=current_user.id,
        request=TextRiskAnalysisRequest(text=event_text),
        analysis=analysis,
        source_type="call",
        source_text=event_text,
        source_metadata={
            "call_guard": {
                "phone_number_masked": masked_number,
                "direction": payload.direction.value,
                "verification_status": payload.verification_status.value,
                "user_initiated_help": True,
                "raw_audio_stored": False,
                "call_log_stored": False,
            }
        },
    )

    contacts = await list_eligible_trusted_contacts(
        db,
        family_id=payload.family_id,
        subject_user_id=current_user.id,
    )
    recipient_data = build_alert_recipients(contacts)

    alert = await create_family_alert(
        db,
        family_id=payload.family_id,
        risk_event_id=event.id,
        subject_user_id=current_user.id,
        risk_level="high",
        title="通话中一键家庭求助",
        summary=(
            f"{current_user.display_name}正在与{masked_number}通话，"
            "并主动请求家庭成员协助核实，请尽快联系本人。"
        ),
        recipients=recipient_data,
    )

    dispatch_summary = await deliver_family_alert_notifications(
        alert.recipients,
        title=alert.title,
        message=alert.summary,
    )

    await record_family_alert_delivery_attempts(
        db,
        recipients=alert.recipients,
        attempted_recipient_ids=set(
            dispatch_summary.attempted_recipient_ids
        ),
    )
    alert = await save_family_alert_delivery_state(
        db,
        alert=alert,
    )

    await send_family_alert_push_safely(
        db,
        alert=alert,
        event_type="created",
        actor_display_name=current_user.display_name,
    )

    return CallGuardHelpResponse(
        family_id=payload.family_id,
        event_id=event.id,
        alert_id=alert.id,
        recipient_count=len(alert.recipients),
        sent_count=dispatch_summary.sent_count,
        failed_count=dispatch_summary.failed_count,
        skipped_count=dispatch_summary.skipped_count,
        message="家庭求助已创建并通知家庭成员。",
    )
