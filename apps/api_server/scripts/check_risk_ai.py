from __future__ import annotations

import asyncio

from app.core.config import settings
from app.schemas.risk import TextRiskAnalysisRequest
from app.services.ai_semantic_risk import (
    OllamaReviewerConfig,
    OllamaSemanticRiskReviewer,
)
from app.services.hybrid_risk_analyzer import (
    HybridRiskAnalyzer,
    HybridRiskConfig,
)
from app.services.risk_analyzer import TextRiskAnalyzer


async def main() -> None:
    reviewer = OllamaSemanticRiskReviewer(
        OllamaReviewerConfig(
            enabled=settings.risk_ai_enabled,
            base_url=settings.risk_ai_base_url,
            model=settings.risk_ai_model,
            timeout_seconds=(
                settings.risk_ai_timeout_seconds
            ),
            max_text_chars=(
                settings.risk_ai_max_text_chars
            ),
            keep_alive=settings.risk_ai_keep_alive,
            allow_remote=settings.risk_ai_allow_remote,
        )
    )

    print(
        "AI 配置状态：",
        "已启用" if settings.risk_ai_enabled else "未启用",
    )
    print(f"模型：{settings.risk_ai_model}")
    print(f"服务地址：{settings.risk_ai_base_url}")

    available = await reviewer.is_available()
    print(
        "模型服务：",
        "可用" if available else "不可用",
    )

    if not available:
        print(
            "当前后端会自动使用增强规则库，"
            "不会影响基础风险检测。"
        )
        return

    analyzer = HybridRiskAnalyzer(
        rule_analyzer=TextRiskAnalyzer(),
        reviewer=reviewer,
        config=HybridRiskConfig(
            enabled=True,
            model=settings.risk_ai_model,
            minimum_confidence=(
                settings.risk_ai_min_confidence
            ),
            high_confidence=(
                settings.risk_ai_high_confidence
            ),
        ),
    )

    result = await analyzer.analyze(
        TextRiskAnalysisRequest(
            text=(
                "我现在不方便接电话，先帮我处理这笔费用，"
                "不要问别人，晚点再解释。"
            )
        )
    )

    print(f"分析模式：{result.analysis_mode}")
    print(f"风险等级：{result.risk_level.value}")
    print(f"风险分：{result.score}")
    print(f"AI 置信度：{result.ai_confidence}")


if __name__ == "__main__":
    asyncio.run(main())
