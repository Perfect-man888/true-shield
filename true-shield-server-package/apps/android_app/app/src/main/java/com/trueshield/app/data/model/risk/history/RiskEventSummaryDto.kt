package com.trueshield.app.data.model.risk.history

import com.google.gson.annotations.SerializedName

/**
 * 历史记录列表中的单条风险事件摘要。
 *
 * 对应后端：
 * GET /api/v1/risk/events
 * 返回结果中的 items 元素。
 */
data class RiskEventSummaryDto(
    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("source_type")
    val sourceType: String,

    @SerializedName("risk_level")
    val riskLevel: String,

    val score: Int,

    val summary: String,

    @SerializedName("evidence_count")
    val evidenceCount: Int,

    @SerializedName("created_at")
    val createdAt: String,
)