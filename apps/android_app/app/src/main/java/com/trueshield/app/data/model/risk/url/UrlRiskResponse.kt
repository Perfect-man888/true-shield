package com.trueshield.app.data.model.risk.url

import com.google.gson.annotations.SerializedName

/**
 * URL 风险分析并保存后的响应。
 *
 * 对应后端：
 * PersistedURLRiskAnalysisResponse
 */
data class UrlRiskResponse(
    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("source_type")
    val sourceType: String,

    @SerializedName("original_url")
    val originalUrl: String,

    @SerializedName("normalized_url")
    val normalizedUrl: String,

    /**
     * 后端识别出的主机名。
     */
    val host: String,

    @SerializedName("risk_level")
    val riskLevel: String,

    val score: Int,

    /**
     * URL 风险摘要。
     */
    val summary: String,

    /**
     * URL 接口使用 signals，
     * 与文本和图片接口的 evidence 不同。
     */
    val signals: List<UrlRiskSignalDto> =
        emptyList(),

    val actions: List<String> =
        emptyList(),

    val disclaimer: String,

    @SerializedName("rule_version")
    val ruleVersion: String,

    @SerializedName("redirect_inspection")
    val redirectInspection:
    UrlRedirectInspectionDto,
)