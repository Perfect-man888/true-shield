package com.trueshield.app.callguard

import com.trueshield.app.data.model.callguard.CallGuardSignalDto
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeResponse
import com.trueshield.app.data.model.callguard.CallGuardRuleBundleDto

/**
 * 5 秒来电响应窗口内使用的本地兜底判断。
 *
 * 服务器结果返回后会覆盖本地结果，但系统来电不会等待网络。
 */
object CallGuardLocalEvaluator {

    fun evaluate(
        phoneNumber: String,
        direction: String,
        verificationStatus: String,
        ruleBundle: CallGuardRuleBundleDto? = null,
    ): CallNumberAnalyzeResponse {
        val normalized = normalize(phoneNumber)
        val signals = mutableListOf<CallGuardSignalDto>()
        var score = 0

        if (normalized == "unknown") {
            score += 45
            signals += CallGuardSignalDto(
                signalId = "LOCAL-CALL-001",
                title = "隐藏或未知号码",
                score = 45,
                explanation = "无法在本机完成号码身份核验。",
                source = "android_system",
                confidence = 0.7,
            )
        }

        if (verificationStatus == "failed") {
            score += 65
            signals += CallGuardSignalDto(
                signalId = "LOCAL-CALL-002",
                title = "运营商号码验证失败",
                score = 65,
                explanation = "该号码可能存在伪造或异常。",
                source = "carrier_verification",
                confidence = 0.9,
            )
        } else if (verificationStatus == "passed") {
            score -= 20
        }

        if (direction == "incoming" && signals.isEmpty()) {
            score += 20
            signals += CallGuardSignalDto(
                signalId = "LOCAL-CALL-003",
                title = "陌生来电",
                score = 20,
                explanation = "接听后不要透露验证码、银行卡或身份证信息。",
                source = "safety_baseline",
                confidence = 0.3,
            )
        }

        ruleBundle?.rules
            ?.filter { rule ->
                when (rule.matchType) {
                    "exact" -> normalized == normalize(rule.value)
                    "prefix" -> normalized.startsWith(normalize(rule.value))
                    else -> false
                }
            }
            ?.forEach { rule ->
                score += rule.score
                signals += CallGuardSignalDto(
                    signalId = rule.ruleId,
                    title = rule.title,
                    score = rule.score,
                    explanation = rule.explanation,
                    source = rule.source,
                    confidence = rule.confidence,
                    confirmed = rule.confirmed,
                )
            }

        if (verificationStatus == "failed") {
            score = score.coerceAtLeast(75)
        }

        score = score.coerceIn(0, 100)
        val riskLevel = when {
            score >= 70 -> "high"
            score >= 35 -> "medium"
            else -> "low"
        }
        val summary = when (riskLevel) {
            "high" -> "疑似高风险来电，请勿转账、共享验证码或安装对方指定软件。"
            "medium" -> "陌生号码需要谨慎核实，涉及资金时请先联系家人。"
            else -> "暂未发现明确号码风险，仍请谨慎核实身份。"
        }

        return CallNumberAnalyzeResponse(
            normalizedNumber = normalized,
            maskedNumber = mask(normalized),
            direction = direction,
            verificationStatus = verificationStatus,
            riskLevel = riskLevel,
            score = score,
            isTrustedContact = false,
            summary = summary,
            signals = signals,
            actions = listOf(
                "不要透露短信验证码。",
                "对方要求转账时立即暂停。",
                "必要时使用一键家庭求助。",
            ),
            ruleVersion = "call-guard-local-1.0.0",
        )
    }

    private fun normalize(value: String): String {
        val text = value.trim()
        if (text.isBlank()) {
            return "unknown"
        }
        val compact = text.replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
        return if (
            compact.startsWith("+86") &&
            compact.removePrefix("+86").length == 11
        ) {
            compact.removePrefix("+86")
        } else {
            compact
        }
    }

    private fun mask(value: String): String {
        if (value == "unknown") {
            return "未知/隐藏号码"
        }
        val digits = value.filter { it.isDigit() }
        if (digits.length <= 7) {
            return value
        }
        val prefix = if (value.startsWith("+")) "+" else ""
        return "$prefix${digits.take(3)}****${digits.takeLast(4)}"
    }
}
