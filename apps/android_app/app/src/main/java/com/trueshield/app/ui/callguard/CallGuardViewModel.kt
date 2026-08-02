package com.trueshield.app.ui.callguard

import android.app.Application
import android.app.role.RoleManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.callguard.CallGuardStore
import com.trueshield.app.data.model.callguard.CallGuardHelpRequest
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeRequest
import com.trueshield.app.data.repository.CallGuardOperationResult
import com.trueshield.app.data.repository.CallGuardRepository
import com.trueshield.app.data.repository.FamilyOperationResult
import com.trueshield.app.data.repository.FamilyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 通话安全护航 ViewModel。
 */
class CallGuardViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val callGuardRepository =
        CallGuardRepository(
            callGuardApi =
                trueShieldApplication
                    .apiClient
                    .callGuardApi,
        )

    private val familyRepository =
        FamilyRepository(
            familyApi =
                trueShieldApplication
                    .apiClient
                    .familyApi,
        )

    private val store = CallGuardStore(application)

    private val _uiState =
        MutableStateFlow(CallGuardUiState())

    val uiState: StateFlow<CallGuardUiState> =
        _uiState.asStateFlow()

    fun loadInitial() {
        refreshRoleStatus()

        if (
            _uiState.value.initialized ||
            _uiState.value.isLoadingFamilies
        ) {
            return
        }

        loadFamilies()
    }

    fun refreshRoleStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _uiState.value =
                _uiState.value.copy(
                    roleSupported = false,
                    roleAvailable = false,
                    roleHeld = false,
                )
            return
        }

        val roleManager =
            getApplication<Application>()
                .getSystemService(RoleManager::class.java)

        val available =
            roleManager?.isRoleAvailable(
                RoleManager.ROLE_CALL_SCREENING,
            ) == true

        val held =
            available &&
                roleManager?.isRoleHeld(
                    RoleManager.ROLE_CALL_SCREENING,
                ) == true

        _uiState.value =
            _uiState.value.copy(
                roleSupported = true,
                roleAvailable = available,
                roleHeld = held,
            )
    }

    fun loadFamilies() {
        if (_uiState.value.isLoadingFamilies) {
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoadingFamilies = true,
                    errorMessage = null,
                )

            when (val result = familyRepository.getFamilies()) {
                is FamilyOperationResult.Success -> {
                    val families = result.data.items
                    val storedFamilyId =
                        store.getSelectedFamilyId()
                    val selected =
                        families.firstOrNull {
                            it.id == storedFamilyId
                        } ?: families.firstOrNull()

                    if (selected != null) {
                        store.saveSelectedFamily(
                            familyId = selected.id,
                            familyName = selected.name,
                        )
                    } else {
                        store.clearSelectedFamily()
                    }

                    _uiState.value =
                        _uiState.value.copy(
                            initialized = true,
                            isLoadingFamilies = false,
                            families = families,
                            selectedFamilyId = selected?.id,
                            selectedFamilyName = selected?.name,
                        )
                }

                is FamilyOperationResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            initialized = true,
                            isLoadingFamilies = false,
                            errorMessage = result.message,
                            sessionExpired = result.requiresLogin,
                        )
                }
            }
        }
    }

    fun selectFamily(
        familyId: String,
    ) {
        val family =
            _uiState.value.families.firstOrNull {
                it.id == familyId
            } ?: return

        store.saveSelectedFamily(
            familyId = family.id,
            familyName = family.name,
        )

        _uiState.value =
            _uiState.value.copy(
                selectedFamilyId = family.id,
                selectedFamilyName = family.name,
                successMessage = "已将“${family.name}”设为一键求助家庭。",
                errorMessage = null,
            )
    }

    fun onPhoneNumberChange(
        value: String,
    ) {
        if (value.length > 64) {
            return
        }

        _uiState.value =
            _uiState.value.copy(
                phoneNumber = value,
                errorMessage = null,
                successMessage = null,
            )
    }

    fun analyzeNumber() {
        val number = _uiState.value.phoneNumber.trim()

        if (number.isBlank()) {
            _uiState.value =
                _uiState.value.copy(
                    errorMessage = "请输入要测试的电话号码。",
                )
            return
        }

        if (_uiState.value.isAnalyzing) {
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isAnalyzing = true,
                    errorMessage = null,
                    successMessage = null,
                )

            when (
                val result = callGuardRepository.analyzeNumber(
                    CallNumberAnalyzeRequest(
                        phoneNumber = number,
                        direction = "incoming",
                        verificationStatus = "not_verified",
                    ),
                )
            ) {
                is CallGuardOperationResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isAnalyzing = false,
                            analyzedPhoneNumber = number,
                            analyzeResult = result.data,
                            helpSent = false,
                        )
                }

                is CallGuardOperationResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isAnalyzing = false,
                            errorMessage = result.message,
                            sessionExpired = result.requiresLogin,
                        )
                }
            }
        }
    }

    fun requestFamilyHelp() {
        val state = _uiState.value
        val familyId = state.selectedFamilyId
        val number = state.analyzedPhoneNumber
        val result = state.analyzeResult

        if (familyId.isNullOrBlank()) {
            _uiState.value =
                state.copy(
                    errorMessage = "请先选择一个家庭作为求助接收方。",
                )
            return
        }

        if (number.isNullOrBlank() || result == null) {
            _uiState.value =
                state.copy(
                    errorMessage = "请先完成一次号码风险筛查。",
                )
            return
        }

        if (state.isSendingHelp) {
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isSendingHelp = true,
                    errorMessage = null,
                    successMessage = null,
                )

            when (
                val operation =
                    callGuardRepository.requestFamilyHelp(
                        CallGuardHelpRequest(
                            familyId = familyId,
                            phoneNumber = number,
                            direction = result.direction,
                            verificationStatus =
                                result.verificationStatus,
                            riskLevel = result.riskLevel,
                            score = result.score,
                            summary = result.summary,
                        ),
                    )
            ) {
                is CallGuardOperationResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSendingHelp = false,
                            helpSent = true,
                            successMessage =
                                "家庭求助已发送，告警编号：${operation.data.alertId}",
                        )
                }

                is CallGuardOperationResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSendingHelp = false,
                            errorMessage = operation.message,
                            sessionExpired = operation.requiresLogin,
                        )
                }
            }
        }
    }

    fun consumeSessionExpired() {
        _uiState.value =
            _uiState.value.copy(
                sessionExpired = false,
            )
    }

    fun consumeMessages() {
        _uiState.value =
            _uiState.value.copy(
                errorMessage = null,
                successMessage = null,
            )
    }
}
