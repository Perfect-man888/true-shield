package com.trueshield.app.ui.risk.url

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.RiskRepository
import com.trueshield.app.data.repository.UrlRiskAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * URL 链接风险检测 ViewModel。
 */
class UrlRiskViewModel(
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
            UrlRiskUiState(),
        )

    val uiState: StateFlow<UrlRiskUiState> =
        _uiState.asStateFlow()

    /**
     * 更新用户输入的网址。
     */
    fun onUrlChange(
        newUrl: String,
    ) {
        val limitedUrl =
            newUrl.take(
                URL_RISK_MAX_LENGTH,
            )

        _uiState.update {
            it.copy(
                url = limitedUrl,
                result = null,
                errorMessage =
                    if (
                        newUrl.length >
                        URL_RISK_MAX_LENGTH
                    ) {
                        "网址最多允许输入 " +
                                "$URL_RISK_MAX_LENGTH 个字符。"
                    } else {
                        null
                    },
                sessionExpired = false,
            )
        }
    }

    /**
     * 切换是否解析重定向。
     */
    fun onResolveRedirectsChange(
        enabled: Boolean,
    ) {
        _uiState.update {
            it.copy(
                resolveRedirects = enabled,
                result = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }
    }

    /**
     * 提交 URL 风险检测。
     */
    fun analyzeUrl() {
        val currentState =
            _uiState.value

        val cleanedUrl =
            currentState.url.trim()

        if (cleanedUrl.isBlank()) {
            showError(
                message =
                    "请输入或粘贴需要检测的网址。",
            )
            return
        }

        if (
            cleanedUrl.length >
            URL_RISK_MAX_LENGTH
        ) {
            showError(
                message =
                    "网址不能超过 " +
                            "$URL_RISK_MAX_LENGTH 个字符。",
            )
            return
        }

        if (currentState.isLoading) {
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
                    riskRepository.analyzeUrl(
                        url = cleanedUrl,
                        resolveRedirects =
                            currentState
                                .resolveRedirects,
                    )
            ) {
                is UrlRiskAnalysisResult.Success -> {
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

                is UrlRiskAnalysisResult.Error -> {
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
     * 关闭结果，但保留输入网址。
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
     * 清空输入和结果。
     */
    fun clearAll() {
        _uiState.value =
            UrlRiskUiState()
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