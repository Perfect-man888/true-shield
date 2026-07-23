import uuid

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    Query,
    status,
)
from sqlalchemy.ext.asyncio import AsyncSession

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
    PersistedTextRiskAnalysisResponse,
    RiskEventDetailResponse,
    RiskEventListItem,
    RiskEventListResponse,
    RiskEvidence,
    RiskLevel,
    TextRiskAnalysisRequest,
)
from app.services.risk_analyzer import TextRiskAnalyzer

router = APIRouter(
    prefix="/risk",
)

text_risk_analyzer = TextRiskAnalyzer()


def event_to_list_item(
    event: RiskEvent,
) -> RiskEventListItem:
    """将数据库事件转换为列表响应。"""

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
    """将数据库事件转换为完整详情响应。"""

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
    )

    return PersistedTextRiskAnalysisResponse(
        **analysis.model_dump(),
        event_id=event.id,
        created_at=event.created_at,
    )


@router.get(
    "/events",
    response_model=RiskEventListResponse,
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
    "/events/{event_id}",
    response_model=RiskEventDetailResponse,
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