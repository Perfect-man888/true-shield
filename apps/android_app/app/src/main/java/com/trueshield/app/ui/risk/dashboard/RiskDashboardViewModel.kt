package com.trueshield.app.ui.risk.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.FamilyOperationResult
import com.trueshield.app.data.repository.FamilyRepository
import com.trueshield.app.data.repository.RiskDashboardRepository
import com.trueshield.app.data.repository.RiskDashboardResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 风险统计仪表盘 ViewModel。
 */
class RiskDashboardViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val dashboardRepository =
        RiskDashboardRepository(
            riskApi =
                trueShieldApplication
                    .apiClient
                    .riskApi,
        )

    private val familyRepository =
        FamilyRepository(
            familyApi =
                trueShieldApplication
                    .apiClient
                    .familyApi,
        )

    private val _uiState =
        MutableStateFlow(
            RiskDashboardUiState(),
        )

    val uiState: StateFlow<RiskDashboardUiState> =
        _uiState.asStateFlow()

    /**
     * 第一次进入页面时读取家庭列表和个人统计。
     */
    fun loadInitial(
        force: Boolean = false,
    ) {
        val currentState = _uiState.value

        if (currentState.isBusy) {
            return
        }

        if (
            !force &&
            currentState.dashboard != null
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                familyMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            loadFamilies()

            if (!_uiState.value.sessionExpired) {
                requestDashboard()
            }
        }
    }

    /**
     * 切换到当前用户自己的统计。
     */
    fun selectPersonalScope() {
        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                scope =
                    RiskDashboardScopeSelection
                        .PERSONAL,
                selectedFamilyId = null,
                errorMessage = null,
            )
        }

        reloadForSelection()
    }

    /**
     * 切换到指定家庭统计。
     */
    fun selectFamily(
        familyId: String,
    ) {
        val cleanedFamilyId = familyId.trim()
        val currentState = _uiState.value

        if (
            currentState.isBusy ||
            cleanedFamilyId.isBlank() ||
            currentState.families.none {
                it.id == cleanedFamilyId
            }
        ) {
            return
        }

        _uiState.update {
            it.copy(
                scope =
                    RiskDashboardScopeSelection
                        .FAMILY,
                selectedFamilyId = cleanedFamilyId,
                errorMessage = null,
            )
        }

        reloadForSelection()
    }

    /**
     * 切换近期趋势周期。
     */
    fun selectPeriodDays(
        periodDays: Int,
    ) {
        val safePeriodDays =
            if (periodDays == 30) 30 else 7

        if (
            _uiState.value.isBusy ||
            _uiState.value.selectedPeriodDays ==
            safePeriodDays
        ) {
            return
        }

        _uiState.update {
            it.copy(
                selectedPeriodDays =
                    safePeriodDays,
                errorMessage = null,
            )
        }

        reloadForSelection()
    }

    /**
     * 重新读取家庭与统计数据。
     */
    fun refresh() {
        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                familyMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            loadFamilies()

            if (!_uiState.value.sessionExpired) {
                requestDashboard()
            }
        }
    }

    fun retry() {
        loadInitial(
            force = true,
        )
    }

    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(
                sessionExpired = false,
            )
        }
    }

    private fun reloadForSelection() {
        _uiState.update {
            it.copy(
                dashboard = null,
                isLoading = true,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            requestDashboard()
        }
    }

    private suspend fun loadFamilies() {
        when (
            val result =
                familyRepository.getFamilies()
        ) {
            is FamilyOperationResult.Success -> {
                val families =
                    result.data.items

                _uiState.update { current ->
                    val selectedStillExists =
                        families.any {
                            it.id ==
                                    current
                                        .selectedFamilyId
                        }

                    current.copy(
                        families = families,
                        scope =
                            if (
                                current.scope ==
                                RiskDashboardScopeSelection
                                    .FAMILY &&
                                !selectedStillExists
                            ) {
                                RiskDashboardScopeSelection
                                    .PERSONAL
                            } else {
                                current.scope
                            },
                        selectedFamilyId =
                            current.selectedFamilyId
                                .takeIf {
                                    selectedStillExists
                                },
                        familyMessage =
                            if (families.isEmpty()) {
                                "你尚未加入家庭，" +
                                        "当前只显示个人统计。"
                            } else {
                                null
                            },
                    )
                }
            }

            is FamilyOperationResult.Error -> {
                _uiState.update {
                    it.copy(
                        familyMessage =
                            result.message,
                        sessionExpired =
                            result.requiresLogin,
                        isLoading =
                            if (result.requiresLogin) {
                                false
                            } else {
                                it.isLoading
                            },
                        isRefreshing =
                            if (result.requiresLogin) {
                                false
                            } else {
                                it.isRefreshing
                            },
                    )
                }
            }
        }
    }

    private suspend fun requestDashboard() {
        val currentState = _uiState.value
        val familyId =
            if (
                currentState.scope ==
                RiskDashboardScopeSelection.FAMILY
            ) {
                currentState.selectedFamilyId
            } else {
                null
            }

        when (
            val result =
                dashboardRepository.getDashboard(
                    periodDays =
                        currentState
                            .selectedPeriodDays,
                    familyId = familyId,
                )
        ) {
            is RiskDashboardResult.Success -> {
                _uiState.update {
                    it.copy(
                        dashboard = result.response,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                        sessionExpired = false,
                    )
                }
            }

            is RiskDashboardResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message,
                        sessionExpired =
                            result.requiresLogin,
                    )
                }
            }
        }
    }
}
