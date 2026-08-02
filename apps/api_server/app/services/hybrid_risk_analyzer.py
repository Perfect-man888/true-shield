from __future__ import annotations

from dataclasses import dataclass

from app.schemas.risk import (
    RiskEvidence,
    RiskLevel,
    TextRiskAnalysisRequest,
    TextRiskAnalysisResponse,
)
from app.services.ai_semantic_risk import (
    SemanticRiskAssessment,
    SemanticRiskReviewer,
)
from app.services.risk_analyzer import (
    TextRiskAnalyzer,
)


@dataclass(frozen=True, slots=True)
class HybridRiskConfig:
    """规则与 AI 融合参数。"""

    enabled: bool
    model: str
    minimum_confidence: float = 0.72
    high_confidence: float = 0.85


@dataclass(frozen=True, slots=True)
class RiskFusionDecision:
    """一次规则分与 AI 语义分的融合结果。"""

    rule_score: int
    ai_score: int
    final_score: int
    applied: bool
    reason: str


class HybridRiskAnalyzer:
    """以可解释规则为基础、本地 AI 语义评分为增强的分析器。"""

    ENGINE_VERSION = "hybrid-2.2.0"

    def __init__(
        self,
        *,
        rule_analyzer: TextRiskAnalyzer,
        reviewer: SemanticRiskReviewer,
        config: HybridRiskConfig,
    ) -> None:
        self.rule_analyzer = rule_analyzer
        self.reviewer = reviewer
        self.config = config

    @staticmethod
    def _level_weight(level: RiskLevel) -> int:
        return {
            RiskLevel.LOW: 0,
            RiskLevel.MEDIUM: 1,
            RiskLevel.HIGH: 2,
        }[level]

    @classmethod
    def _stronger_level(
        cls,
        first: RiskLevel,
        second: RiskLevel,
    ) -> RiskLevel:
        if (
            cls._level_weight(second)
            > cls._level_weight(first)
        ):
            return second

        return first

    @property
    def _medium_threshold(self) -> int:
        return (
            self.rule_analyzer
            .rule_set
            .thresholds
            .medium
        )

    @property
    def _high_threshold(self) -> int:
        return (
            self.rule_analyzer
            .rule_set
            .thresholds
            .high
        )

    def _score_level(self, score: int) -> RiskLevel:
        if score >= self._high_threshold:
            return RiskLevel.HIGH

        if score >= self._medium_threshold:
            return RiskLevel.MEDIUM

        return RiskLevel.LOW

    def _normalize_ai_score(
        self,
        assessment: SemanticRiskAssessment,
    ) -> int:
        """
        将模型给出的等级与分数校准到规则库当前阈值。

        小模型偶尔会返回“high + 30 分”这类不一致结果。
        这里以结构化 risk_level 为边界进行校准，避免界面出现
        “AI 判高风险，但 AI 分数仍是低风险”的冲突。
        """

        score = max(0, min(100, assessment.score))

        if assessment.risk_level == RiskLevel.HIGH:
            return max(self._high_threshold, score)

        if assessment.risk_level == RiskLevel.MEDIUM:
            return min(
                self._high_threshold - 1,
                max(self._medium_threshold, score),
            )

        return min(
            max(self._medium_threshold - 1, 0),
            score,
        )

    @staticmethod
    def _semantic_signal_count(
        assessment: SemanticRiskAssessment,
    ) -> int:
        """
        统计 AI 给出的可核验语义信号。

        qwen3:1.7b 偶尔会把具体信号写进 categories 或 explanation，
        而没有严格填满 indicators。为了避免因此完全丢弃高置信度
        判断，允许“类别 + 充分说明”作为一个有效语义信号。
        """

        values = {
            value.strip().casefold()
            for value in [
                *assessment.indicators,
                *assessment.categories,
            ]
            if value.strip()
        }

        if values:
            return len(values)

        if len(assessment.explanation.strip()) >= 16:
            return 1

        return 0

    def _calculate_fused_score(
        self,
        *,
        rule_score: int,
        assessment: SemanticRiskAssessment,
    ) -> RiskFusionDecision:
        ai_score = self._normalize_ai_score(
            assessment
        )

        if assessment.safety_education:
            return RiskFusionDecision(
                rule_score=rule_score,
                ai_score=ai_score,
                final_score=rule_score,
                applied=False,
                reason=(
                    "AI 将文本识别为反诈科普或安全提醒，"
                    "不提高规则评分。"
                ),
            )

        if (
            assessment.confidence
            < self.config.minimum_confidence
        ):
            return RiskFusionDecision(
                rule_score=rule_score,
                ai_score=ai_score,
                final_score=rule_score,
                applied=False,
                reason=(
                    "AI 置信度未达到融合阈值，"
                    "最终评分保持规则结果。"
                ),
            )

        signal_count = self._semantic_signal_count(
            assessment
        )

        # 规则仍占主要权重；AI 用来补足同义改写、隐晦诱导等
        # 关键词规则难以覆盖的风险。
        weighted_score = round(
            rule_score * 0.60
            + ai_score * 0.40
        )

        final_score = max(
            rule_score,
            weighted_score,
        )
        reason = "规则评分与 AI 语义评分已按 60%/40% 融合。"

        if assessment.risk_level == RiskLevel.HIGH:
            if (
                assessment.confidence
                >= self.config.high_confidence
                and signal_count >= 1
            ):
                # 高置信度语义高风险必须真正反映到最终分数，
                # 但将 AI 单独贡献上限控制为 90，避免模型直接给满分。
                final_score = max(
                    final_score,
                    self._high_threshold,
                    min(ai_score, 90),
                )
                reason = (
                    "AI 以高置信度识别到完整诈骗语义，"
                    "已启用高风险语义兜底。"
                )
            else:
                # 置信度尚未达到高风险兜底门槛时，AI 仍可把
                # 明显可疑但证据不足的文本提升到中风险核验区间。
                final_score = max(
                    final_score,
                    self._medium_threshold,
                )
                reason = (
                    "AI 识别到高风险倾向，但置信度或语义证据"
                    "不足以直接兜底为高风险，已提升至核验区间。"
                )

        elif assessment.risk_level == RiskLevel.MEDIUM:
            final_score = max(
                final_score,
                self._medium_threshold,
            )
            reason = (
                "AI 识别到需要核验的语义风险，"
                "已与规则评分融合。"
            )

        else:
            # AI 判定低风险时不主动降低规则分，也不因为一个
            # 低风险 AI 分数改变已有的规则证据。
            final_score = rule_score
            reason = (
                "AI 未发现额外语义风险，"
                "最终评分保持规则结果。"
            )

        final_score = min(100, final_score)

        return RiskFusionDecision(
            rule_score=rule_score,
            ai_score=ai_score,
            final_score=final_score,
            applied=(final_score > rule_score),
            reason=reason,
        )

    def _ai_evidence(
        self,
        assessment: SemanticRiskAssessment,
    ) -> RiskEvidence:
        categories = (
            assessment.categories
            or ["semantic_review"]
        )
        matched_terms = [
            item.strip()
            for item in assessment.indicators
            if item.strip()
        ]

        if not matched_terms:
            matched_terms = [
                item.strip()
                for item in assessment.categories
                if item.strip()
            ]

        if not matched_terms:
            matched_terms = ["完整语义链条"]

        return RiskEvidence(
            rule_id="AI-SEMANTIC-001",
            category="ai_semantic",
            title="本地 AI 识别到语义风险",
            matched_terms=matched_terms[:8],
            score=self._normalize_ai_score(
                assessment
            ),
            explanation=assessment.explanation,
            tags=[
                "本地 AI 复核",
                *categories[:6],
            ],
            matches=[],
        )

    @staticmethod
    def _default_actions_for_level(
        level: RiskLevel,
    ) -> list[str]:
        """
        当 AI 将规则低风险提升为中/高风险时，不能继续沿用
        “暂未发现明显风险”这类低风险默认建议。
        """

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

    @staticmethod
    def _merge_actions(
        rule_actions: list[str],
        ai_actions: list[str],
    ) -> list[str]:
        merged: list[str] = []

        for action in [
            *ai_actions,
            *rule_actions,
        ]:
            cleaned = action.strip()

            if cleaned and cleaned not in merged:
                merged.append(cleaned)

            if len(merged) >= 5:
                break

        return merged

    def _rules_only_result(
        self,
        rule_result: TextRiskAnalysisResponse,
        *,
        ai_model: str | None = None,
        reason: str,
    ) -> TextRiskAnalysisResponse:
        return rule_result.model_copy(
            update={
                "analysis_mode": "rules_only",
                "engine_version": self.ENGINE_VERSION,
                "ai_model": ai_model,
                "rule_score": rule_result.score,
                "ai_score": None,
                "ai_risk_level": None,
                "fusion_applied": False,
                "fusion_reason": reason,
            }
        )

    async def analyze(
        self,
        request: TextRiskAnalysisRequest,
    ) -> TextRiskAnalysisResponse:
        rule_result = self.rule_analyzer.analyze(
            request
        )

        if not self.config.enabled:
            return self._rules_only_result(
                rule_result,
                reason="AI 语义复核未启用，使用规则评分。",
            )

        assessment = await self.reviewer.review(
            request,
            rule_result,
        )

        if assessment is None:
            return self._rules_only_result(
                rule_result,
                ai_model=self.config.model,
                reason=(
                    "AI 服务不可用或返回格式无效，"
                    "已安全回退到规则评分。"
                ),
            )

        decision = self._calculate_fused_score(
            rule_score=rule_result.score,
            assessment=assessment,
        )

        final_level = self._stronger_level(
            rule_result.risk_level,
            self._score_level(
                decision.final_score
            ),
        )

        evidence = list(rule_result.evidence)

        ai_can_contribute = (
            not assessment.safety_education
            and assessment.confidence
            >= self.config.minimum_confidence
            and assessment.risk_level
            in {
                RiskLevel.MEDIUM,
                RiskLevel.HIGH,
            }
            and self._semantic_signal_count(
                assessment
            ) >= 1
        )

        if ai_can_contribute:
            evidence.append(
                self._ai_evidence(assessment)
            )

        actions = rule_result.actions

        if ai_can_contribute:
            rule_actions = rule_result.actions

            if final_level != rule_result.risk_level:
                rule_actions = (
                    self._default_actions_for_level(
                        final_level
                    )
                )

            actions = self._merge_actions(
                rule_actions,
                assessment.recommended_actions,
            )

        return rule_result.model_copy(
            update={
                "risk_level": final_level,
                "score": decision.final_score,
                "rule_score": decision.rule_score,
                "ai_score": decision.ai_score,
                "ai_risk_level": assessment.risk_level,
                "fusion_applied": decision.applied,
                "fusion_reason": decision.reason,
                "evidence": evidence,
                "actions": actions,
                "analysis_mode": "rules_ai",
                "engine_version": self.ENGINE_VERSION,
                "ai_model": self.config.model,
                "ai_confidence": assessment.confidence,
                "ai_summary": assessment.explanation,
            }
        )
