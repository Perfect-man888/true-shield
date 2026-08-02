package com.trueshield.app.ui.risk.statistics

import com.trueshield.app.data.model.risk.feedback.RiskFeedbackStatisticsResponse

/**
 * 风险反馈统计页面状态。
 */
data class RiskFeedbackStatisticsUiState(
    val statistics: RiskFeedbackStatisticsResponse? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val isBusy: Boolean
        get() = isLoading || isRefreshing
}
