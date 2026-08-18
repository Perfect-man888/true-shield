package com.trueshield.app.data.model.risk.feedback

import com.google.gson.annotations.SerializedName

/**
 * 后端支持的风险反馈类型。
 */
object RiskFeedbackTypeValue {

    /**
     * 系统判断准确。
     */
    const val ACCURATE =
        "accurate"

    /**
     * 系统判断为有风险，但用户认为没有风险。
     */
    const val FALSE_POSITIVE =
        "false_positive"

    /**
     * 系统未发现风险，但用户认为实际存在风险。
     */
    const val FALSE_NEGATIVE =
        "false_negative"

    /**
     * 系统风险等级偏高。
     */
    const val RISK_TOO_HIGH =
        "risk_too_high"

    /**
     * 系统风险等级偏低。
     */
    const val RISK_TOO_LOW =
        "risk_too_low"
}

/**
 * 后端支持的风险等级值。
 */
object RiskLevelValue {
    const val LOW = "low"
    const val MEDIUM = "medium"
    const val HIGH = "high"
}

/**
 * 创建或更新风险反馈请求。
 *
 * 对应后端：
 * RiskFeedbackCreate
 */
data class RiskFeedbackRequest(
    @SerializedName("feedback_type")
    val feedbackType: String,

    @SerializedName("expected_risk_level")
    val expectedRiskLevel: String? = null,

    val comment: String? = null,

    @SerializedName("ocr_text_accurate")
    val ocrTextAccurate: Boolean? = null,

    @SerializedName("corrected_text")
    val correctedText: String? = null,
)

/**
 * 风险反馈响应。
 *
 * 同一用户对同一风险事件重复提交时，
 * 后端会更新原反馈，而不是创建重复反馈。
 */
data class RiskFeedbackResponse(
    val id: String,

    @SerializedName("event_id")
    val eventId: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("feedback_type")
    val feedbackType: String,

    @SerializedName("expected_risk_level")
    val expectedRiskLevel: String? = null,

    val comment: String? = null,

    @SerializedName("ocr_text_accurate")
    val ocrTextAccurate: Boolean? = null,

    @SerializedName("corrected_text")
    val correctedText: String? = null,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,
)