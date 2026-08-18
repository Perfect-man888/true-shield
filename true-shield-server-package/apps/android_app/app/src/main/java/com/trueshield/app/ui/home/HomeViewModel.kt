package com.trueshield.app.ui.home

import android.app.Application
import android.app.role.RoleManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.AccountOperationResult
import com.trueshield.app.data.repository.AccountRepository
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

class HomeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val accountRepository =
        AccountRepository(
            authApi = trueShieldApplication.apiClient.authApi,
            userApi = trueShieldApplication.apiClient.userApi,
        )

    private val dashboardRepository =
        RiskDashboardRepository(
            riskApi = trueShieldApplication.apiClient.riskApi,
        )

    private val familyRepository =
        FamilyRepository(
            familyApi = trueShieldApplication.apiClient.familyApi,
        )

    private val _uiState =
        MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> =
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
                callGuardEnabled = readCallGuardStatus(),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            var displayName = _uiState.value.displayName
            var todayCount = _uiState.value.todayDetectionCount
            var pendingAlertCount = _uiState.value.pendingAlertCount
            var familyCount = _uiState.value.familyCount
            var message: String? = null
            var sessionExpired = false

            when (val account = accountRepository.getCurrentUser()) {
                is AccountOperationResult.Success -> {
                    displayName = account.data.displayName
                        .trim()
                        .takeUnless {
                            it.isBlank() ||
                                it.equals("string", ignoreCase = true)
                        }
                        ?: "未设置昵称"
                }

                is AccountOperationResult.Error -> {
                    message = account.message
                    sessionExpired = account.requiresLogin
                }
            }

            if (!sessionExpired) {
                when (
                    val dashboard =
                        dashboardRepository.getDashboard(
                            periodDays = 1,
                            familyId = null,
                        )
                ) {
                    is RiskDashboardResult.Success -> {
                        todayCount =
                            dashboard.response.overview.periodEvents
                    }

                    is RiskDashboardResult.Error -> {
                        message = message ?: dashboard.message
                        sessionExpired = dashboard.requiresLogin
                    }
                }
            }

            if (!sessionExpired) {
                when (val families = familyRepository.getFamilies()) {
                    is FamilyOperationResult.Success -> {
                        familyCount = families.data.items.size

                        val counts = families.data.items.map { family ->
                            async {
                                when (
                                    val result =
                                        dashboardRepository.getDashboard(
                                            periodDays = 7,
                                            familyId = family.id,
                                        )
                                ) {
                                    is RiskDashboardResult.Success ->
                                        result.response.alertStatus.pending

                                    is RiskDashboardResult.Error -> 0
                                }
                            }
                        }.awaitAll()

                        pendingAlertCount = counts.sum()
                    }

                    is FamilyOperationResult.Error -> {
                        message = message ?: families.message
                        sessionExpired = families.requiresLogin
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    initialized = true,
                    displayName = displayName,
                    callGuardEnabled = readCallGuardStatus(),
                    todayDetectionCount = todayCount,
                    pendingAlertCount = pendingAlertCount,
                    familyCount = familyCount,
                    errorMessage = message,
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

    private fun readCallGuardStatus(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        val roleManager =
            getApplication<Application>()
                .getSystemService(RoleManager::class.java)

        return roleManager?.isRoleAvailable(
            RoleManager.ROLE_CALL_SCREENING,
        ) == true && roleManager.isRoleHeld(
            RoleManager.ROLE_CALL_SCREENING,
        )
    }
}
