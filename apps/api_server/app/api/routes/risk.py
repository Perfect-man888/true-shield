from __future__ import annotations

import uuid

from fastapi import (
    APIRouter,
    Depends,
    File,
    HTTPException,
    Query,
    UploadFile,
    status,
)
from sqlalchemy.ext.asyncio import AsyncSession
from starlette.concurrency import run_in_threadpool

from app.api.dependencies import get_current_user
from app.db.session import get_db
from app.models.risk import RiskEvent
from app.models.user import User
from app.repositories.risk import (
    create_risk_event,
    get_risk_event_by_id,
    list_risk_events,
)
from app.schemas.risk import (
    ImageRiskAnalysisResponse,
    OCRQualityResponse,
    OCRTextLineResponse,
    PersistedTextRiskAnalysisResponse,
    RiskEventDetailResponse,
    RiskEventListItem,
    RiskEventListResponse,
    RiskEvidence,
    RiskLevel,
    TextRiskAnalysisRequest,
)
from app.schemas.risk_feedback import (
    RiskFeedbackCreate,
    RiskFeedbackResponse,
)
from app.schemas.risk_feedback_statistics import (
    RiskFeedbackStatisticsResponse,
)
from app.services.ocr_service import (
    OCRImageTooLargeError,
    OCRInvalidImageError,
    OCRNoTextError,
    OCRService,
    OCRServiceError,
    evaluate_ocr_quality,
)
from app.services.risk_analyzer import TextRiskAnalyzer
from app.services.risk_feedback_service import (
    get_owned_risk_event,
    get_risk_feedback,
    upsert_risk_feedback,
)
from app.services.risk_feedback_statistics_service import (
    RiskFeedbackStatisticsService,
)

router = APIRouter(
    prefix="/risk",
)

text_risk_analyzer = TextRiskAnalyzer()
ocr_service = OCRService()

risk_feedback_statistics_service = (
    RiskFeedbackStatisticsService()
)


def event_to_list_item(
    event: RiskEvent,
) -> RiskEventListItem:
    """将数据库事件转换为风险历史列表项。"""

    return RiskEventListItem(
        event_id=event.id,
        source_type=event.source_type,
        risk_level=RiskLevel(event.risk_level),
        score=event.risk_score,
        summary=event.summary,
        evidence_count=len(event.signals),
        created_at=event.created_at,
    )


def event_to_detail(
    event: RiskEvent,
) -> RiskEventDetailResponse:
    """将数据库事件转换为风险事件详情。"""

    sorted_signals = sorted(
        event.signals,
        key=lambda signal: signal.signal_weight,
        reverse=True,
    )

    evidence = [
        RiskEvidence(
            rule_id=signal.rule_id,
            category=signal.signal_type,
            title=signal.title,
            matched_terms=signal.matched_terms,
            tags=signal.tags,
            matches=signal.match_positions,
            score=signal.signal_weight,
            explanation=signal.explanation,
        )
        for signal in sorted_signals
    ]

    return RiskEventDetailResponse(
        event_id=event.id,
        created_at=event.created_at,
        source_type=event.source_type,
        source_text=event.source_text,
        summary=event.summary,
        risk_level=RiskLevel(event.risk_level),
        score=event.risk_score,
        evidence=evidence,
        actions=event.actions,
        disclaimer=event.disclaimer,
        rule_version=event.rule_version,
    )


@router.post(
    "/text/analyze",
    response_model=PersistedTextRiskAnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="分析并保存可疑聊天文本",
    description=(
        "根据 YAML 风险规则分析聊天文本，"
        "返回风险等级、风险分数、命中证据和行动建议，"
        "同时将本次风险分析保存到数据库。"
    ),
)
async def analyze_text_risk(
    request: TextRiskAnalysisRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> PersistedTextRiskAnalysisResponse:
    """分析文本并保存风险事件。"""

    analysis = text_risk_analyzer.analyze(request)

    event = await create_risk_event(
        db,
        user_id=current_user.id,
        request=request,
        analysis=analysis,
        source_type="text",
        source_text=request.text,
    )

    return PersistedTextRiskAnalysisResponse(
        **analysis.model_dump(),
        event_id=event.id,
        created_at=event.created_at,
    )


@router.post(
    "/image/analyze",
    response_model=ImageRiskAnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="分析聊天截图中的风险",
    description=(
        "上传聊天截图，通过 OCR 提取图片中的文字，"
        "再执行反诈风险分析，并保存风险事件。"
    ),
)
async def analyze_image_risk(
    image: UploadFile = File(
        ...,
        description="聊天截图文件",
    ),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> ImageRiskAnalysisResponse:
    """识别聊天截图并执行风险分析。"""

    allowed_content_types = {
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/bmp",
    }

    if image.content_type not in allowed_content_types:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="仅支持 JPEG、PNG、WEBP 和 BMP 图片。",
        )

    try:
        image_bytes = await image.read()
    finally:
        await image.close()

    try:
        ocr_result = await run_in_threadpool(
            ocr_service.extract_text,
            image_bytes,
        )

    except OCRImageTooLargeError as exc:
        raise HTTPException(
            status_code=413,
            detail=str(exc),
        ) from exc

    except OCRInvalidImageError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    except OCRNoTextError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        ) from exc

    except OCRServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="OCR 服务暂时无法完成识别。",
        ) from exc

    # 注意：必须放在全部 except 结束之后。
    # 运行到这里，说明 ocr_result 已经成功生成。
    ocr_quality = evaluate_ocr_quality(
        ocr_result
    )

    text_request = TextRiskAnalysisRequest(
        text=ocr_result.text,
    )

    analysis = text_risk_analyzer.analyze(
        text_request
    )

    event = await create_risk_event(
        db,
        user_id=current_user.id,
        request=text_request,
        analysis=analysis,
        source_type="image",
        source_text=ocr_result.text,
    )

    return ImageRiskAnalysisResponse(
        **analysis.model_dump(),
        source_type="image",
        extracted_text=ocr_result.text,
        image_width=ocr_result.width,
        image_height=ocr_result.height,
        ocr_lines=[
            OCRTextLineResponse(
                text=line.text,
                confidence=line.confidence,
                box=list(line.box),
            )
            for line in ocr_result.lines
        ],
        ocr_quality=OCRQualityResponse(
            line_count=ocr_quality.line_count,
            average_confidence=(
                ocr_quality.average_confidence
            ),
            minimum_confidence=(
                ocr_quality.minimum_confidence
            ),
            needs_manual_review=(
                ocr_quality.needs_manual_review
            ),
            review_reason=ocr_quality.review_reason,
        ),
        event_id=event.id,
        created_at=event.created_at,
    )

