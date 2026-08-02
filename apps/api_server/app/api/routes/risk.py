from __future__ import annotations

import logging
import tempfile
import uuid
from pathlib import Path

from fastapi import (
    APIRouter,
    Depends,
    File,
    Form,
    HTTPException,
    Query,
    UploadFile,
    status,
)
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from starlette.concurrency import run_in_threadpool
from starlette.responses import Response

from app.api.dependencies import get_current_user
from app.core.config import settings
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
    TextRiskAnalysisResponse,
)
from app.schemas.risk_dashboard import (
    RiskDashboardResponse,
)
from app.schemas.risk_engine import (
    RiskEngineStatusResponse,
)
from app.schemas.risk_report import (
    RiskReportSummaryResponse,
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
from app.schemas.risk_voice import (
    VoiceRiskAnalysisRequest,
    VoiceRiskAnalysisResponse,
)
from app.services.family_alert_automation_service import (
    trigger_family_alert_automation_safely,
)
from app.services.ocr_service import (
    OCRImageTooLargeError,
    OCRInvalidImageError,
    OCRNoTextError,
    OCRService,
    OCRServiceError,
    evaluate_ocr_quality,
)
from app.services.ai_semantic_risk import (
    OllamaReviewerConfig,
    OllamaSemanticRiskReviewer,
)
from app.services.hybrid_risk_analyzer import (
    HybridRiskAnalyzer,
    HybridRiskConfig,
)
from app.services.risk_analyzer import TextRiskAnalyzer
from app.services.risk_dashboard_service import (
    FamilyDashboardAccessError,
    RiskDashboardService,
)
from app.services.risk_report_pdf import (
    RiskReportPdfRenderer,
)
from app.services.risk_report_service import (
    FamilyRiskReportAccessError,
    RiskReportService,
)
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
from app.services.voice_transcription import (
    VoiceAudioTooLongError,
    VoiceNoSpeechError,
    VoiceTranscriptionError,
    VoiceTranscriptionUnavailableError,
    voice_transcription_service,
)

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/risk",
)

text_rule_analyzer = TextRiskAnalyzer()

semantic_risk_reviewer = OllamaSemanticRiskReviewer(
    OllamaReviewerConfig(
        enabled=settings.risk_ai_enabled,
        base_url=settings.risk_ai_base_url,
        model=settings.risk_ai_model,
        timeout_seconds=(
            settings.risk_ai_timeout_seconds
        ),
        max_text_chars=(
            settings.risk_ai_max_text_chars
        ),
        keep_alive=settings.risk_ai_keep_alive,
        allow_remote=(
            settings.risk_ai_allow_remote
        ),
    )
)

hybrid_risk_analyzer = HybridRiskAnalyzer(
    rule_analyzer=text_rule_analyzer,
    reviewer=semantic_risk_reviewer,
    config=HybridRiskConfig(
        enabled=settings.risk_ai_enabled,
        model=settings.risk_ai_model,
        minimum_confidence=(
            settings.risk_ai_min_confidence
        ),
        high_confidence=(
            settings.risk_ai_high_confidence
        ),
    ),
)

url_risk_analyzer = URLRiskAnalyzer()
url_redirect_resolver = URLRedirectResolver()
ocr_service = OCRService()

risk_feedback_statistics_service = (
    RiskFeedbackStatisticsService()
)

risk_dashboard_service = RiskDashboardService()
risk_report_service = RiskReportService()
risk_report_pdf_renderer = RiskReportPdfRenderer()

VOICE_ALLOWED_CONTENT_TYPES = {
    "audio/mp4",
    "audio/m4a",
    "audio/aac",
    "audio/mpeg",
    "audio/wav",
    "audio/x-wav",
    "audio/ogg",
    "application/octet-stream",
}


