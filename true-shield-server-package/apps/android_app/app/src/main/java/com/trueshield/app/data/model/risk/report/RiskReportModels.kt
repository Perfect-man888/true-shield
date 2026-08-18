package com.trueshield.app.data.model.risk.report

import com.google.gson.annotations.SerializedName
import com.trueshield.app.data.model.risk.dashboard.FamilyAlertStatusDistributionDto
import com.trueshield.app.data.model.risk.dashboard.RiskDailyTrendPointDto
import com.trueshield.app.data.model.risk.dashboard.RiskLevelDistributionDto
import com.trueshield.app.data.model.risk.dashboard.RiskSourceDistributionDto

data class RiskReportOverviewDto(
    @SerializedName("total_events")
    val totalEvents: Int = 0,

    @SerializedName("average_score")
    val averageScore: Double = 0.0,

    @SerializedName("highest_score")
    val highestScore: Int = 0,

    @SerializedName("high_risk_events")
    val highRiskEvents: Int = 0,

    @SerializedName("medium_risk_events")
    val mediumRiskEvents: Int = 0,

    @SerializedName("low_risk_events")
    val lowRiskEvents: Int = 0,

    @SerializedName("alert_count")
    val alertCount: Int = 0,

    @SerializedName("resolved_alert_count")
    val resolvedAlertCount: Int = 0,

    @SerializedName("resolution_rate")
    val resolutionRate: Double = 0.0,
)

data class RiskReportTopSignalDto(
    @SerializedName("signal_type")
    val signalType: String,
    val title: String,
    val count: Int,
)

data class RiskReportEventItemDto(
    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("source_type")
    val sourceType: String,

    @SerializedName("risk_level")
    val riskLevel: String,

    @SerializedName("risk_score")
    val riskScore: Int,

    val summary: String,

    @SerializedName("source_excerpt")
    val sourceExcerpt: String? = null,

    @SerializedName("evidence_titles")
    val evidenceTitles: List<String> = emptyList(),

    @SerializedName("created_at")
    val createdAt: String,
)

data class RiskReportAlertItemDto(
    @SerializedName("alert_id")
    val alertId: String,

    @SerializedName("risk_event_id")
    val riskEventId: String,

    @SerializedName("risk_level")
    val riskLevel: String,

    val title: String,
    val summary: String,
    val status: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("resolved_at")
    val resolvedAt: String? = null,

    @SerializedName("resolution_note")
    val resolutionNote: String? = null,
)

data class RiskReportSummaryResponse(
    @SerializedName("report_code")
    val reportCode: String,

    @SerializedName("report_title")
    val reportTitle: String,

    val scope: String,

    @SerializedName("subject_name")
    val subjectName: String,

    @SerializedName("family_id")
    val familyId: String? = null,

    @SerializedName("family_name")
    val familyName: String? = null,

    @SerializedName("member_count")
    val memberCount: Int = 1,

    @SerializedName("period_days")
    val periodDays: Int = 30,

    @SerializedName("period_start")
    val periodStart: String,

    @SerializedName("period_end")
    val periodEnd: String,

    @SerializedName("generated_at")
    val generatedAt: String,

    val overview: RiskReportOverviewDto =
        RiskReportOverviewDto(),

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

    @SerializedName("top_signals")
    val topSignals: List<RiskReportTopSignalDto> =
        emptyList(),

    @SerializedName("recent_events")
    val recentEvents: List<RiskReportEventItemDto> =
        emptyList(),

    @SerializedName("recent_alerts")
    val recentAlerts: List<RiskReportAlertItemDto> =
        emptyList(),

    @SerializedName("privacy_notice")
    val privacyNotice: String,

    val disclaimer: String,
)
