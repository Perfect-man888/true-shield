package com.trueshield.app.data.model.risk

import com.google.gson.annotations.SerializedName

/**
 * 后端返回的单条风险证据。
 */
data class RiskEvidenceDto(
    @SerializedName("rule_id")
    val ruleId: String,

    val category: String,

    val title: String,

    @SerializedName("matched_terms")
    val matchedTerms: List<String> =
        emptyList(),

    val score: Int,

    val explanation: String,

    val tags: List<String> =
        emptyList(),

    val matches: List<RiskTermMatchDto> =
        emptyList(),
)

/**
 * 风险词在原始文本中的命中位置。
 *
 * start 包含该位置，end 不包含该位置。
 */
data class RiskTermMatchDto(
    val term: String,

    val start: Int,

    val end: Int,
)