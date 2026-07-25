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
    build_event_summary,
    create_risk_event,
    create_url_risk_event,
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
from app.schemas.risk_reanalysis import (
    CorrectedRiskReanalysisResponse,
    RiskAnalysisComparisonResponse,
    RiskReanalysisSnapshotResponse,
)
from app.schemas.risk_url import (
    PersistedURLRiskAnalysisResponse,
    URLRedirectInspection,
    URLRiskAnalysisRequest,
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
    OCRFeedbackNotAllowedError,
    get_owned_risk_event,
    get_risk_feedback,
    upsert_risk_feedback,
)
from app.services.risk_feedback_statistics_service import (
    RiskFeedbackStatisticsService,
)
from app.services.risk_reanalysis_service import (
    compare_risk_analyses,
)
from app.services.url_redirect_resolver import (
    UnsafeURLTargetError,
    URLRedirectResolutionError,
    URLRedirectResolver,
)
from app.services.url_redirect_risk_service import (
    enrich_url_analysis_with_redirects,
)
from app.services.url_risk_analyzer import (
    URLRiskAnalyzer,
)

router = APIRouter(
    prefix="/risk",
)

text_risk_analyzer = TextRiskAnalyzer()
url_risk_analyzer = URLRiskAnalyzer()
url_redirect_resolver = URLRedirectResolver()
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
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
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

@router.post(
    "/url/analyze",
    response_model=PersistedURLRiskAnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="分析并保存可疑链接",
    description=(
        "根据链接协议、域名、端口、敏感词、"
        "短网址和混淆结构等特征分析 URL 风险。"
        "当 resolve_redirects=true 时，"
        "还会安全解析重定向链。"
    ),
)
async def analyze_url_risk(
    request: URLRiskAnalysisRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> PersistedURLRiskAnalysisResponse:
    """分析 URL 并保存风险事件。"""

    try:
        analysis = url_risk_analyzer.analyze(
            request
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=(
                status.HTTP_422_UNPROCESSABLE_CONTENT
            ),
            detail=str(exc),
        ) from exc

    redirect_inspection = URLRedirectInspection(
        requested=request.resolve_redirects,
        status="not_requested",
        resolution=None,
        message=None,
    )

    if request.resolve_redirects:
        try:
            resolution = (
                await url_redirect_resolver.resolve(
                    analysis.normalized_url
                )
            )

        except UnsafeURLTargetError as exc:
            redirect_inspection = (
                URLRedirectInspection(
                    requested=True,
                    status="blocked",
                    resolution=None,
                    message=str(exc),
                )
            )

        except URLRedirectResolutionError as exc:
            redirect_inspection = (
                URLRedirectInspection(
                    requested=True,
                    status="failed",
                    resolution=None,
                    message=str(exc),
                )
            )

        else:
            analysis = (
                enrich_url_analysis_with_redirects(
                    analysis,
                    resolution,
                )
            )

            redirect_inspection = (
                URLRedirectInspection(
                    requested=True,
                    status="completed",
                    resolution=resolution,
                    message=None,
                )
            )

    event = await create_url_risk_event(
        db,
        user_id=current_user.id,
        request=request,
        analysis=analysis,
    )

    return PersistedURLRiskAnalysisResponse(
        **analysis.model_dump(),
        event_id=event.id,
        created_at=event.created_at,
        source_type="url",
        redirect_inspection=(
            redirect_inspection
        ),
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

    try:
        feedback = await upsert_risk_feedback(
            session,
            event_id=event_id,
            user_id=current_user.id,
            payload=payload,
        )

    except OCRFeedbackNotAllowedError as exc:
        raise HTTPException(
            status_code=(
                status.HTTP_422_UNPROCESSABLE_CONTENT
            ),
            detail=str(exc),
        ) from exc

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

@router.post(
    "/events/{event_id}/reanalyze-corrected",
    response_model=CorrectedRiskReanalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="使用人工修正文本重新分析风险",
    description=(
        "读取当前用户为图片风险事件提交的 "
        "corrected_text，重新执行风险分析，"
        "并返回修正前后的结果差异。"
        "本操作不会覆盖原始风险事件。"
    ),
)
async def reanalyze_corrected_ocr_text(
    event_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> CorrectedRiskReanalysisResponse:
    """使用人工修正的 OCR 文本重新分析风险。"""

    event = await get_risk_event_by_id(
        db,
        event_id=event_id,
        user_id=current_user.id,
    )

    if event is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="风险事件不存在或无权访问。",
        )

    if event.source_type != "image":
        raise HTTPException(
            status_code=(
                status.HTTP_422_UNPROCESSABLE_CONTENT
            ),
            detail=(
                "只有图片风险事件可以使用 "
                "OCR 修正文本重新分析。"
            ),
        )

    feedback = await get_risk_feedback(
        db,
        event_id=event_id,
        user_id=current_user.id,
    )

    if feedback is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="当前风险事件尚未提交反馈。",
        )

    if not feedback.corrected_text:
        raise HTTPException(
            status_code=(
                status.HTTP_422_UNPROCESSABLE_CONTENT
            ),
            detail=(
                "当前反馈中没有可用于重新分析的 "
                "corrected_text。"
            ),
        )

    if not event.source_text:
        raise HTTPException(
            status_code=(
                status.HTTP_422_UNPROCESSABLE_CONTENT
            ),
            detail="原始 OCR 文本不存在。",
        )

    # 从数据库事件恢复原始分析结果。
    original_detail = event_to_detail(event)

    corrected_request = TextRiskAnalysisRequest(
        text=feedback.corrected_text,
    )

    corrected_analysis = text_risk_analyzer.analyze(
        corrected_request
    )

    comparison = compare_risk_analyses(
        original_detail,
        corrected_analysis,
    )

    return CorrectedRiskReanalysisResponse(
        event_id=event.id,
        source_type="image",
        original_text=event.source_text,
        corrected_text=feedback.corrected_text,
        original_analysis=(
            RiskReanalysisSnapshotResponse(
                risk_level=original_detail.risk_level,
                score=original_detail.score,
                summary=original_detail.summary,
                evidence=original_detail.evidence,
                actions=original_detail.actions,
            )
        ),
        corrected_analysis=(
            RiskReanalysisSnapshotResponse(
                risk_level=corrected_analysis.risk_level,
                score=corrected_analysis.score,
                summary=build_event_summary(
                    corrected_analysis
                ),
                evidence=corrected_analysis.evidence,
                actions=corrected_analysis.actions,
            )
        ),
        comparison=RiskAnalysisComparisonResponse(
            original_risk_level=(
                comparison.original_risk_level
            ),
            corrected_risk_level=(
                comparison.corrected_risk_level
            ),
            original_score=(
                comparison.original_score
            ),
            corrected_score=(
                comparison.corrected_score
            ),
            score_delta=comparison.score_delta,
            risk_level_changed=(
                comparison.risk_level_changed
            ),
            added_rule_ids=list(
                comparison.added_rule_ids
            ),
            removed_rule_ids=list(
                comparison.removed_rule_ids
            ),
            retained_rule_ids=list(
                comparison.retained_rule_ids
            ),
        ),
    )