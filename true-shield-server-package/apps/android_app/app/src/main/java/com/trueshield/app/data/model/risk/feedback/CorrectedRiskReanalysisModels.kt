package com.trueshield.app.data.model.risk.feedback

import com.google.gson.annotations.SerializedName
import com.trueshield.app.data.model.risk.RiskEvidenceDto

/**
 * 一次风险分析结果快照。
 *
 * 修正后重新分析接口会同时返回：
 * 1. 原始 OCR 文本分析结果
 * 2. 人工修正文本分析结果
 */
data class RiskReanalysisSnapshotDto(
    @SerializedName("risk_level")
    val riskLevel: String,

    val score: Int,

    val summary: String,

    val evidence: List<RiskEvidenceDto> =
        emptyList(),

    val actions: List<String> =
        emptyList(),
)

/**
 * 原始结果和修正结果的差异。
 */
data class RiskAnalysisComparisonDto(
    @SerializedName("original_risk_level")
    val originalRiskLevel: String,

    @SerializedName("corrected_risk_level")
    val correctedRiskLevel: String,

    @SerializedName("original_score")
    val originalScore: Int,

    @SerializedName("corrected_score")
    val correctedScore: Int,

    @SerializedName("score_delta")
    val scoreDelta: Int,

    @SerializedName("risk_level_changed")
    val riskLevelChanged: Boolean,

    @SerializedName("added_rule_ids")
    val addedRuleIds: List<String> =
        emptyList(),

    @SerializedName("removed_rule_ids")
    val removedRuleIds: List<String> =
        emptyList(),

    @SerializedName("retained_rule_ids")
    val retainedRuleIds: List<String> =
        emptyList(),
)

/**
 * 使用人工修正 OCR 文本重新分析的响应。
 */
data class CorrectedRiskReanalysisResponse(
    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("source_type")
    val sourceType: String,

    @SerializedName("original_text")
    val originalText: String,

    @SerializedName("corrected_text")
    val correctedText: String,

    @SerializedName("original_analysis")
    val originalAnalysis:
    RiskReanalysisSnapshotDto,

    @SerializedName("corrected_analysis")
    val correctedAnalysis:
    RiskReanalysisSnapshotDto,

    val comparison:
    RiskAnalysisComparisonDto,
)