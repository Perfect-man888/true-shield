package com.trueshield.app.ui.risk.common

/**
 * 将后端分析模式转换为用户可读文字。
 */
fun riskAnalysisEngineName(
    analysisMode: String,
): String =
    if (
        analysisMode.equals(
            other = "rules_ai",
            ignoreCase = true,
        )
    ) {
        "规则库 + 本地 AI 语义复核"
    } else {
        "可解释规则库"
    }

/**
 * 将 0～1 的 AI 置信度显示为百分比。
 */
fun formatAiConfidence(
    confidence: Double,
): String =
    "${(confidence * 100).toInt()}%"

/**
 * 将后端风险等级转换为中文。
 */
fun riskLevelDisplayName(
    riskLevel: String,
): String =
    when (riskLevel.lowercase()) {
        "high" -> "高风险"
        "medium" -> "中风险"
        "low" -> "低风险"
        else -> riskLevel
    }

/**
 * 统一显示 0～100 的风险评分。
 */
fun formatRiskScore(
    score: Int,
): String = "${score.coerceIn(0, 100)}/100"

