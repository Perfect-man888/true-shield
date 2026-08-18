package com.trueshield.app.data.model.risk.history

/**
 * 风险历史记录分页响应。
 *
 * 后端使用 limit 和 offset 分页，
 * 而不是 page 和 page_size。
 */
data class RiskEventListResponse(
    /**
     * 当前返回的风险事件。
     */
    val items: List<RiskEventSummaryDto> =
        emptyList(),

    /**
     * 当前用户全部风险事件数量。
     */
    val total: Int,

    /**
     * 本次请求的最大返回数量。
     */
    val limit: Int,

    /**
     * 本次请求跳过的记录数量。
     */
    val offset: Int,
)