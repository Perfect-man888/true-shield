from fastapi import APIRouter, Depends, status

from app.api.dependencies import get_current_user
from app.schemas.risk import (
    TextRiskAnalysisRequest,
    TextRiskAnalysisResponse,
)
from app.services.risk_analyzer import TextRiskAnalyzer

router = APIRouter(
    prefix="/risk",
)

text_risk_analyzer = TextRiskAnalyzer()


@router.post(
    "/text/analyze",
    response_model=TextRiskAnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="分析可疑聊天文本",
    description=(
        "根据 YAML 风险规则分析聊天文本，"
        "返回风险等级、风险分数、命中证据和行动建议。"
    ),
    dependencies=[Depends(get_current_user)],
)
def analyze_text_risk(
    request: TextRiskAnalysisRequest,
) -> TextRiskAnalysisResponse:
    """分析用户提交的可疑聊天文本。"""

    return text_risk_analyzer.analyze(request)