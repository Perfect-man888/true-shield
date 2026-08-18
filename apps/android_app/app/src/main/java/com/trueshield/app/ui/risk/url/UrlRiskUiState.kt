package com.trueshield.app.ui.risk.url

import com.trueshield.app.data.model.risk.url.UrlRiskResponse

/**
 * 与后端 URLRiskAnalysisRequest 保持一致。
 */
const val URL_RISK_MAX_LENGTH =
    2_048

/**
 * URL 风险检测页面状态。
 */
data class UrlRiskUiState(

    /**
     * 用户输入的网址。
     */
    val url: String = "",

    /**
     * 是否要求后端实际访问链接并解析重定向。
     */
    val resolveRedirects: Boolean = false,

    /**
     * 是否正在请求后端。
     */
    val isLoading: Boolean = false,

    /**
     * URL 风险检测结果。
     */
    val result: UrlRiskResponse? = null,

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
     * 当前是否允许提交。
     */
    val canSubmit: Boolean
        get() =
            url.isNotBlank() &&
                    !isLoading
}