def _risk_fusion_metadata(
    analysis: TextRiskAnalysisResponse,
) -> dict[str, object]:
    """
    构造可随风险事件一起保存的评分来源。

    使用独立的 risk_fusion 节点，避免依赖 repositories/risk.py
    的具体实现；图片、语音原有 source_metadata 也会被保留。
    """

    return jsonable_encoder(
        {
            "rule_score": analysis.rule_score,
            "ai_score": analysis.ai_score,
            "ai_risk_level": analysis.ai_risk_level,
            "fusion_applied": analysis.fusion_applied,
            "fusion_reason": analysis.fusion_reason,
            "final_score": analysis.score,
        }
    )


def _with_risk_fusion_metadata(
    source_metadata: dict[str, object] | None,
    analysis: TextRiskAnalysisResponse,
) -> dict[str, object]:
    metadata = dict(source_metadata or {})
    metadata["risk_fusion"] = _risk_fusion_metadata(
        analysis
    )
    return metadata


def _voice_audio_suffix(upload: UploadFile) -> str:
    filename = (upload.filename or "voice.m4a").lower()
    suffix = Path(filename).suffix
    if suffix in {".m4a", ".mp4", ".aac", ".mp3", ".wav", ".ogg"}:
        return suffix
    return ".m4a"


async def _save_voice_upload_to_temp(upload: UploadFile) -> tuple[Path, int]:
    content_type = (upload.content_type or "application/octet-stream").lower()
    if content_type not in VOICE_ALLOWED_CONTENT_TYPES:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="不支持该录音格式，请上传 m4a、mp4、aac、mp3、wav 或 ogg 音频。",
        )

    temp_file = tempfile.NamedTemporaryFile(
        prefix="true-shield-voice-",
        suffix=_voice_audio_suffix(upload),
        delete=False,
    )
    temp_path = Path(temp_file.name)
    total_bytes = 0

    try:
        while chunk := await upload.read(1024 * 1024):
            total_bytes += len(chunk)
            if total_bytes > settings.voice_audio_max_bytes:
                raise HTTPException(
                    status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                    detail=(
                        "录音文件过大，最大允许 "
                        f"{settings.voice_audio_max_bytes // (1024 * 1024)} MB。"
                    ),
                )
            temp_file.write(chunk)
    except Exception:
        temp_file.close()
        temp_path.unlink(missing_ok=True)
        raise
    finally:
        await upload.close()

    temp_file.close()
    if total_bytes < 256:
        temp_path.unlink(missing_ok=True)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="录音文件为空或内容过短，请重新录制。",
        )

    return temp_path, total_bytes


