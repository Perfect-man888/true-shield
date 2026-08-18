package com.trueshield.app.data.model.risk

import com.google.gson.annotations.SerializedName

/**
 * 用户修正本地模型转写结果后重新检测的请求。
 */
data class VoiceRiskRequest(
    val transcript: String,

    val language: String = "zh-CN",

    @SerializedName("recognition_provider")
    val recognitionProvider: String = "manual_correction",

    @SerializedName("recognition_confidence")
    val recognitionConfidence: Double? = null,

    @SerializedName("duration_seconds")
    val durationSeconds: Double? = null,

    @SerializedName("transcription_model")
    val transcriptionModel: String? = null,
)

/**
 * 本地语音转写与风险分析联合响应。
 */
data class VoiceRiskResponse(
    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("created_at")
    val createdAt: String,

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

    val evidence: List<RiskEvidenceDto> = emptyList(),

    val actions: List<String> = emptyList(),

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

    @SerializedName("source_type")
    val sourceType: String,

    val transcript: String,

    val language: String,

    @SerializedName("recognition_provider")
    val recognitionProvider: String,

    @SerializedName("recognition_confidence")
    val recognitionConfidence: Double? = null,

    @SerializedName("language_probability")
    val languageProbability: Double? = null,

    @SerializedName("duration_seconds")
    val durationSeconds: Double? = null,

    @SerializedName("transcription_model")
    val transcriptionModel: String? = null,

    @SerializedName("audio_size_bytes")
    val audioSizeBytes: Long? = null,
)
