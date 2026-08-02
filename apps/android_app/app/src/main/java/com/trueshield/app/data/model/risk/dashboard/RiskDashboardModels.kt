package com.trueshield.app.data.model.risk.dashboard

import com.google.gson.annotations.SerializedName

/**
 * 风险统计概览。
 */
data class RiskDashboardOverviewDto(
    @SerializedName("total_events")
    val totalEvents: Int = 0,

    @SerializedName("period_events")
    val periodEvents: Int = 0,

    @SerializedName("average_score")
    val averageScore: Double = 0.0,

    @SerializedName("latest_event_at")
    val latestEventAt: String? = null,
)

/**
 * 风险等级分布。
 */
data class RiskLevelDistributionDto(
    val low: Int = 0,
    val medium: Int = 0,
    val high: Int = 0,
)

/**
 * 检测来源分布。
 */
data class RiskSourceDistributionDto(
    val text: Int = 0,
    val image: Int = 0,
    val url: Int = 0,
    val voice: Int = 0,
    val call: Int = 0,
    val other: Int = 0,
)

/**
 * 每日风险趋势点。
 */
data class RiskDailyTrendPointDto(
    val date: String,
    val total: Int = 0,
    val low: Int = 0,
    val medium: Int = 0,
    val high: Int = 0,
)

/**
 * 家庭告警状态统计。
 */
data class FamilyAlertStatusDistributionDto(
    val total: Int = 0,
    val pending: Int = 0,
    val acknowledged: Int = 0,
    val resolved: Int = 0,
    val cancelled: Int = 0,

    @SerializedName("resolution_rate")
    val resolutionRate: Double = 0.0,
)

/**
 * 个人或家庭风险统计仪表盘响应。
 */
data class RiskDashboardResponse(
    val scope: String,

    @SerializedName("family_id")
    val familyId: String? = null,

    @SerializedName("family_name")
    val familyName: String? = null,

    @SerializedName("member_count")
    val memberCount: Int = 1,

    @SerializedName("period_days")
    val periodDays: Int = 7,

    @SerializedName("generated_at")
    val generatedAt: String,

    val overview: RiskDashboardOverviewDto =
        RiskDashboardOverviewDto(),

    @SerializedName("risk_levels")
    val riskLevels: RiskLevelDistributionDto =
        RiskLevelDistributionDto(),

    @SerializedName("source_types")
    val sourceTypes: RiskSourceDistributionDto =
        RiskSourceDistributionDto(),

    @SerializedName("daily_trend")
    val dailyTrend: List<RiskDailyTrendPointDto> =
        emptyList(),

    @SerializedName("alert_status")
    val alertStatus: FamilyAlertStatusDistributionDto =
        FamilyAlertStatusDistributionDto(),
)
