from __future__ import annotations

from app.schemas.risk import (
    RiskLevel,
    TextRiskAnalysisRequest,
)
from app.services.ai_semantic_risk import (
    OllamaReviewerConfig,
    OllamaSemanticRiskReviewer,
    SemanticRiskAssessment,
)
from app.services.risk_analyzer import TextRiskAnalyzer


def build_reviewer() -> OllamaSemanticRiskReviewer:
    return OllamaSemanticRiskReviewer(
        OllamaReviewerConfig(
            enabled=True,
            base_url="http://127.0.0.1:11434",
            model="qwen3:1.7b",
            timeout_seconds=30,
            max_text_chars=4000,
            keep_alive="5m",
            allow_remote=False,
        )
    )


def low_assessment() -> SemanticRiskAssessment:
    return SemanticRiskAssessment(
        risk_level=RiskLevel.LOW,
        score=0,
        confidence=0.90,
        categories=[],
        indicators=[],
        explanation="未发现显式风险。",
        recommended_actions=[],
        safety_education=False,
    )


def test_implicit_payment_secrecy_chain_triggers_recheck() -> None:
    reviewer = build_reviewer()
    text = (
        "我现在不方便接电话，你先替我把那笔费用处理了。"
        "这件事先不要告诉其他人，完成后把结果发给我。"
    )

    tags = reviewer._semantic_chain_tags(text)

    assert tags == {
        "financial_action",
        "secrecy",
        "verification_barrier",
    }
    assert reviewer._should_adversarial_recheck(
        text,
        low_assessment(),
    )


def test_transparent_normal_payment_does_not_trigger_recheck() -> None:
    reviewer = build_reviewer()
    text = "请通过电力公司的官方 App 缴纳本月电费，账单可以公开核对。"

    tags = reviewer._semantic_chain_tags(text)

    assert tags == {"financial_action"}
    assert not reviewer._should_adversarial_recheck(
        text,
        low_assessment(),
    )


def test_safety_education_never_triggers_recheck() -> None:
    reviewer = build_reviewer()
    assessment = low_assessment().model_copy(
        update={"safety_education": True}
    )

    assert not reviewer._should_adversarial_recheck(
        "不要告诉别人，也不要向陌生人付款。",
        assessment,
    )


def test_second_high_assessment_wins() -> None:
    reviewer = build_reviewer()
    second = SemanticRiskAssessment(
        risk_level=RiskLevel.HIGH,
        score=85,
        confidence=0.94,
        categories=["impersonation_payment"],
        indicators=[
            "阻断电话核验",
            "要求代为处理费用",
            "要求不要告诉他人",
        ],
        explanation="三项行为构成冒充熟人代付诈骗链。",
        recommended_actions=["通过原号码回拨核验。"],
        safety_education=False,
    )

    chosen = reviewer._choose_stronger_assessment(
        low_assessment(),
        second,
    )

    assert chosen is second


def test_prompt_places_text_before_rule_score_and_forbids_keyword_anchoring() -> None:
    reviewer = build_reviewer()
    request = TextRiskAnalysisRequest(
        text=(
            "我现在不方便接电话，你先替我把那笔费用处理了，"
            "先不要告诉其他人。"
        )
    )
    rule_result = TextRiskAnalyzer().analyze(request)

    messages = reviewer._build_messages(
        request,
        rule_result,
    )
    system_prompt = messages[0]["content"]
    user_prompt = messages[1]["content"]

    assert "禁止仅依据是否出现固定关键词" in system_prompt
    assert "规则分为0绝不代表安全" in system_prompt
    assert user_prompt.index("待分析文本开始") < user_prompt.index(
        "规则风险分"
    )
