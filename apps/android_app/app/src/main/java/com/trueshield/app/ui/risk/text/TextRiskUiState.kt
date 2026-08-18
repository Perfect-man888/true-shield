package com.trueshield.app.ui.risk.text

import com.trueshield.app.data.model.risk.TextRiskResponse

/**
 * 后端允许提交的最大文本长度。
 */
const val TEXT_RISK_MAX_LENGTH = 10_000

/**
 * 文本风险检测页面状态。
 */
data class TextRiskUiState(
    /**
     * 用户输入或粘贴的聊天内容。
     */
    val text: String = "",

    /**
     * 是否正在请求后端。
     */
    val isLoading: Boolean = false,

    /**
     * 成功返回的风险检测结果。
     */
    val result: TextRiskResponse? = null,

    /**
     * 页面错误提示。
     */
    val errorMessage: String? = null,

    /**
     * 是否因为 Token 失效而需要重新登录。
     */
    val sessionExpired: Boolean = false,
) {
    /**
     * 当前是否可以提交检测。
     */
    val canSubmit: Boolean
        get() =
            text.isNotBlank() &&
                    !isLoading
}