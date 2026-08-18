package com.trueshield.app.data.model.risk

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * 风险事件完整详情。
 *
 * 对应后端：
 * GET /api/v1/risk/events/{event_id}
 */
data class RiskEventDetailResponse(
    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("source_type")
    val sourceType: String,

    @SerializedName("source_text")
    val sourceText: String,

    @SerializedName("source_metadata")
    val sourceMetadata: JsonElement? = null,

    val summary: String,

    @SerializedName("risk_level")
    val riskLevel: String,

    val score: Int,


    @SerializedName("rule_score")
    val ruleScore: Int? = null,

    @SerializedName("ai_score")
    val aiScore: Int? = null,

    @SerializedName("ai_risk_level")
    val aiRiskLevel: String? = null,

    @SerializedName("fusion_applied")
    val fusionApplied: Boolean = false,

    @SerializedName("fusion_reason")
    val fusionReason: String? = null,

    val evidence: List<RiskEvidenceDto> =
        emptyList(),

    val actions: List<String> =
        emptyList(),

    val disclaimer: String,

    @SerializedName("rule_version")
    val ruleVersion: String,

    @SerializedName("analysis_mode")
    val analysisMode: String = "rules_only",

    @SerializedName("engine_version")
    val engineVersion: String = "rules-1.0.0",

    @SerializedName("ai_model")
    val aiModel: String? = null,

    @SerializedName("ai_confidence")
    val aiConfidence: Double? = null,

    @SerializedName("ai_summary")
    val aiSummary: String? = null,
)