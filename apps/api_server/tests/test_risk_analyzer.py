from pathlib import Path

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

def test_disabled_rule_is_not_used(
    tmp_path: Path,
) -> None:
    """enabled=false 的规则不能参与分析。"""

    rules_path = tmp_path / "disabled_rules.yaml"

    rules_path.write_text(
        """
version: "test-disabled"

thresholds:
  medium: 25
  high: 60

scoring:
  mode: highest_per_category
  max_total: 100

rules:
  - id: TEST-DISABLED-001
    enabled: false
    category: payment
    title: 已停用的转账规则
    score: 80
    match:
      any:
        - "转账"
    explanation: 该规则已经停用。
    advice: 不应返回这条建议。
""".strip(),
        encoding="utf-8",
    )

    test_analyzer = TextRiskAnalyzer(
        rules_path=rules_path,
    )

    result = test_analyzer.analyze(
        TextRiskAnalysisRequest(
            text="请立即转账。",
        )
    )

    assert result.score == 0
    assert result.risk_level == RiskLevel.LOW
    assert result.evidence == []


def test_same_category_only_uses_highest_score(
    tmp_path: Path,
) -> None:
    """同一类别命中多条规则时只计算最高分。"""

    rules_path = tmp_path / "category_score_rules.yaml"

    rules_path.write_text(
        """
version: "test-category-score"

thresholds:
  medium: 25
  high: 60

scoring:
  mode: highest_per_category
  max_total: 100

rules:
  - id: TEST-PAYMENT-001
    enabled: true
    category: payment
    title: 普通转账请求
    score: 25
    match:
      any:
        - "转账"
    explanation: 出现转账请求。
    advice: 不要立即转账。

  - id: TEST-PAYMENT-002
    enabled: true
    category: payment
    title: 紧急转账请求
    score: 40
    match:
      all:
        - "马上"
        - "转账"
    explanation: 出现紧急转账请求。
    advice: 暂停操作并核验身份。
""".strip(),
        encoding="utf-8",
    )

    test_analyzer = TextRiskAnalyzer(
        rules_path=rules_path,
    )

    result = test_analyzer.analyze(
        TextRiskAnalysisRequest(
            text="马上给我转账。",
        )
    )

    assert len(result.evidence) == 2

    # 两条规则属于同一个 payment 类别，
    # 因此只取最高的 40 分，而不是 25 + 40。
    assert result.score == 40
    assert result.risk_level == RiskLevel.MEDIUM

def test_risk_evidence_contains_term_positions() -> None:
    """风险证据应包含准确的原文字符位置。"""

    text = (
        "请马上转账，"
        "但是不要告诉家里人，"
        "稍后还要再次转账。"
    )

    result = TextRiskAnalyzer().analyze(
        TextRiskAnalysisRequest(
            text=text,
        )
    )

    payment_evidence = next(
        evidence
        for evidence in result.evidence
        if evidence.rule_id == "R-TEXT-001"
    )

    transfer_matches = [
        match
        for match in payment_evidence.matches
        if match.term == "转账"
    ]

    assert len(transfer_matches) == 2

    for match in payment_evidence.matches:
        assert (
            text[match.start:match.end]
            == match.term
        )

    assert "资金请求" in payment_evidence.tags