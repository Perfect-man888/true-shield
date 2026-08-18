package com.trueshield.app.data.model.risk.feedback

import com.google.gson.annotations.SerializedName

/**
 * 各类风险反馈数量。
 *
 * 对应后端 RiskFeedbackTypeCounts。
 */
data class RiskFeedbackTypeCountsDto(
    val accurate: Int = 0,

    @SerializedName("false_positive")
    val falsePositive: Int = 0,

    @SerializedName("false_negative")
    val falseNegative: Int = 0,

    @SerializedName("risk_too_high")
    val riskTooHigh: Int = 0,

    @SerializedName("risk_too_low")
    val riskTooLow: Int = 0,
)

/**
 * 用户期望风险等级数量。
 *
 * 对应后端 RiskLevelCounts。
 */
data class RiskLevelCountsDto(
    val low: Int = 0,
    val medium: Int = 0,
    val high: Int = 0,
)

/**
 * 当前登录用户的风险反馈统计。
 */
data class RiskFeedbackStatisticsResponse(
    @SerializedName("total_feedbacks")
    val totalFeedbacks: Int = 0,

    @SerializedName("type_counts")
    val typeCounts: RiskFeedbackTypeCountsDto =
        RiskFeedbackTypeCountsDto(),

    @SerializedName("expected_risk_level_counts")
    val expectedRiskLevelCounts: RiskLevelCountsDto =
        RiskLevelCountsDto(),

    @SerializedName("accurate_rate")
    val accurateRate: Double = 0.0,

    @SerializedName("correction_rate")
    val correctionRate: Double = 0.0,
)
