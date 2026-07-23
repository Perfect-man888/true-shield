from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Literal

import yaml
from pydantic import BaseModel, Field

from app.schemas.risk import (
    RiskEvidence,
    RiskLevel,
    RiskTermMatch,
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
    enabled: bool = True
    category: str
    tags: list[str] = Field(
        default_factory=list,
    )
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


class RiskScoring(BaseModel):
    """风险分数聚合配置。"""

    mode: Literal[
        "sum",
        "highest_per_category",
    ] = "highest_per_category"

    max_total: int = Field(
        default=100,
        ge=1,
        le=100,
    )

class TextRuleSet(BaseModel):
    """整个 YAML 规则文件。"""

    version: str
    thresholds: RiskThresholds

    scoring: RiskScoring = Field(
        default_factory=RiskScoring,
    )

    rules: list[TextRiskRule]


class TextRiskAnalyzer:
    """基于 YAML 规则的文本风险分析器。"""

    # 这些类别表示“危险操作”。
    # 当危险词受到“不要、禁止、请勿”等否定词控制时，
    # 不应作为风险证据。
    _NEGATION_AWARE_CATEGORIES = {
        "payment",
        "credential",
        "link",
        "remote_control",
        "software_install",
        "sensitive_info",
    }

    # 当前已经确认需要进行否定判断的规则。
    _NEGATION_AWARE_RULE_IDS = {
        "R-TEXT-001",
        "R-TEXT-005",
    }

    # 标点和转折词会终止前面的否定作用范围。
    _CLAUSE_BOUNDARY_RE = re.compile(
        r"(?:但是|不过|然而|可是|却|但|[。！？!?；;，,\n])"
    )

    # 否定词后最多允许间隔 12 个字符。
    _NEGATION_RE = re.compile(
        r"(?:千万|绝对|务必)?"
        r"(?:不要|别|切勿|请勿|禁止|不可|不能|不应|无需|无须)"
        r"(?P<between>.{0,12})$"
    )

    # 下面这些表达中的“不要”不是安全否定。
    # 例如“不要错过，马上转账”仍然具有风险。
    _NEGATION_FALSE_FRIENDS = (
        "错过",
        "耽误",
        "犹豫",
        "拖延",
        "忘了",
        "忘记",
    )

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

    @staticmethod
    def _find_term_positions(
        text: str,
        keyword: str,
    ) -> list[tuple[int, int]]:
        """查找关键词在原始文本中的所有位置。"""

        source = text.casefold()
        term = keyword.strip().casefold()

        if not term:
            return []

        positions: list[tuple[int, int]] = []
        search_start = 0

        while True:
            start = source.find(term, search_start)

            if start == -1:
                break

            end = start + len(term)
            positions.append((start, end))

            search_start = start + max(len(term), 1)

        return positions

    def _is_negated(
        self,
        rule: TextRiskRule,
        text: str,
        term_start: int,
    ) -> bool:
        """
        判断某个危险词是否受到附近否定词控制。

        例如：
        “不要转账”中的“转账”会被抑制；
        “不要转账，但现在马上转账”中后一个“转账”
        不会被前面的“不要”抑制。
        """

        if (
            rule.id not in self._NEGATION_AWARE_RULE_IDS
            and rule.category
            not in self._NEGATION_AWARE_CATEGORIES
        ):
            return False

        prefix = text[:term_start].casefold()

        # 只分析当前分句，标点和转折词之前的否定词不生效。
        boundaries = list(
            self._CLAUSE_BOUNDARY_RE.finditer(prefix)
        )

        if boundaries:
            prefix = prefix[boundaries[-1].end():]

        compact_prefix = self._normalize(prefix)

        negation_match = self._NEGATION_RE.search(
            compact_prefix
        )

        if negation_match is None:
            return False

        between = negation_match.group("between")

        # “不要错过”“别忘了”等不属于安全提醒。
        if any(
            between.startswith(false_friend)
            for false_friend in self._NEGATION_FALSE_FRIENDS
        ):
            return False

        return True

    def _has_effective_term(
        self,
        rule: TextRiskRule,
        text: str,
        keyword: str,
    ) -> bool:
        """
        判断关键词是否存在至少一次未被否定的有效命中。
        """

        positions = self._find_term_positions(
            text,
            keyword,
        )

        # 保留原来的去空格匹配能力。
        if not positions:
            return self._contains(
                self._normalize(text),
                keyword,
            )

        return any(
            not self._is_negated(
                rule,
                text,
                start,
            )
            for start, _ in positions
        )

    def _match_rule(
        self,
        rule: TextRiskRule,
        request: TextRiskAnalysisRequest,
    ) -> list[str] | None:
        raw_text = request.text
        normalized_text = self._normalize(raw_text)
        match = rule.match

        # exclude_any 仍然作为整条规则的排除条件。
        if any(
            self._contains(
                normalized_text,
                keyword,
            )
            for keyword in match.exclude_any
        ):
            return None

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

        # any 中至少有一个未被否定的有效关键词。
        if match.any:
            any_terms = [
                keyword
                for keyword in match.any
                if self._has_effective_term(
                    rule,
                    raw_text,
                    keyword,
                )
            ]

            if not any_terms:
                return None

            matched_terms.extend(any_terms)

        # all 中的每个关键词都必须存在有效命中。
        if match.all:
            if not all(
                self._has_effective_term(
                    rule,
                    raw_text,
                    keyword,
                )
                for keyword in match.all
            ):
                return None

            matched_terms.extend(match.all)

        # 每个 all_groups 分组至少命中一个有效关键词。
        for group in match.all_groups:
            group_terms = [
                keyword
                for keyword in group
                if self._has_effective_term(
                    rule,
                    raw_text,
                    keyword,
                )
            ]

            if not group_terms:
                return None

            matched_terms.extend(group_terms)

        if not has_condition:
            return None

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

    
    def _locate_matched_terms(
        self,
        text: str,
        matched_terms: list[str],
        rule: TextRiskRule,
    ) -> list[RiskTermMatch]:
        """
        查找有效命中词在原始文本中的位置。

        被“不要、请勿、禁止”等否定词控制的命中，
        不会作为风险证据返回。
        """

        normalized_text = text.casefold()

        matches: list[RiskTermMatch] = []
        seen_positions: set[tuple[int, int, str]] = set()

        for original_term in matched_terms:
            term = original_term.strip()

            if not term:
                continue

            normalized_term = term.casefold()
            search_start = 0

            while True:
                start = normalized_text.find(
                    normalized_term,
                    search_start,
                )

                if start == -1:
                    break

                end = start + len(term)

                # 被否定的词不加入 evidence。
                if self._is_negated(
                    rule,
                    text,
                    start,
                ):
                    search_start = start + max(
                        len(term),
                        1,
                    )
                    continue

                position_key = (
                    start,
                    end,
                    term,
                )

                if position_key not in seen_positions:
                    seen_positions.add(position_key)

                    matches.append(
                        RiskTermMatch(
                            term=text[start:end],
                            start=start,
                            end=end,
                        )
                    )

                search_start = start + max(
                    len(term),
                    1,
                )

        return sorted(
            matches,
            key=lambda item: (
                item.start,
                item.end,
                item.term,
            ),
        )

    def _calculate_score(
        self,
        matched: list[
            tuple[TextRiskRule, list[str]]
        ],
    ) -> int:
        """根据规则配置计算最终风险分数。"""

        scoring = self.rule_set.scoring

        if scoring.mode == "sum":
            raw_score = sum(
                rule.score
                for rule, _ in matched
            )

        else:
            highest_scores: dict[str, int] = {}

            for rule, _ in matched:
                previous_score = highest_scores.get(
                    rule.category,
                    0,
                )

                highest_scores[rule.category] = max(
                    previous_score,
                    rule.score,
                )

            raw_score = sum(
                highest_scores.values()
            )

        return min(
            scoring.max_total,
            raw_score,
        )

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
            if not rule.enabled:
                continue

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

        score = self._calculate_score(
            matched,
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
                tags=rule.tags,
                matches=self._locate_matched_terms(
                    request.text,
                    matched_terms,
                    rule,
                ),
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