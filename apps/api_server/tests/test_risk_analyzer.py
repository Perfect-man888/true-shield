from app.schemas.risk import (
    RiskLevel,
    TextRiskAnalysisRequest,
)
from app.services.risk_analyzer import (
    TextRiskAnalyzer,
)

analyzer = TextRiskAnalyzer()


def analyze(text: str):
    request = TextRiskAnalysisRequest(
        text=text
    )
    return analyzer.analyze(request)


def test_rule_file_contains_at_least_ten_rules():
    assert len(analyzer.rule_set.rules) >= 10


def test_normal_text_is_low_risk():
    result = analyze(
        "今晚一起吃饭吗？"
    )

    assert result.risk_level == RiskLevel.LOW
    assert result.score == 0
    assert result.evidence == []
    assert len(result.actions) >= 3


def test_transfer_request_is_medium_risk():
    result = analyze(
        "请给我转账500元。"
    )

    assert result.risk_level == RiskLevel.MEDIUM
    assert result.score >= 25

    rule_ids = {
        evidence.rule_id
        for evidence in result.evidence
    }

    assert "R-TEXT-001" in rule_ids


def test_impersonation_secret_transfer_is_high_risk():
    result = analyze(
        "我是你儿子，手机坏了，"
        "马上给我转账，别告诉家里人。"
    )

    assert result.risk_level == RiskLevel.HIGH
    assert result.score >= 60
    assert len(result.evidence) >= 2
    assert len(result.actions) >= 3

    rule_ids = {
        evidence.rule_id
        for evidence in result.evidence
    }

    assert "R-TEXT-012" in rule_ids


def test_requesting_verification_code_is_high_risk():
    result = analyze(
        "请把短信验证码发给我。"
    )

    assert result.risk_level == RiskLevel.HIGH
    assert result.score >= 60

    rule_ids = {
        evidence.rule_id
        for evidence in result.evidence
    }

    assert "R-TEXT-005" in rule_ids


def test_security_warning_does_not_trigger_code_rule():
    result = analyze(
        "银行提醒：不要提供验证码。"
    )

    rule_ids = {
        evidence.rule_id
        for evidence in result.evidence
    }

    assert "R-TEXT-005" not in rule_ids
    assert result.risk_level == RiskLevel.LOW