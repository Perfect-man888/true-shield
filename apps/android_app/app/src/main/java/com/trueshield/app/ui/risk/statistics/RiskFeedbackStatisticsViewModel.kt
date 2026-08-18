package com.trueshield.app.ui.risk.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.RiskFeedbackRepository
import com.trueshield.app.data.repository.RiskFeedbackStatisticsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 风险反馈统计 ViewModel。
 */
class RiskFeedbackStatisticsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val repository =
        RiskFeedbackRepository(
            riskApi =
                trueShieldApplication
                    .apiClient
                    .riskApi,
        )

    private val _uiState =
        MutableStateFlow(
            RiskFeedbackStatisticsUiState(),
        )

    val uiState:
            StateFlow<RiskFeedbackStatisticsUiState> =
        _uiState.asStateFlow()

    /**
     * 第一次进入页面时读取统计数据。
     */
    fun loadStatistics(
        force: Boolean = false,
    ) {
        val currentState =
            _uiState.value

        if (currentState.isBusy) {
            return
        }

        if (
            !force &&
            currentState.statistics != null
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        requestStatistics()
    }

    /**
     * 主动刷新统计数据。
     */
    fun refresh() {
        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        requestStatistics()
    }

    /**
     * 页面加载失败后的重试。
     */
    fun retry() {
        loadStatistics(
            force = true,
        )
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

    private fun requestStatistics() {
        viewModelScope.launch {
            when (
                val result =
                    repository.getStatistics()
            ) {
                is RiskFeedbackStatisticsResult.Success -> {
                    _uiState.update {
                        it.copy(
                            statistics =
                                result.response,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is RiskFeedbackStatisticsResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
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
}
