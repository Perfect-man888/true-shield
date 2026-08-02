package com.trueshield.app.data.model.risk.url

import com.google.gson.annotations.SerializedName

/**
 * URL 风险检测请求。
 *
 * 对应后端：
 * URLRiskAnalysisRequest
 */
data class UrlRiskRequest(
    /**
     * 需要检测的网址。
     */
    val url: String,

    /**
     * 是否由后端实际访问该 URL，
     * 并安全解析重定向链。
     *
     * 默认关闭。
     */
    @SerializedName("resolve_redirects")
    val resolveRedirects: Boolean = false,
)