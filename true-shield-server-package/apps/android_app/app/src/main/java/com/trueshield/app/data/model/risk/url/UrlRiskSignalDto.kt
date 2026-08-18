package com.trueshield.app.data.model.risk.url

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * 单项 URL 风险信号。
 */
data class UrlRiskSignalDto(
    /**
     * 风险信号编号，例如 URL-002。
     */
    @SerializedName("signal_id")
    val signalId: String,

    /**
     * 风险类别。
     */
    val category: String,

    /**
     * 风险标题。
     */
    val title: String,

    /**
     * 当前信号贡献的风险分数。
     */
    val score: Int,

    /**
     * 风险解释。
     */
    val explanation: String,

    /**
     * 当前信号的动态附加信息。
     *
     * 不同 URL 规则的 details 结构不同，
     * 因此使用 JsonElement 接收。
     */
    val details: JsonElement? = null,
)