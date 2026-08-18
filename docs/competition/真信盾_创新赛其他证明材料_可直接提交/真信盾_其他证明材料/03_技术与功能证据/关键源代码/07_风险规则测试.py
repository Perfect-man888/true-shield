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

def test_negated_transfer_and_verification_code_are_low_risk() -> None:
    """安全提醒中的转账和验证码不应被判定为风险。"""

    result = analyze(
        "千万不要给陌生人转账，也不要把验证码告诉骗子。"
    )

    assert result.risk_level == RiskLevel.LOW
    assert result.score == 0
    assert result.evidence == []


def test_police_warning_is_low_risk() -> None:
    """警方安全提醒不应触发危险操作规则。"""

    result = analyze(
        "警方提醒：不要点击陌生链接，不要向任何人提供验证码。"
    )

    assert result.risk_level == RiskLevel.LOW
    assert result.score == 0
    assert result.evidence == []


def test_later_transfer_request_is_not_suppressed_by_earlier_negation() -> None:
    """转折词之后的真实资金要求仍应被识别。"""

    result = analyze(
        "虽然警方说不要转账，但你现在必须马上把钱转过来。"
    )

    assert result.risk_level == RiskLevel.MEDIUM
    assert result.score == 40

    evidence_by_rule = {
        evidence.rule_id: evidence
        for evidence in result.evidence
    }

    assert set(evidence_by_rule) == {
        "R-TEXT-001",
        "R-TEXT-002",
    }

    payment_evidence = evidence_by_rule["R-TEXT-001"]

    assert payment_evidence.matched_terms == [
        "把钱转过来"
    ]

    assert [
        match.term
        for match in payment_evidence.matches
    ] == [
        "把钱转过来"
    ]


def test_do_not_forget_transfer_is_still_a_transfer_request() -> None:
    """“不要忘了转账”是操作要求，不属于安全提醒。"""

    result = analyze(
        "不要忘了给对方转账。"
    )

    assert result.risk_level == RiskLevel.MEDIUM
    assert result.score == 25

    assert {
        evidence.rule_id
        for evidence in result.evidence
    } == {
        "R-TEXT-001"
    }

def test_police_impersonation_transfer_chain_triggers_combo_rule() -> None:
    """冒充公安并要求保密、紧急转账，应触发组合风险规则。"""

    result = analyze(
        "我是公安局的，你涉嫌洗钱。"
        "这件事不能告诉家人，必须马上把钱转到安全账户。"
    )

    assert result.risk_level == RiskLevel.HIGH

    evidence_by_rule = {
        evidence.rule_id: evidence
        for evidence in result.evidence
    }

    assert "R-COMBO-001" in evidence_by_rule

    combo_evidence = evidence_by_rule["R-COMBO-001"]

    assert combo_evidence.category == "fraud_chain"
    assert combo_evidence.score == 80
    assert "冒充公检法" in combo_evidence.tags
    assert "安全账户" in combo_evidence.tags


def test_customer_service_refund_chain_triggers_combo_rule() -> None:
    """冒充客服，以退款为由索要验证码，应触发组合风险。"""

    result = analyze(
        "我是平台客服，你的订单出现异常，"
        "现在给你办理退款，请立即提供短信验证码。"
    )

    assert result.risk_level == RiskLevel.HIGH

    evidence_by_rule = {
        evidence.rule_id: evidence
        for evidence in result.evidence
    }

    assert "R-COMBO-002" in evidence_by_rule

    combo_evidence = evidence_by_rule["R-COMBO-002"]

    assert combo_evidence.category == "fraud_chain"
    assert combo_evidence.score == 75
    assert "冒充客服" in combo_evidence.tags
    assert "索要验证码" in combo_evidence.tags


def test_single_transfer_request_does_not_trigger_combo_rule() -> None:
    """普通单一转账要求只命中基础规则，不应命中组合规则。"""

    result = analyze("请给对方转账。")

    assert result.risk_level == RiskLevel.MEDIUM

    rule_ids = {
        evidence.rule_id
        for evidence in result.evidence
    }

    assert "R-TEXT-001" in rule_ids
    assert "R-COMBO-001" not in rule_ids
    assert "R-COMBO-002" not in rule_ids


def test_police_anti_fraud_warning_does_not_trigger_combo_rule() -> None:
    """警方发布的反诈提醒不应被识别成冒充公安诈骗。"""

    result = analyze(
        "警方提醒：公检法不会要求群众把钱转入安全账户，"
        "也不要向陌生人提供验证码。"
    )

    assert result.risk_level == RiskLevel.LOW
    assert result.score == 0
    assert result.evidence == []

def test_task_rebate_scam_is_detected() -> None:
    result = analyze(
        "做点赞任务可以返利，先充值完成最后一单，完成后本金和佣金一起返还。"
    )

    assert result.risk_level == RiskLevel.HIGH
    assert {
        evidence.rule_id
        for evidence in result.evidence
    } & {
        "R-TEXT-016",
        "R-COMBO-003",
    }


def test_training_refund_investment_chain_is_high_risk() -> None:
    result = analyze(
        "教育机构开始清退学费，请加入退费群，下载软件并认购基金办理退款。"
    )

    assert result.risk_level == RiskLevel.HIGH
    assert "R-COMBO-004" in {
        evidence.rule_id
        for evidence in result.evidence
    }


def test_regex_detects_verification_code_number() -> None:
    result = analyze(
        "退款需要核验，请把验证码 839201 发给我。"
    )

    assert result.risk_level == RiskLevel.HIGH
    assert "R-TEXT-030" in {
        evidence.rule_id
        for evidence in result.evidence
    }
