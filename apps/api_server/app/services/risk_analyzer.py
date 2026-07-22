from __future__ import annotations

import re
from pathlib import Path
from typing import Any

import yaml
from pydantic import BaseModel, Field

from app.schemas.risk import (
    RiskEvidence,
    RiskLevel,
    TextRiskAnalysisRequest,
    TextRiskAnalysisResponse,
)


class RuleMatch(BaseModel):
    """一条规则的匹配条件。"""

    any: list[str] = Field(default_factory=list)
    all: list[str] = Field(default_factory=list)
    all_groups: list[list[str]] = Field(default_factory=list)
    exclude_any: list[str] = Field(default_factory=list)
    context: dict[str, Any] = Field(default_factory=dict)


class TextRiskRule(BaseModel):
    """单条文本风险规则。"""

    id: str
    category: str
    title: str
    score: int = Field(ge=0, le=100)
    match: RuleMatch
    explanation: str
    advice: str
    force_level: RiskLevel | None = None


class RiskThresholds(BaseModel):
    """风险等级阈值。"""

    medium: int = Field(default=25, ge=0, le=100)
    high: int = Field(default=60, ge=0, le=100)


class TextRuleSet(BaseModel):
    """整个 YAML 规则文件。"""

    version: str
    thresholds: RiskThresholds
    rules: list[TextRiskRule]


class TextRiskAnalyzer:
    """基于 YAML 规则的文本风险分析器。"""

    def __init__(self, rules_path: Path | None = None) -> None:
        self.rules_path = rules_path or (
            Path(__file__).resolve().parents[1]
            / "rules"
            / "text_risk_rules.yaml"
        )

        self.rule_set = self._load_rules(self.rules_path)

    @staticmethod
    def _load_rules(path: Path) -> TextRuleSet:
        if not path.exists():
            raise FileNotFoundError(
                f"Risk rule file not found: {path}"
            )

        with path.open("r", encoding="utf-8-sig") as file:
            raw_rules = yaml.safe_load(file)

        if not isinstance(raw_rules, dict):
            raise ValueError(
                "Risk rule file must contain a YAML object"
            )

        return TextRuleSet.model_validate(raw_rules)

    @staticmethod
    def _normalize(value: str) -> str:
        """
        转为小写并移除空白。

        这样可以识别：
        “马 上 转 账”
        “USDT”
        “usdt”
        """
        return re.sub(r"\s+", "", value).lower()

    def _contains(
        self,
        normalized_text: str,
        keyword: str,
    ) -> bool:
        return self._normalize(keyword) in normalized_text

    def _match_rule(
        self,
        rule: TextRiskRule,
        request: TextRiskAnalysisRequest,
    ) -> list[str] | None:
        text = self._normalize(request.text)
        match = rule.match

        # 排除银行安全提醒等反向表达。
        if any(
            self._contains(text, keyword)
            for keyword in match.exclude_any
        ):
            return None

        # 检查上下文条件。
        context = request.context.model_dump()

        for field_name, expected_value in match.context.items():
            if context.get(field_name) != expected_value:
                return None

        matched_terms: list[str] = []

        has_condition = bool(
            match.any
            or match.all
            or match.all_groups
            or match.context
        )

        # any 中至少命中一个。
        if match.any:
            any_terms = [
                keyword
                for keyword in match.any
                if self._contains(text, keyword)
            ]

            if not any_terms:
                return None

            matched_terms.extend(any_terms)

        # all 中所有词都必须命中。
        if match.all:
            if not all(
                self._contains(text, keyword)
                for keyword in match.all
            ):
                return None

            matched_terms.extend(match.all)

        # 每个分组至少命中一个词。
        for group in match.all_groups:
            group_terms = [
                keyword
                for keyword in group
                if self._contains(text, keyword)
            ]

            if not group_terms:
                return None

            matched_terms.extend(group_terms)

        if not has_condition:
            return None

        # 去重，同时保持原顺序。
        return list(dict.fromkeys(matched_terms))

    @staticmethod
    def _stronger_level(
        current: RiskLevel | None,
        candidate: RiskLevel | None,
    ) -> RiskLevel | None:
        if candidate is None:
            return current

        weights = {
            RiskLevel.LOW: 0,
            RiskLevel.MEDIUM: 1,
            RiskLevel.HIGH: 2,
        }

        if (
            current is None
            or weights[candidate] > weights[current]
        ):
            return candidate

        return current

    def _score_level(
        self,
        score: int,
        forced_level: RiskLevel | None,
    ) -> RiskLevel:
        if score >= self.rule_set.thresholds.high:
            level = RiskLevel.HIGH
        elif score >= self.rule_set.thresholds.medium:
            level = RiskLevel.MEDIUM
        else:
            level = RiskLevel.LOW

        return (
            self._stronger_level(level, forced_level)
            or level
        )

    @staticmethod
    def _default_actions(
        level: RiskLevel,
    ) -> list[str]:
        if level == RiskLevel.HIGH:
            return [
                "立即暂停转账、付款或提供任何敏感信息。",
                "不要提供短信验证码，也不要安装远程控制或屏幕共享软件。",
                "退出当前会话，通过原号码、官方客服电话或可信联系人重新核验身份。",
            ]

        if level == RiskLevel.MEDIUM:
            return [
                "先暂停操作，不要在对方催促下立即付款。",
                "通过原有可信联系方式核验对方身份和请求内容。",
                "不要发送验证码、银行卡信息、身份证照片或屏幕共享内容。",
            ]

        return [
            "暂未发现明显高风险组合，但不要仅凭本结果认定安全。",
            "涉及金钱或敏感信息时，仍应通过可信渠道确认对方身份。",
            "不要向任何人提供短信验证码、支付密码或远程控制权限。",
        ]

    def analyze(
        self,
        request: TextRiskAnalysisRequest,
    ) -> TextRiskAnalysisResponse:
        matched: list[
            tuple[TextRiskRule, list[str]]
        ] = []

        forced_level: RiskLevel | None = None

        for rule in self.rule_set.rules:
            matched_terms = self._match_rule(
                rule,
                request,
            )

            if matched_terms is None:
                continue

            matched.append(
                (rule, matched_terms)
            )

            forced_level = self._stronger_level(
                forced_level,
                rule.force_level,
            )

        # 高分证据排在前面。
        matched.sort(
            key=lambda item: item[0].score,
            reverse=True,
        )

        score = min(
            100,
            sum(
                rule.score
                for rule, _ in matched
            ),
        )

        risk_level = self._score_level(
            score,
            forced_level,
        )

        evidence = [
            RiskEvidence(
                rule_id=rule.id,
                category=rule.category,
                title=rule.title,
                matched_terms=matched_terms,
                score=rule.score,
                explanation=rule.explanation,
            )
            for rule, matched_terms in matched
        ]

        actions: list[str] = []

        # 先加入规则本身的建议。
        for rule, _ in matched:
            if rule.advice not in actions:
                actions.append(rule.advice)

        # 不足三条时加入默认建议。
        for action in self._default_actions(
            risk_level
        ):
            if action not in actions:
                actions.append(action)

            if len(actions) >= 3:
                break

        return TextRiskAnalysisResponse(
            risk_level=risk_level,
            score=score,
            evidence=evidence,
            actions=actions[:5],
            disclaimer=(
                "本结果仅用于风险提示，不构成司法或权威鉴定。"
                "重要事项请通过可信渠道再次确认。"
            ),
            rule_version=self.rule_set.version,
        )