async def _persist_voice_analysis(
    *,
    db: AsyncSession,
    current_user: User,
    transcript: str,
    language: str,
    recognition_provider: str,
    recognition_confidence: float | None,
    language_probability: float | None,
    duration_seconds: float | None,
    transcription_model: str | None,
    audio_size_bytes: int | None,
    audio_content_type: str | None,
) -> VoiceRiskAnalysisResponse:
    text_request = TextRiskAnalysisRequest(text=transcript)
    analysis = await hybrid_risk_analyzer.analyze(text_request)

    voice_source_metadata = _with_risk_fusion_metadata(
        {
            "voice": jsonable_encoder(
                {
                    "language": language,
                    "recognition_provider": recognition_provider,
                    "recognition_confidence": recognition_confidence,
                    "language_probability": language_probability,
                    "duration_seconds": duration_seconds,
                    "transcription_model": transcription_model,
                    "audio_size_bytes": audio_size_bytes,
                    "audio_content_type": audio_content_type,
                    "raw_audio_stored": False,
                }
            ),
        },
        analysis,
    )

    event = await create_risk_event(
        db,
        user_id=current_user.id,
        request=text_request,
        analysis=analysis,
        source_type="voice",
        source_text=transcript,
        source_metadata=voice_source_metadata,
    )

    await trigger_family_alert_automation_safely(db, event=event)

    return VoiceRiskAnalysisResponse(
        **analysis.model_dump(),
        source_type="voice",
        transcript=transcript,
        language=language,
        recognition_provider=recognition_provider,
        recognition_confidence=recognition_confidence,
        language_probability=language_probability,
        duration_seconds=duration_seconds,
        transcription_model=transcription_model,
        audio_size_bytes=audio_size_bytes,
        event_id=event.id,
        created_at=event.created_at,
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

    source_metadata = event.source_metadata or {}
    engine_metadata = source_metadata.get(
        "analysis_engine",
        {},
    )
    fusion_metadata = source_metadata.get(
        "risk_fusion",
        {},
    )

    if not isinstance(engine_metadata, dict):
        engine_metadata = {}

    if not isinstance(fusion_metadata, dict):
        fusion_metadata = {}

    return RiskEventDetailResponse(
        event_id=event.id,
        created_at=event.created_at,
        source_metadata=event.source_metadata,
        source_type=event.source_type,
        source_text=event.source_text,
        summary=event.summary,
        risk_level=RiskLevel(event.risk_level),
        score=event.risk_score,
        rule_score=fusion_metadata.get(
            "rule_score"
        ),
        ai_score=fusion_metadata.get(
            "ai_score"
        ),
        ai_risk_level=fusion_metadata.get(
            "ai_risk_level"
        ),
        fusion_applied=bool(
            fusion_metadata.get(
                "fusion_applied",
                False,
            )
        ),
        fusion_reason=fusion_metadata.get(
            "fusion_reason"
        ),
        evidence=evidence,
        actions=event.actions,
        disclaimer=event.disclaimer,
        rule_version=event.rule_version,
        analysis_mode=str(
            engine_metadata.get(
                "analysis_mode",
                "rules_only",
            )
        ),
        engine_version=str(
            engine_metadata.get(
                "engine_version",
                "rules-1.0.0",
            )
        ),
        ai_model=engine_metadata.get("ai_model"),
        ai_confidence=engine_metadata.get(
            "ai_confidence"
        ),
        ai_summary=engine_metadata.get("ai_summary"),
    )


@router.post(
    "/text/analyze",
    response_model=PersistedTextRiskAnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="分析并保存可疑聊天文本",
    description=(
        "先由 YAML 可解释规则库分析聊天文本，"
        "可选使用本地 AI 进行语义复核，"
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

    analysis = await hybrid_risk_analyzer.analyze(request)

    event = await create_risk_event(
        db,
        user_id=current_user.id,
        request=request,
        analysis=analysis,
        source_type="text",
        source_text=request.text,
        source_metadata=_with_risk_fusion_metadata(
            None,
            analysis,
        ),
    )

    await trigger_family_alert_automation_safely(
        db,
        event=event,
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

    analysis = await hybrid_risk_analyzer.analyze(
        text_request
    )

    image_source_metadata = _with_risk_fusion_metadata(
        {
            "image": jsonable_encoder(
                {
                    "filename": image.filename,
                    "content_type": image.content_type,
                    "image_width": ocr_result.width,
                    "image_height": ocr_result.height,
                    "extracted_text": ocr_result.text,
                    "ocr_lines": ocr_result.lines,
                    "ocr_quality": ocr_quality,
                }
            ),
        },
        analysis,
    )

    event = await create_risk_event(
        db,
        user_id=current_user.id,
        request=text_request,
        analysis=analysis,
        source_type="image",
        source_text=ocr_result.text,
        source_metadata=image_source_metadata,
    )

    await trigger_family_alert_automation_safely(
        db,
        event=event,
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
    "/voice/analyze",
    response_model=VoiceRiskAnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="分析人工确认的语音转写文本",
    description=(
        "用于用户修正本地模型转写结果后的重新检测。"
        "正式录音流程请使用 /voice/audio/analyze。"
    ),
)
async def analyze_voice_risk(
    request: VoiceRiskAnalysisRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> VoiceRiskAnalysisResponse:
    return await _persist_voice_analysis(
        db=db,
        current_user=current_user,
        transcript=request.transcript,
        language=request.language,
        recognition_provider=request.recognition_provider,
        recognition_confidence=request.recognition_confidence,
        language_probability=None,
        duration_seconds=request.duration_seconds,
        transcription_model=request.transcription_model,
        audio_size_bytes=None,
        audio_content_type=None,
    )


@router.post(
    "/voice/audio/analyze",
    response_model=VoiceRiskAnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="上传录音、本地转写并执行风险检测",
    description=(
        "接收 Android 录制的音频，使用服务器本地 faster-whisper 模型转写，"
        "随后执行反诈风险分析。临时音频在请求完成后立即删除，不长期保存。"
    ),
)
async def analyze_voice_audio(
    audio: UploadFile = File(..., description="m4a/mp4/aac/mp3/wav/ogg 录音文件"),
    language: str = Form(default="zh-CN"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> VoiceRiskAnalysisResponse:
    temp_path, audio_size_bytes = await _save_voice_upload_to_temp(audio)
    audio_content_type = audio.content_type or "application/octet-stream"

    try:
        transcription = await run_in_threadpool(
            voice_transcription_service.transcribe,
            temp_path,
            requested_language=language,
        )
    except VoiceTranscriptionUnavailableError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(exc),
        ) from exc
    except VoiceAudioTooLongError as exc:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=str(exc),
        ) from exc
    except VoiceNoSpeechError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        ) from exc
    except VoiceTranscriptionError as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(exc),
        ) from exc
    finally:
        temp_path.unlink(missing_ok=True)

    return await _persist_voice_analysis(
        db=db,
        current_user=current_user,
        transcript=transcription.transcript,
        language=transcription.language,
        recognition_provider=transcription.provider,
        recognition_confidence=transcription.recognition_confidence,
        language_probability=transcription.language_probability,
        duration_seconds=transcription.duration_seconds,
        transcription_model=transcription.model_name,
        audio_size_bytes=audio_size_bytes,
        audio_content_type=audio_content_type,
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
        redirect_inspection=redirect_inspection,
    )

    await trigger_family_alert_automation_safely(
        db,
        event=event,
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
    "/engine/status",
    response_model=RiskEngineStatusResponse,
    status_code=status.HTTP_200_OK,
    summary="获取风险分析引擎状态",
    description=(
        "返回当前规则库版本、启用规则数量以及"
        "本地 AI 语义复核服务是否可用。"
    ),
)
async def get_risk_engine_status(
    current_user: User = Depends(get_current_user),
) -> RiskEngineStatusResponse:
    """获取当前风险分析引擎状态。"""

    del current_user

    total_rules = len(
        text_rule_analyzer.rule_set.rules
    )
    enabled_rules = sum(
        1
        for rule in text_rule_analyzer.rule_set.rules
        if rule.enabled
    )
    ai_available = (
        await semantic_risk_reviewer.is_available()
    )

    return RiskEngineStatusResponse(
        analysis_mode=(
            "rules_ai"
            if settings.risk_ai_enabled
            and ai_available
            else "rules_only"
        ),
        engine_version=(
            hybrid_risk_analyzer.ENGINE_VERSION
        ),
        rule_version=(
            text_rule_analyzer.rule_set.version
        ),
        total_rules=total_rules,
        enabled_rules=enabled_rules,
        ai_enabled=settings.risk_ai_enabled,
        ai_provider=settings.risk_ai_provider,
        ai_model=settings.risk_ai_model,
        ai_available=ai_available,
        privacy_mode=(
            "local_only"
            if not settings.risk_ai_allow_remote
            else "remote_allowed"
        ),
    )


@router.get(
    "/dashboard",
    response_model=RiskDashboardResponse,
    status_code=status.HTTP_200_OK,
    summary="获取个人或家庭风险统计仪表盘",
    description=(
        "默认统计当前用户自己的风险事件与相关告警。"
        "传入 family_id 后，统计该家庭有效成员的风险事件"
        "以及该家庭的告警处理情况。"
    ),
)
async def get_risk_dashboard(
    period_days: int = Query(
        default=7,
        ge=1,
        le=90,
        description="近期趋势包含的天数",
    ),
    family_id: uuid.UUID | None = Query(
        default=None,
        description="可选家庭编号；不传时统计个人数据",
    ),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> RiskDashboardResponse:
    """获取个人或家庭风险统计仪表盘。"""

    try:
        return await risk_dashboard_service.get_dashboard(
            db,
            user_id=current_user.id,
            period_days=period_days,
            family_id=family_id,
        )
    except FamilyDashboardAccessError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭不存在或无权查看该家庭统计。",
        ) from exc


@router.get(
    "/reports/summary",
    response_model=RiskReportSummaryResponse,
    status_code=status.HTTP_200_OK,
    summary="生成个人或家庭风险报告预览",
    description=(
        "返回选定周期内的脱敏风险统计、主要证据、重点事件和家庭告警。"
        "不返回原始录音、图片、完整号码或完整账号。"
    ),
)
async def get_risk_report_summary(
    period_days: int = Query(
        default=30,
        ge=1,
        le=365,
        description="报告统计周期天数",
    ),
    family_id: uuid.UUID | None = Query(
        default=None,
        description="可选家庭编号；不传时生成个人报告",
    ),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> RiskReportSummaryResponse:
    try:
        return await risk_report_service.build_report(
            db,
            current_user=current_user,
            period_days=period_days,
            family_id=family_id,
        )
    except FamilyRiskReportAccessError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭不存在或无权生成该家庭报告。",
        ) from exc


@router.get(
    "/reports/pdf",
    status_code=status.HTTP_200_OK,
    summary="导出个人或家庭风险报告 PDF",
    description=(
        "即时生成脱敏 PDF，不在服务器长期保存导出文件。"
    ),
)
async def download_risk_report_pdf(
    period_days: int = Query(
        default=30,
        ge=1,
        le=365,
        description="报告统计周期天数",
    ),
    family_id: uuid.UUID | None = Query(
        default=None,
        description="可选家庭编号；不传时生成个人报告",
    ),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> Response:
    try:
        report = await risk_report_service.build_report(
            db,
            current_user=current_user,
            period_days=period_days,
            family_id=family_id,
        )
    except FamilyRiskReportAccessError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="家庭不存在或无权生成该家庭报告。",
        ) from exc

    try:
        pdf_bytes = await run_in_threadpool(
            risk_report_pdf_renderer.render,
            report,
        )
    except Exception as exc:
        logger.exception(
            "Failed to render risk report PDF: report_code=%s",
            report.report_code,
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=(
                "PDF 生成失败。请查看后端日志中的具体异常后重试。"
            ),
        ) from exc

    if not pdf_bytes.startswith(b"%PDF-"):
        logger.error(
            "Risk report renderer returned invalid PDF bytes: report_code=%s size=%s",
            report.report_code,
            len(pdf_bytes),
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="PDF 生成结果无效，请稍后重试。",
        )

    filename = (
        "true-shield-family-risk-report.pdf"
        if family_id is not None
        else "true-shield-personal-risk-report.pdf"
    )
    # 风险报告体积较小，直接返回完整响应比流式响应在部分 Android
    # 设备和本地开发代理环境中更稳定。
    return Response(
        content=pdf_bytes,
        media_type="application/pdf",
        headers={
            "Content-Disposition": f'attachment; filename="{filename}"',
            "Cache-Control": "no-store",
            "X-Content-Type-Options": "nosniff",
            "Content-Length": str(len(pdf_bytes)),
        },
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

    corrected_analysis = await hybrid_risk_analyzer.analyze(
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