package com.trueshield.app.ui.risk.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RiskAnalysisEngineTextTest {
    @Test
    fun engineNameRecognizesRulesAiWithoutCaseSensitivity() {
        assertEquals(
            "规则库 + 本地 AI 语义复核",
            riskAnalysisEngineName("RULES_AI"),
        )
    }

    @Test
    fun engineNameFallsBackToExplainableRules() {
        assertEquals(
            "可解释规则库",
            riskAnalysisEngineName("rules"),
        )
    }

    @Test
    fun confidenceIsDisplayedAsWholePercent() {
        assertEquals("72%", formatAiConfidence(0.729))
    }

    @Test
    fun riskLevelUsesLocalizedNamesAndPreservesUnknownValues() {
        assertEquals("高风险", riskLevelDisplayName("HIGH"))
        assertEquals("中风险", riskLevelDisplayName("medium"))
        assertEquals("低风险", riskLevelDisplayName("low"))
        assertEquals("unknown", riskLevelDisplayName("unknown"))
    }

    @Test
    fun riskScoreIsClampedToBackendRange() {
        assertEquals("0/100", formatRiskScore(-1))
        assertEquals("42/100", formatRiskScore(42))
        assertEquals("100/100", formatRiskScore(101))
    }
}
