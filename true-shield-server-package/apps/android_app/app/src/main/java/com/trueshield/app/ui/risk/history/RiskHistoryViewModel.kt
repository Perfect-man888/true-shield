package com.trueshield.app.ui.risk.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.RiskHistoryResult
import com.trueshield.app.data.repository.RiskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 风险历史记录 ViewModel。
 */
class RiskHistoryViewModel(
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
            RiskHistoryUiState(),
        )

    val uiState: StateFlow<RiskHistoryUiState> =
        _uiState.asStateFlow()

    /**
     * 第一次进入页面时读取最近一页记录。
     *
     * force 为 false 时，已经存在列表就不重复请求。
     */
    fun loadInitial(
        force: Boolean = false,
    ) {
        val currentState =
            _uiState.value

        if (
            !force &&
            (
                    currentState.isInitialLoading ||
                            currentState.items.isNotEmpty()
                    )
        ) {
            return
        }

        if (currentState.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                isInitialLoading = true,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    riskRepository.getRiskHistory(
                        limit =
                            RISK_HISTORY_PAGE_SIZE,
                        offset = 0,
                    )
            ) {
                is RiskHistoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            items =
                                result.response.items,
                            total =
                                result.response.total,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is RiskHistoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
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
     * 重新读取第一页。
     *
     * 刷新成功后会用服务器最新结果替换当前列表。
     */
    fun refresh() {
        val currentState =
            _uiState.value

        if (currentState.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    riskRepository.getRiskHistory(
                        limit =
                            RISK_HISTORY_PAGE_SIZE,
                        offset = 0,
                    )
            ) {
                is RiskHistoryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            items =
                                result.response.items,
                            total =
                                result.response.total,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is RiskHistoryResult.Error -> {
                    _uiState.update {
                        it.copy(
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

    /**
     * 加载下一页历史记录。
     */
    fun loadMore() {
        val currentState =
            _uiState.value

        if (!currentState.canLoadMore) {
            return
        }

        val nextOffset =
            currentState.items.size

        _uiState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    riskRepository.getRiskHistory(
                        limit =
                            RISK_HISTORY_PAGE_SIZE,
                        offset =
                            nextOffset,
                    )
            ) {
                is RiskHistoryResult.Success -> {
                    _uiState.update {
                        val mergedItems =
                            (
                                    it.items +
                                            result.response.items
                                    )
                                .distinctBy {
                                        item ->
                                    item.eventId
                                }

                        it.copy(
                            items = mergedItems,
                            total =
                                result.response.total,
                            isLoadingMore = false,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is RiskHistoryResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
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
     * 页面加载失败后的重试。
     */
    fun retry() {
        if (_uiState.value.items.isEmpty()) {
            loadInitial(
                force = true,
            )
        } else {
            refresh()
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
}