@router.get(
    "/events",
    response_model=RiskEventListResponse,
    status_code=status.HTTP_200_OK,
    summary="查询风险事件历史",
    description=(
        "分页查询当前登录用户自己的风险分析记录，"
        "按照创建时间从新到旧排列。"
    ),
)
async def get_risk_events(
    limit: int = Query(
        default=20,
        ge=1,
        le=100,
        description="每页返回数量",
    ),
    offset: int = Query(
        default=0,
        ge=0,
        description="跳过的记录数量",
    ),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> RiskEventListResponse:
    """分页查询当前用户的风险历史。"""

    events, total = await list_risk_events(
        db,
        user_id=current_user.id,
        limit=limit,
        offset=offset,
    )

    return RiskEventListResponse(
        items=[
            event_to_list_item(event)
            for event in events
        ],
        total=total,
        limit=limit,
        offset=offset,
    )


@router.get(
    "/feedback/statistics",
    response_model=RiskFeedbackStatisticsResponse,
    status_code=status.HTTP_200_OK,
    summary="获取当前用户的风险反馈统计",
    description=(
        "统计当前登录用户自己的风险反馈，"
        "包括反馈类型数量、期望风险等级数量、"
        "判断准确率和需要纠正的反馈比例。"
    ),
)
async def get_my_risk_feedback_statistics(
    session: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> RiskFeedbackStatisticsResponse:
    """获取当前登录用户自己的风险反馈统计。"""

    return await (
        risk_feedback_statistics_service
        .get_user_statistics(
            session,
            user_id=current_user.id,
        )
    )


@router.get(
    "/events/{event_id}",
    response_model=RiskEventDetailResponse,
    status_code=status.HTTP_200_OK,
    summary="查询风险事件详情",
    description=(
        "查询当前登录用户自己的一条风险事件，"
        "包括原始文本、风险证据和行动建议。"
    ),
)
async def get_risk_event_detail(
    event_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> RiskEventDetailResponse:
    """查询当前用户的一条风险事件详情。"""

    event = await get_risk_event_by_id(
        db,
        event_id=event_id,
        user_id=current_user.id,
    )

    if event is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Risk event not found",
        )

    return event_to_detail(event)


@router.put(
    "/events/{event_id}/feedback",
    response_model=RiskFeedbackResponse,
    status_code=status.HTTP_200_OK,
    summary="提交或更新风险反馈",
)
async def upsert_event_feedback(
    event_id: uuid.UUID,
    payload: RiskFeedbackCreate,
    session: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> RiskFeedbackResponse:
    """
    当前用户对自己的风险事件提交反馈。

    同一个用户对同一个风险事件再次提交时，
    更新原来的反馈记录，不会创建重复记录。
    """

    feedback = await upsert_risk_feedback(
        session,
        event_id=event_id,
        user_id=current_user.id,
        payload=payload,
    )

    if feedback is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="风险事件不存在或无权访问。",
        )

    await session.commit()
    await session.refresh(feedback)

    return RiskFeedbackResponse.model_validate(
        feedback
    )


@router.get(
    "/events/{event_id}/feedback",
    response_model=RiskFeedbackResponse,
    status_code=status.HTTP_200_OK,
    summary="查询风险反馈",
)
async def read_event_feedback(
    event_id: uuid.UUID,
    session: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> RiskFeedbackResponse:
    """查询当前用户对指定风险事件提交的反馈。"""

    risk_event = await get_owned_risk_event(
        session,
        event_id=event_id,
        user_id=current_user.id,
    )

    if risk_event is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="风险事件不存在或无权访问。",
        )

    feedback = await get_risk_feedback(
        session,
        event_id=event_id,
        user_id=current_user.id,
    )

    if feedback is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="当前风险事件尚未提交反馈。",
        )

    return RiskFeedbackResponse.model_validate(
        feedback
    )