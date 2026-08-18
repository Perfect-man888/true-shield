package com.trueshield.app.ui.risk.feedback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.model.risk.RiskEventDetailResponse
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackRequest
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackTypeValue
import com.trueshield.app.data.model.risk.feedback.RiskLevelValue
import com.trueshield.app.data.repository.CorrectedRiskReanalysisResult
import com.trueshield.app.data.repository.RiskEventDetailResult
import com.trueshield.app.data.repository.RiskFeedbackRepository
import com.trueshield.app.data.repository.RiskFeedbackResult
import com.trueshield.app.data.repository.RiskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * OCR 反馈与修正后重新分析 ViewModel。
 */
class RiskFeedbackViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val riskRepository =
        RiskRepository(
            riskApi =
                trueShieldApplication
                    .apiClient
                    .riskApi,
        )

    private val feedbackRepository =
        RiskFeedbackRepository(
            riskApi =
                trueShieldApplication
                    .apiClient
                    .riskApi,
        )

    private val _uiState =
        MutableStateFlow(
            RiskFeedbackUiState(),
        )

    val uiState: StateFlow<RiskFeedbackUiState> =
        _uiState.asStateFlow()

    /**
     * 加载风险事件和当前用户已有的反馈。
     */
    fun loadEvent(
        eventId: String,
        force: Boolean = false,
    ) {
        val cleanedEventId =
            eventId.trim()

        if (cleanedEventId.isBlank()) {
            showError(
                message = "风险事件编号不能为空。",
            )
            return
        }

        val currentState =
            _uiState.value

        /*
         * 防止 Compose 重组时重复请求。
         */
        if (
            !force &&
            currentState.eventId ==
            cleanedEventId &&
            (
                    currentState.isInitialLoading ||
                            currentState.eventDetail != null
                    )
        ) {
            return
        }

        _uiState.value =
            RiskFeedbackUiState(
                eventId = cleanedEventId,
                isInitialLoading = true,
            )

        viewModelScope.launch {
            when (
                val detailResult =
                    riskRepository.getEventDetail(
                        eventId = cleanedEventId,
                    )
            ) {
                is RiskEventDetailResult.Success -> {
                    handleLoadedEvent(
                        detail =
                            detailResult.response,
                    )
                }

                is RiskEventDetailResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            errorMessage =
                                detailResult.message,
                            sessionExpired =
                                detailResult.requiresLogin,
                        )
                    }
                }
            }
        }
    }

    /**
     * 事件读取成功后继续读取已有反馈。
     */
    private suspend fun handleLoadedEvent(
        detail: RiskEventDetailResponse,
    ) {

        when (
            val feedbackResult =
                feedbackRepository.getFeedback(
                    eventId = detail.eventId,
                )
        ) {
            is RiskFeedbackResult.Success -> {
                val feedback =
                    feedbackResult.response

                _uiState.update {
                    it.copy(
                        eventId = detail.eventId,
                        isInitialLoading = false,
                        eventDetail = detail,
                        savedFeedback = feedback,
                        feedbackType =
                            feedback.feedbackType,
                        expectedRiskLevel =
                            feedback.expectedRiskLevel,
                        comment =
                            feedback.comment.orEmpty(),
                        /*
 * OCR 字段只适用于图片事件。
 */
                        ocrTextAccurate =
                            if (
                                detail.sourceType
                                    .lowercase() == "image"
                            ) {
                                feedback.ocrTextAccurate
                            } else {
                                null
                            },

                        correctedText =
                            if (
                                detail.sourceType
                                    .lowercase() == "image"
                            ) {
                                feedback.correctedText
                                    ?: detail.sourceText
                            } else {
                                ""
                            },
                        errorMessage = null,
                        sessionExpired = false,
                    )
                }
            }

            is RiskFeedbackResult.Error -> {
                /*
                 * 详情已经读取成功，因此这里的 404
                 * 表示尚未提交反馈，不属于真正错误。
                 */
                if (
                    feedbackResult.statusCode == 404
                ) {
                    _uiState.update {
                        it.copy(
                            eventId =
                                detail.eventId,
                            isInitialLoading = false,
                            eventDetail = detail,
                            savedFeedback = null,
                            feedbackType =
                                RiskFeedbackTypeValue
                                    .ACCURATE,
                            expectedRiskLevel = null,
                            comment = "",
                            ocrTextAccurate = null,

                            correctedText =
                                if (
                                    detail.sourceType
                                        .lowercase() == "image"
                                ) {
                                    detail.sourceText
                                } else {
                                    ""
                                },
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            eventDetail = detail,
                            errorMessage =
                                feedbackResult.message,
                            sessionExpired =
                                feedbackResult
                                    .requiresLogin,
                        )
                    }
                }
            }
        }
    }

    /**
     * 更新风险反馈类型。
     */
    fun onFeedbackTypeChange(
        feedbackType: String,
    ) {
        if (
            feedbackType !in
            SUPPORTED_FEEDBACK_TYPES
        ) {
            return
        }

        val suggestedRiskLevel =
            when (feedbackType) {
                /*
                 * 误报表示系统认为有风险，
                 * 用户认为实际为低风险。
                 */
                RiskFeedbackTypeValue
                    .FALSE_POSITIVE -> {
                    RiskLevelValue.LOW
                }

                /*
                 * 漏报通常说明实际为高风险，
                 * 用户后续仍可手动调整。
                 */
                RiskFeedbackTypeValue
                    .FALSE_NEGATIVE -> {
                    RiskLevelValue.HIGH
                }

                /*
                 * 系统等级偏高时，
                 * 默认建议选择低风险。
                 */
                RiskFeedbackTypeValue
                    .RISK_TOO_HIGH -> {
                    RiskLevelValue.LOW
                }

                /*
                 * 系统等级偏低时，
                 * 默认建议选择高风险。
                 */
                RiskFeedbackTypeValue
                    .RISK_TOO_LOW -> {
                    RiskLevelValue.HIGH
                }

                else -> {
                    null
                }
            }

        _uiState.update {
            it.copy(
                feedbackType =
                    feedbackType,
                expectedRiskLevel =
                    suggestedRiskLevel,
                successMessage = null,
                errorMessage = null,
                reanalysisResult = null,
            )
        }
    }

    /**
     * 更新用户认为正确的风险等级。
     */
    fun onExpectedRiskLevelChange(
        riskLevel: String,
    ) {
        if (
            riskLevel !in
            SUPPORTED_RISK_LEVELS
        ) {
            return
        }

        _uiState.update {
            it.copy(
                expectedRiskLevel =
                    riskLevel,
                successMessage = null,
                errorMessage = null,
                reanalysisResult = null,
            )
        }
    }

    /**
     * 更新 OCR 准确性选择。
     */
    fun onOcrTextAccurateChange(
        isAccurate: Boolean,
    ) {
        if (!_uiState.value.isImageEvent) {
            return
        }
        _uiState.update {
            it.copy(
                ocrTextAccurate =
                    isAccurate,
                successMessage = null,
                errorMessage = null,
                reanalysisResult = null,
            )
        }
    }

    /**
     * 更新人工修正后的 OCR 文字。
     */
    fun onCorrectedTextChange(
        correctedText: String,
    ) {
        if (!_uiState.value.isImageEvent) {
            return
        }
        val limitedText =
            correctedText.take(
                CORRECTED_OCR_TEXT_MAX_LENGTH,
            )

        _uiState.update {
            it.copy(
                correctedText =
                    limitedText,
                successMessage = null,
                errorMessage =
                    if (
                        correctedText.length >
                        CORRECTED_OCR_TEXT_MAX_LENGTH
                    ) {
                        "修正文字最多允许输入 " +
                                "$CORRECTED_OCR_TEXT_MAX_LENGTH 个字符。"
                    } else {
                        null
                    },
                reanalysisResult = null,
            )
        }
    }

    /**
     * 更新补充说明。
     */
    fun onCommentChange(
        comment: String,
    ) {
        val limitedComment =
            comment.take(
                RISK_FEEDBACK_COMMENT_MAX_LENGTH,
            )

        _uiState.update {
            it.copy(
                comment =
                    limitedComment,
                successMessage = null,
                errorMessage =
                    if (
                        comment.length >
                        RISK_FEEDBACK_COMMENT_MAX_LENGTH
                    ) {
                        "反馈说明最多允许输入 " +
                                "$RISK_FEEDBACK_COMMENT_MAX_LENGTH 个字符。"
                    } else {
                        null
                    },
            )
        }
    }

    /**
     * 提交反馈。
     *
     * OCR 不准确时，在反馈保存成功后，
     * 自动使用 corrected_text 重新分析。
     */
    fun submitFeedback() {
        val validationMessage =
            validateBeforeSubmit()

        if (validationMessage != null) {
            showError(
                message =
                    validationMessage,
            )
            return
        }

        if (_uiState.value.isBusy) {
            return
        }

        val currentState =
            _uiState.value

        val isImageEvent =
            currentState.isImageEvent

        val expectedRiskLevel =
            when {
                currentState.feedbackType ==
                        RiskFeedbackTypeValue
                            .FALSE_POSITIVE -> {
                    RiskLevelValue.LOW
                }

                currentState
                    .requiresExpectedRiskLevel -> {
                    currentState
                        .expectedRiskLevel
                }

                else -> {
                    null
                }
            }

        val correctedText =
            if (
                isImageEvent &&
                currentState
                    .ocrTextAccurate == false
            ) {
                currentState
                    .correctedText
                    .trim()
            } else {
                null
            }

        val request =
            RiskFeedbackRequest(
                feedbackType =
                    currentState.feedbackType,
                expectedRiskLevel =
                    expectedRiskLevel,
                comment =
                    currentState
                        .comment
                        .trim()
                        .takeIf {
                            it.isNotBlank()
                        },
                ocrTextAccurate =
                    if (isImageEvent) {
                        currentState
                            .ocrTextAccurate
                    } else {
                        null
                    },
                correctedText =
                    correctedText,
            )

        _uiState.update {
            it.copy(
                isSubmitting = true,
                successMessage = null,
                errorMessage = null,
                reanalysisResult = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    feedbackRepository
                        .upsertFeedback(
                            eventId =
                                currentState
                                    .eventId,
                            request = request,
                        )
            ) {
                is RiskFeedbackResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            savedFeedback =
                                result.response,
                            successMessage =
                                "风险反馈已成功保存。",
                            errorMessage = null,
                        )
                    }

                    /*
                     * OCR 不准确并提供修正文字时，
                     * 自动继续执行重新分析。
                     */
                    if (
                        isImageEvent &&
                        currentState
                            .ocrTextAccurate == false
                    ) {
                        reanalyzeCorrectedInternal()
                    }
                }

                is RiskFeedbackResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage =
                                result.message,
                            sessionExpired =
                                result.requiresLogin,
                        )
                    }
                }
            }
        }
    }

    /**
     * 手动重新执行修正文字分析。
     *
     * 可用于重新分析失败后的重试。
     */
    fun reanalyzeCorrected() {
        if (_uiState.value.isBusy) {
            return
        }

        if (!_uiState.value.isImageEvent) {
            showError(
                message =
                    "只有图片风险事件可以使用 OCR 修正后重新分析。",
            )
            return
        }

        if (
            _uiState.value
                .savedFeedback == null
        ) {
            showError(
                message =
                    "请先提交并保存 OCR 修正反馈。",
            )
            return
        }

        if (
            _uiState.value
                .correctedText
                .isBlank()
        ) {
            showError(
                message =
                    "修正后的 OCR 文字不能为空。",
            )
            return
        }

        viewModelScope.launch {
            reanalyzeCorrectedInternal()
        }
    }

    /**
     * 调用修正文字重新分析接口。
     */
    private suspend fun reanalyzeCorrectedInternal() {
        val currentEventId =
            _uiState.value.eventId

        _uiState.update {
            it.copy(
                isReanalyzing = true,
                errorMessage = null,
                reanalysisResult = null,
                sessionExpired = false,
            )
        }

        when (
            val result =
                feedbackRepository
                    .reanalyzeCorrected(
                        eventId =
                            currentEventId,
                    )
        ) {
            is CorrectedRiskReanalysisResult.Success -> {
                _uiState.update {
                    it.copy(
                        isReanalyzing = false,
                        reanalysisResult =
                            result.response,
                        successMessage =
                            "反馈已保存，" +
                                    "并已根据修正文字重新分析。",
                        errorMessage = null,
                    )
                }
            }

            is CorrectedRiskReanalysisResult.Error -> {
                _uiState.update {
                    it.copy(
                        isReanalyzing = false,
                        /*
                         * 反馈已经保存成功，
                         * 这里只提示重新分析失败。
                         */
                        errorMessage =
                            "反馈已保存，但重新分析失败：" +
                                    result.message,
                        sessionExpired =
                            result.requiresLogin,
                    )
                }
            }
        }
    }

    /**
     * 提交前进行客户端校验。
     */
    private fun validateBeforeSubmit():
            String? {
        val state =
            _uiState.value

        if (state.eventId.isBlank()) {
            return "风险事件编号不能为空。"
        }

        if (state.eventDetail == null) {
            return "风险事件尚未成功加载。"
        }

        if (
            state.feedbackType !in
            SUPPORTED_FEEDBACK_TYPES
        ) {
            return "请选择有效的风险反馈类型。"
        }

        if (
            state.isImageEvent &&
            state.ocrTextAccurate == null
        ) {
            return "请选择 OCR 文字是否准确。"
        }

        if (
            state.requiresExpectedRiskLevel &&
            state.expectedRiskLevel
                .isNullOrBlank()
        ) {
            return "请选择你认为正确的风险等级。"
        }

        if (
            state.feedbackType ==
            RiskFeedbackTypeValue
                .FALSE_NEGATIVE &&
            state.expectedRiskLevel ==
            RiskLevelValue.LOW
        ) {
            return "漏报反馈的正确风险等级不能选择低风险。"
        }

        if (
            state.requiresCorrectedText &&
            state.correctedText
                .isBlank()
        ) {
            return "OCR 识别不准确时，必须填写修正后的完整文字。"
        }

        if (
            state.correctedText.length >
            CORRECTED_OCR_TEXT_MAX_LENGTH
        ) {
            return "修正文字不能超过 " +
                    "$CORRECTED_OCR_TEXT_MAX_LENGTH 个字符。"
        }

        if (
            state.comment.length >
            RISK_FEEDBACK_COMMENT_MAX_LENGTH
        ) {
            return "反馈说明不能超过 " +
                    "$RISK_FEEDBACK_COMMENT_MAX_LENGTH 个字符。"
        }

        return null
    }

    /**
     * 重新加载当前事件。
     */
    fun retry() {
        val eventId =
            _uiState.value.eventId

        loadEvent(
            eventId = eventId,
            force = true,
        )
    }

    /**
     * 清除页面提示信息。
     */
    fun clearMessages() {
        _uiState.update {
            it.copy(
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    /**
     * 标记登录失效事件已经处理。
     */
    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(
                sessionExpired = false,
            )
        }
    }

    private fun showError(
        message: String,
    ) {
        _uiState.update {
            it.copy(
                isInitialLoading = false,
                isSubmitting = false,
                isReanalyzing = false,
                errorMessage = message,
            )
        }
    }

    private companion object {

        val SUPPORTED_FEEDBACK_TYPES =
            setOf(
                RiskFeedbackTypeValue
                    .ACCURATE,
                RiskFeedbackTypeValue
                    .FALSE_POSITIVE,
                RiskFeedbackTypeValue
                    .FALSE_NEGATIVE,
                RiskFeedbackTypeValue
                    .RISK_TOO_HIGH,
                RiskFeedbackTypeValue
                    .RISK_TOO_LOW,
            )

        val SUPPORTED_RISK_LEVELS =
            setOf(
                RiskLevelValue.LOW,
                RiskLevelValue.MEDIUM,
                RiskLevelValue.HIGH,
            )
    }
}