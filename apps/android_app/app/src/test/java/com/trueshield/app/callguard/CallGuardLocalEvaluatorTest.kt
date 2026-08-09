package com.trueshield.app.callguard

import com.trueshield.app.data.model.callguard.CallGuardRuleBundleDto
import com.trueshield.app.data.model.callguard.CallGuardRuleItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallGuardLocalEvaluatorTest {
    private val confirmedBundle = CallGuardRuleBundleDto(
        version = "test-1",
        updatedAt = "2026-08-09T00:00:00Z",
        expiresAt = "2099-08-09T00:00:00Z",
        checksum = "test",
        disclaimer = "test",
        rules = listOf(
            CallGuardRuleItemDto(
                ruleId = "CONFIRMED-001",
                matchType = "exact",
                value = "13800138000",
                score = 90,
                title = "已确认测试风险号码",
                explanation = "仅用于测试。",
                source = "reviewed_test",
                confidence = 1.0,
                confirmed = true,
            ),
        ),
    )

    @Test
    fun confirmedOfflineRuleMatchesNormalizedMainlandNumber() {
        val result = CallGuardLocalEvaluator.evaluate(
            phoneNumber = "+86 138-0013-8000",
            direction = "incoming",
            verificationStatus = "not_verified",
            ruleBundle = confirmedBundle,
        )
        assertEquals("high", result.riskLevel)
        assertTrue(result.signals.any { it.signalId == "CONFIRMED-001" })
        assertTrue(result.signals.any { it.confirmed })
    }

    @Test
    fun failedCarrierVerificationIsHighRiskWithoutConfirmedRule() {
        val result = CallGuardLocalEvaluator.evaluate(
            phoneNumber = "13900139000",
            direction = "incoming",
            verificationStatus = "failed",
        )
        assertEquals("high", result.riskLevel)
        assertTrue(result.signals.none { it.confirmed })
    }
}
