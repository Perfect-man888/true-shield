from __future__ import annotations

from dataclasses import dataclass

import pytest

from app.schemas.risk import (
    RiskLevel,
    TextRiskAnalysisRequest,
)
from app.services.ai_semantic_risk import (
    SemanticRiskAssessment,
)
from app.services.hybrid_risk_analyzer import (
    HybridRiskAnalyzer,
    HybridRiskConfig,
)
from app.services.risk_analyzer import TextRiskAnalyzer


@dataclass
class FakeReviewer:
    result: SemanticRiskAssessment | None

    async def review(self, request, rule_result):
        return self.result


def build_analyzer(
    assessment: SemanticRiskAssessment | None,
    *,
    enabled: bool = True,
) -> HybridRiskAnalyzer:
    return HybridRiskAnalyzer(
        rule_analyzer=TextRiskAnalyzer(),
        reviewer=FakeReviewer(assessment),
        config=HybridRiskConfig(
            enabled=enabled,
            model="test-local-model",
        ),
    )


@pytest.mark.asyncio
async def test_rules_only_when_ai_is_disabled() -> None:
    analyzer = build_analyzer(
        None,
        enabled=False,
    )

    result = await analyzer.analyze(
        TextRiskAnalysisRequest(
            text="请给我转账500元。",
        )
    )

    assert result.analysis_mode == "rules_only"
    assert result.risk_level == RiskLevel.MEDIUM
    assert result.ai_model is None


@pytest.mark.asyncio
async def test_ai_can_detect_high_risk_semantic_paraphrase() -> None:
    analyzer = build_analyzer(
        SemanticRiskAssessment(
            risk_level=RiskLevel.HIGH,
            score=88,
            confidence=0.93,
            categories=["impersonation", "payment"],
            indicators=[
                "冒充熟人身份",
                "要求秘密代付",
            ],
            explanation=(
                "对方以熟人身份制造紧急情境，"
                "并要求在不核验的情况下代为付款。"
            ),
            recommended_actions=[
                "通过原号码联系本人核实。",
            ],
            safety_education=False,
        )
    )

    result = await analyzer.analyze(
        TextRiskAnalysisRequest(
            text=(
                "我现在不方便接电话，先帮我把这笔费用处理一下，"
                "不要问其他人，晚点我再解释。"
            ),
        )
    )

    assert result.analysis_mode == "rules_ai"
    assert result.risk_level == RiskLevel.HIGH
    assert result.score >= 70
    assert result.rule_score is not None
    assert result.ai_score == 88
    assert result.ai_risk_level == RiskLevel.HIGH
    assert result.fusion_applied is True
    assert result.score > result.rule_score
    assert any(
        item.rule_id == "AI-SEMANTIC-001"
        for item in result.evidence
    )


@pytest.mark.asyncio
async def test_ai_never_downgrades_strong_rule_result() -> None:
    analyzer = build_analyzer(
        SemanticRiskAssessment(
            risk_level=RiskLevel.LOW,
            score=5,
            confidence=0.95,
            categories=[],
            indicators=[],
            explanation="模型未发现额外风险。",
            recommended_actions=[],
            safety_education=False,
        )
    )

    result = await analyzer.analyze(
        TextRiskAnalysisRequest(
            text="请把短信验证码发给我。",
        )
    )

    assert result.risk_level == RiskLevel.HIGH
    assert result.score >= 60
    assert any(
        item.rule_id == "R-TEXT-005"
        for item in result.evidence
    )


@pytest.mark.asyncio
async def test_safety_education_does_not_add_ai_risk() -> None:
    analyzer = build_analyzer(
        SemanticRiskAssessment(
            risk_level=RiskLevel.LOW,
            score=8,
            confidence=0.96,
            categories=["education"],
            indicators=[],
            explanation="这是反诈安全提醒。",
            recommended_actions=[],
            safety_education=True,
        )
    )

    result = await analyzer.analyze(
        TextRiskAnalysisRequest(
            text="警方提醒：不要转账，不要提供验证码。",
        )
    )

    assert result.risk_level == RiskLevel.LOW
    assert result.score == 0
    assert not any(
        item.rule_id == "AI-SEMANTIC-001"
        for item in result.evidence
    )



@pytest.mark.asyncio
async def test_high_confidence_ai_score_is_not_discarded_when_model_only_returns_category() -> None:
    """
    回归测试：小模型有时会把风险线索写入 categories 和 explanation，
    indicators 为空。旧逻辑要求 indicators 至少两项，导致 AI 明确判高风险，
    但最终 score 仍基本等于关键词规则分。
    """

    analyzer = build_analyzer(
        SemanticRiskAssessment(
            risk_level=RiskLevel.HIGH,
            score=91,
            confidence=0.94,
            categories=["impersonation_payment"],
            indicators=[],
            explanation=(
                "对方冒充熟人并制造无法接电话的紧急情境，"
                "要求用户绕过本人核验立即代付费用。"
            ),
            recommended_actions=[
                "通过原号码联系本人核实。",
            ],
            safety_education=False,
        )
    )

    result = await analyzer.analyze(
        TextRiskAnalysisRequest(
            text=(
                "我现在不方便接电话，你先替我把那笔费用处理了，"
                "先别告诉其他人，稍后我解释。"
            ),
        )
    )

    assert result.ai_risk_level == RiskLevel.HIGH
    assert result.ai_score == 91
    assert result.fusion_applied is True
    assert result.risk_level == RiskLevel.HIGH
    assert result.score >= 60
    assert result.score > (result.rule_score or 0)
    assert result.fusion_reason
    assert not any(
        "暂未发现明显高风险" in action
        for action in result.actions
    )
    assert any(
        item.rule_id == "AI-SEMANTIC-001"
        for item in result.evidence
    )


@pytest.mark.asyncio
async def test_low_confidence_ai_does_not_override_rule_score() -> None:
    analyzer = build_analyzer(
        SemanticRiskAssessment(
            risk_level=RiskLevel.HIGH,
            score=95,
            confidence=0.50,
            categories=["payment"],
            indicators=["要求秘密付款"],
            explanation="模型发现付款诱导，但置信度不足。",
            recommended_actions=[],
            safety_education=False,
        )
    )

    result = await analyzer.analyze(
        TextRiskAnalysisRequest(
            text="请帮我处理一下费用。",
        )
    )

    assert result.score == result.rule_score
    assert result.ai_score == 95
    assert result.fusion_applied is False
    assert "置信度" in (result.fusion_reason or "")
    assert not any(
        item.rule_id == "AI-SEMANTIC-001"
        for item in result.evidence
    )


@pytest.mark.asyncio
async def test_ai_level_and_score_are_calibrated_consistently() -> None:
    analyzer = build_analyzer(
        SemanticRiskAssessment(
            risk_level=RiskLevel.HIGH,
            score=20,
            confidence=0.95,
            categories=["impersonation"],
            indicators=["冒充身份"],
            explanation="对方冒充身份并要求绕过正常核验流程。",
            recommended_actions=[],
            safety_education=False,
        )
    )

    result = await analyzer.analyze(
        TextRiskAnalysisRequest(
            text="我换了联系方式，先按照我说的处理。",
        )
    )

    assert result.ai_score is not None
    assert result.ai_score >= 60
    assert result.risk_level == RiskLevel.HIGH
    assert result.score >= 60
