package com.trueshield.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.FamilyOperationResult
import com.trueshield.app.data.repository.FamilyRepository
import com.trueshield.app.data.repository.RiskDashboardRepository
import com.trueshield.app.data.repository.RiskDashboardResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MessageCenterViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val familyRepository =
        FamilyRepository(
            familyApi = trueShieldApplication.apiClient.familyApi,
        )

    private val dashboardRepository =
        RiskDashboardRepository(
            riskApi = trueShieldApplication.apiClient.riskApi,
        )

    private val _uiState =
        MutableStateFlow(MessageCenterUiState())

    val uiState: StateFlow<MessageCenterUiState> =
        _uiState.asStateFlow()

    fun refresh() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            var familyCount = 0
            var pendingAlertCount = 0
            var acknowledgedAlertCount = 0
            var invitationCount = 0
            var recentRiskCount = 0
            var recentHighRiskCount = 0
            var errorMessage: String? = null
            var sessionExpired = false

            val familyResult = familyRepository.getFamilies()
            when (familyResult) {
                is FamilyOperationResult.Success -> {
                    val families = familyResult.data.items
                    familyCount = families.size

                    val alertCounts = families.map { family ->
                        async {
                            when (
                                val dashboard = dashboardRepository.getDashboard(
                                    periodDays = 7,
                                    familyId = family.id,
                                )
                            ) {
                                is RiskDashboardResult.Success -> {
                                    dashboard.response.alertStatus.pending to
                                        dashboard.response.alertStatus.acknowledged
                                }

                                is RiskDashboardResult.Error -> {
                                    0 to 0
                                }
                            }
                        }
                    }.awaitAll()

                    pendingAlertCount = alertCounts.sumOf { it.first }
                    acknowledgedAlertCount = alertCounts.sumOf { it.second }
                }

                is FamilyOperationResult.Error -> {
                    errorMessage = familyResult.message
                    sessionExpired = familyResult.requiresLogin
                }
            }

            if (!sessionExpired) {
                when (val invitations = familyRepository.getReceivedInvitations()) {
                    is FamilyOperationResult.Success -> {
                        invitationCount = invitations.data.total
                    }

                    is FamilyOperationResult.Error -> {
                        errorMessage = errorMessage ?: invitations.message
                        sessionExpired = invitations.requiresLogin
                    }
                }
            }

            if (!sessionExpired) {
                when (
                    val dashboard = dashboardRepository.getDashboard(
                        periodDays = 7,
                        familyId = null,
                    )
                ) {
                    is RiskDashboardResult.Success -> {
                        recentRiskCount = dashboard.response.overview.periodEvents
                        recentHighRiskCount = dashboard.response.riskLevels.high
                    }

                    is RiskDashboardResult.Error -> {
                        errorMessage = errorMessage ?: dashboard.message
                        sessionExpired = dashboard.requiresLogin
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    initialized = true,
                    familyCount = familyCount,
                    pendingAlertCount = pendingAlertCount,
                    acknowledgedAlertCount = acknowledgedAlertCount,
                    invitationCount = invitationCount,
                    recentRiskCount = recentRiskCount,
                    recentHighRiskCount = recentHighRiskCount,
                    errorMessage = errorMessage,
                    sessionExpired = sessionExpired,
                )
            }
        }
    }

    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(sessionExpired = false)
        }
    }
}
