package com.trueshield.app.ui.risk.feedback

import com.trueshield.app.data.model.risk.RiskEventDetailResponse
import com.trueshield.app.data.model.risk.feedback.CorrectedRiskReanalysisResponse
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackResponse
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackTypeValue

/**
 * 后端允许的反馈说明最大长度。
 */
const val RISK_FEEDBACK_COMMENT_MAX_LENGTH =
    1_000

/**
 * 后端允许的 OCR 修正文字最大长度。
 */
const val CORRECTED_OCR_TEXT_MAX_LENGTH =
    10_000

/**
 * OCR 反馈与重新分析页面状态。
 */
data class RiskFeedbackUiState(

    /**
     * 当前操作的风险事件编号。
     */
    val eventId: String = "",

    /**
     * 是否正在加载事件和已有反馈。
     */
    val isInitialLoading: Boolean = false,

    /**
     * 是否正在提交反馈。
     */
    val isSubmitting: Boolean = false,

    /**
     * 是否正在根据修正文字重新分析。
     */
    val isReanalyzing: Boolean = false,

    /**
     * 风险事件完整详情。
     *
     * 用于显示原始 OCR 文字和事件信息。
     */
    val eventDetail:
    RiskEventDetailResponse? = null,

    /**
     * 已经保存到后端的反馈。
     */
    val savedFeedback:
    RiskFeedbackResponse? = null,

    /**
     * 用户选择的风险判断反馈类型。
     */
    val feedbackType: String =
        RiskFeedbackTypeValue.ACCURATE,

    /**
     * 用户认为正确的风险等级。
     *
     * 某些反馈类型必须提供该字段。
     */
    val expectedRiskLevel: String? = null,

    /**
     * 用户填写的补充说明。
     */
    val comment: String = "",

    /**
     * 用户是否确认 OCR 文字准确。
     *
     * null 表示尚未选择；
     * true 表示准确；
     * false 表示不准确。
     */
    val ocrTextAccurate: Boolean? = null,

    /**
     * 用户人工修正后的完整 OCR 文字。
     */
    val correctedText: String = "",

    /**
     * 修正前后风险分析对比结果。
     */
    val reanalysisResult:
    CorrectedRiskReanalysisResponse? = null,

    /**
     * 操作成功提示。
     */
    val successMessage: String? = null,

    /**
     * 操作失败提示。
     */
    val errorMessage: String? = null,

    /**
     * Token 是否已经过期。
     */
    val sessionExpired: Boolean = false,
) {

    /**
     * 页面当前是否正在执行任务。
     */
    val isBusy: Boolean
        get() =
            isInitialLoading ||
                    isSubmitting ||
                    isReanalyzing


    /**
     * 当前是否为图片风险事件。
     */
    val isImageEvent: Boolean
        get() =
            eventDetail
                ?.sourceType
                ?.lowercase() == "image"

    /**
     * 当前反馈类型是否要求用户选择正确风险等级。
     */
    val requiresExpectedRiskLevel: Boolean
        get() =
            feedbackType ==
                    RiskFeedbackTypeValue.FALSE_NEGATIVE ||
                    feedbackType ==
                    RiskFeedbackTypeValue.RISK_TOO_HIGH ||
                    feedbackType ==
                    RiskFeedbackTypeValue.RISK_TOO_LOW

    /**
     * 当前是否需要填写 OCR 修正文字。
     */
    /**
     * 只有图片事件且 OCR 被标记为不准确时，
     * 才必须填写修正文字。
     */
    val requiresCorrectedText: Boolean
        get() =
            isImageEvent &&
                    ocrTextAccurate == false

    /**
     * 当前是否可以提交反馈。
     */
    /**
     * 当前是否允许提交反馈。
     */
    val canSubmit: Boolean
        get() {
            if (isBusy) {
                return false
            }

            /*
             * 必须先成功加载风险事件。
             */
            if (eventDetail == null) {
                return false
            }

            /*
             * 图片事件必须明确选择 OCR 是否准确。
             * 文本和 URL 事件不需要该字段。
             */
            if (
                isImageEvent &&
                ocrTextAccurate == null
            ) {
                return false
            }

            if (
                requiresExpectedRiskLevel &&
                expectedRiskLevel.isNullOrBlank()
            ) {
                return false
            }

            if (
                requiresCorrectedText &&
                correctedText.isBlank()
            ) {
                return false
            }

            return true
        }
}