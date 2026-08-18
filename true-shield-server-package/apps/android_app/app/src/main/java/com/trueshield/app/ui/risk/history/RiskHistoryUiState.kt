package com.trueshield.app.ui.risk.history

import com.trueshield.app.data.model.risk.history.RiskEventSummaryDto

/**
 * 历史记录默认每次读取 20 条。
 */
const val RISK_HISTORY_PAGE_SIZE =
    20

/**
 * 风险历史记录页面状态。
 */
data class RiskHistoryUiState(

    /**
     * 当前已经加载的历史记录。
     */
    val items: List<RiskEventSummaryDto> =
        emptyList(),

    /**
     * 当前账户全部风险事件数量。
     */
    val total: Int = 0,

    /**
     * 第一次进入页面时是否正在加载。
     */
    val isInitialLoading: Boolean = false,

    /**
     * 是否正在刷新第一页。
     */
    val isRefreshing: Boolean = false,

    /**
     * 是否正在加载下一页。
     */
    val isLoadingMore: Boolean = false,

    /**
     * 页面错误提示。
     */
    val errorMessage: String? = null,

    /**
     * Token 是否已经失效。
     */
    val sessionExpired: Boolean = false,
) {

    /**
     * 当前是否正在执行任何网络请求。
     */
    val isBusy: Boolean
        get() =
            isInitialLoading ||
                    isRefreshing ||
                    isLoadingMore

    /**
     * 当前是否还有下一页。
     */
    val canLoadMore: Boolean
        get() =
            items.size < total &&
                    !isBusy
}