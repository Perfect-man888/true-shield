package com.trueshield.app.data.model.risk

import com.google.gson.annotations.SerializedName

/**
 * 文本风险分析请求。
 *
 * 对应后端：
 * TextRiskAnalysisRequest
 */
data class TextRiskRequest(
    val text: String,

    val context: TextRiskContext =
        TextRiskContext(),
)

/**
 * 文本风险分析时可选的上下文。
 *
 * 第一版页面可以暂时只提交 text，
 * 其他字段保持 null。
 */
data class TextRiskContext(
    @SerializedName("sender_is_known")
    val senderIsKnown: Boolean? = null,

    @SerializedName("claimed_identity")
    val claimedIdentity: String? = null,

    val amount: Double? = null,
)