package com.trueshield.app.data.model.risk.url

import com.google.gson.annotations.SerializedName

/**
 * URL 重定向链中的一次请求结果。
 */
data class UrlRedirectHopDto(
    /**
     * 当前节点在重定向链中的序号。
     */
    val index: Int,

    /**
     * 当前实际访问的 URL。
     */
    val url: String,

    /**
     * 当前 URL 的主机名。
     */
    val host: String,

    /**
     * HTTP 响应状态码。
     */
    @SerializedName("status_code")
    val statusCode: Int,

    /**
     * 响应中的 Location 地址。
     *
     * 最终页面通常为 null。
     */
    val location: String? = null,
)

/**
 * URL 重定向解析成功结果。
 */
data class UrlRedirectResolutionDto(
    @SerializedName("original_url")
    val originalUrl: String,

    @SerializedName("final_url")
    val finalUrl: String,

    @SerializedName("final_status_code")
    val finalStatusCode: Int,

    @SerializedName("redirect_count")
    val redirectCount: Int,

    val completed: Boolean,

    val hops: List<UrlRedirectHopDto> =
        emptyList(),
)

/**
 * URL 外部访问和重定向检查状态。
 */
data class UrlRedirectInspectionDto(
    /**
     * 用户是否要求检查重定向。
     */
    val requested: Boolean,

    /**
     * 可能的值：
     *
     * not_requested
     * completed
     * blocked
     * failed
     */
    val status: String,

    /**
     * 检查成功时的重定向结果。
     */
    val resolution:
    UrlRedirectResolutionDto? = null,

    /**
     * 被阻止或检查失败时的原因。
     */
    val message: String? = null,
)