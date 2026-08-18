package com.trueshield.app.ui.risk.text

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.RiskRepository
import com.trueshield.app.data.repository.TextRiskAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 文本风险检测页面 ViewModel。
 */
class TextRiskViewModel(
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

    private val _uiState =
        MutableStateFlow(
            TextRiskUiState(),
        )

    val uiState: StateFlow<TextRiskUiState> =
        _uiState.asStateFlow()

    /**
     * 更新用户输入的文本。
     */
    fun onTextChange(
        newText: String,
    ) {
        val limitedText =
            newText.take(
                TEXT_RISK_MAX_LENGTH,
            )

        _uiState.update {
            it.copy(
                text = limitedText,
                result = null,
                errorMessage =
                    if (
                        newText.length >
                        TEXT_RISK_MAX_LENGTH
                    ) {
                        "文本最多允许输入 " +
                                "$TEXT_RISK_MAX_LENGTH 个字符。"
                    } else {
                        null
                    },
                sessionExpired = false,
            )
        }
    }

    /**
     * 提交文本风险检测。
     */
    fun analyzeText() {
        val text =
            _uiState.value
                .text
                .trim()

        if (text.isBlank()) {
            showError(
                message = "请输入或粘贴需要检测的聊天内容。",
            )
            return
        }

        if (
            text.length >
            TEXT_RISK_MAX_LENGTH
        ) {
            showError(
                message =
                    "文本不能超过 " +
                            "$TEXT_RISK_MAX_LENGTH 个字符。",
            )
            return
        }

        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                result = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    riskRepository.analyzeText(
                        text = text,
                    )
            ) {
                is TextRiskAnalysisResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            result =
                                result.response,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is TextRiskAnalysisResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            result = null,
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
     * 清除当前检测结果。
     */
    fun clearResult() {
        _uiState.update {
            it.copy(
                result = null,
                errorMessage = null,
            )
        }
    }

    /**
     * 清空输入内容和检测结果。
     */
    fun clearAll() {
        _uiState.value =
            TextRiskUiState()
    }

    /**
     * 标记登录过期事件已经处理。
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
                isLoading = false,
                errorMessage = message,
            )
        }
    }
}