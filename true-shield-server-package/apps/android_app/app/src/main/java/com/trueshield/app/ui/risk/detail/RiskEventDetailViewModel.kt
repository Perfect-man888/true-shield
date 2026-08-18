package com.trueshield.app.ui.risk.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.FamilyAlertOperationResult
import com.trueshield.app.data.repository.FamilyAlertRepository
import com.trueshield.app.data.repository.FamilyOperationResult
import com.trueshield.app.data.repository.FamilyRepository
import com.trueshield.app.data.repository.RiskEventDetailResult
import com.trueshield.app.data.repository.RiskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 通用风险事件详情 ViewModel。
 */
class RiskEventDetailViewModel(
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

    private val familyRepository =
        FamilyRepository(
            familyApi =
                trueShieldApplication
                    .apiClient
                    .familyApi,
        )

    private val familyAlertRepository =
        FamilyAlertRepository(
            familyApi =
                trueShieldApplication
                    .apiClient
                    .familyApi,
        )

    private val _uiState =
        MutableStateFlow(
            RiskEventDetailUiState(),
        )

    val uiState: StateFlow<RiskEventDetailUiState> =
        _uiState.asStateFlow()

    /**
     * 根据事件编号读取详情。
     *
     * force 为 true 时，即使已经加载过也重新请求。
     */
    fun loadEvent(
        eventId: String,
        force: Boolean = false,
    ) {
        val cleanedEventId =
            eventId.trim()

        if (cleanedEventId.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    detail = null,
                    errorMessage =
                        "风险事件编号不能为空。",
                )
            }
            return
        }

        val currentState =
            _uiState.value

        /*
         * 防止 Compose 重组时重复调用接口。
         */
        if (
            !force &&
            currentState.eventId ==
            cleanedEventId &&
            (
                    currentState.isLoading ||
                            currentState.detail != null
                    )
        ) {
            return
        }

        _uiState.update {
            it.copy(
                eventId = cleanedEventId,
                isLoading = true,
                detail = null,
                availableFamilies = emptyList(),
                selectedFamilyId = null,
                isLoadingFamilies = false,
                isCreatingFamilyAlert = false,
                createdFamilyAlert = null,
                familyAlertSuccessMessage = null,
                familyAlertErrorMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    riskRepository.getEventDetail(
                        eventId = cleanedEventId,
                    )
            ) {
                is RiskEventDetailResult.Success -> {
                    val loadedDetail =
                        result.response

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = loadedDetail,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }

                    /*
                     * 后端只允许 high 风险事件
                     * 创建家庭告警，因此只在此时
                     * 请求家庭列表。
                     */
                    if (
                        loadedDetail.riskLevel
                            .equals(
                                other = "high",
                                ignoreCase = true,
                            )
                    ) {
                        loadFamiliesForAlert()
                    }
                }

                is RiskEventDetailResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = null,
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
     * 读取当前用户可以访问的家庭。
     */
    private suspend fun loadFamiliesForAlert() {
        val state =
            _uiState.value

        if (
            state.detail?.riskLevel
                ?.equals(
                    other = "high",
                    ignoreCase = true,
                ) != true
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isLoadingFamilies = true,
                familyAlertErrorMessage = null,
                sessionExpired = false,
            )
        }

        when (
            val result =
                familyRepository.getFamilies()
        ) {
            is FamilyOperationResult.Success -> {
                val families =
                    result.data.items

                _uiState.update {
                        currentState ->

                    val retainedFamilyId =
                        currentState
                            .selectedFamilyId
                            ?.takeIf {
                                    selectedId ->

                                families.any {
                                        family ->
                                    family.id == selectedId
                                }
                            }

                    currentState.copy(
                        availableFamilies = families,
                        selectedFamilyId =
                            retainedFamilyId
                                ?: families
                                    .firstOrNull()
                                    ?.id,
                        isLoadingFamilies = false,
                        familyAlertErrorMessage = null,
                        sessionExpired = false,
                    )
                }
            }

            is FamilyOperationResult.Error -> {
                _uiState.update {
                    it.copy(
                        availableFamilies = emptyList(),
                        selectedFamilyId = null,
                        isLoadingFamilies = false,
                        familyAlertErrorMessage =
                            result.message,
                        sessionExpired =
                            result.requiresLogin,
                    )
                }
            }
        }
    }

    /**
     * 选择要生成告警的家庭。
     */
    fun selectFamily(
        familyId: String,
    ) {
        val cleanedFamilyId =
            familyId.trim()

        if (cleanedFamilyId.isBlank()) {
            return
        }

        val familyExists =
            _uiState.value
                .availableFamilies
                .any {
                        family ->
                    family.id == cleanedFamilyId
                }

        if (!familyExists) {
            return
        }

        _uiState.update {
            it.copy(
                selectedFamilyId = cleanedFamilyId,
                familyAlertSuccessMessage = null,
                familyAlertErrorMessage = null,
            )
        }
    }

    /**
     * 根据当前 high 风险事件创建家庭告警。
     */
    fun createFamilyAlert() {
        val state =
            _uiState.value

        if (
            state.isLoadingFamilies ||
            state.isCreatingFamilyAlert
        ) {
            return
        }

        val detail =
            state.detail

        if (detail == null) {
            showFamilyAlertError(
                message = "请先加载风险事件详情。",
            )
            return
        }

        if (
            !detail.riskLevel.equals(
                other = "high",
                ignoreCase = true,
            )
        ) {
            showFamilyAlertError(
                message =
                    "只有高风险事件可以创建家庭告警。",
            )
            return
        }

        val familyId =
            state.selectedFamilyId
                ?.trim()
                .orEmpty()

        if (familyId.isBlank()) {
            showFamilyAlertError(
                message =
                    "请先选择要接收告警的家庭。",
            )
            return
        }

        _uiState.update {
            it.copy(
                isCreatingFamilyAlert = true,
                createdFamilyAlert = null,
                familyAlertSuccessMessage = null,
                familyAlertErrorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    familyAlertRepository
                        .createAlertFromEvent(
                            familyId = familyId,
                            eventId = detail.eventId,
                        )
            ) {
                is FamilyAlertOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isCreatingFamilyAlert = false,
                            createdFamilyAlert =
                                result.data,
                            familyAlertSuccessMessage =
                                "家庭告警创建成功。请进入告警详情确认并发送通知。",
                            familyAlertErrorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is FamilyAlertOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isCreatingFamilyAlert = false,
                            createdFamilyAlert = null,
                            familyAlertSuccessMessage = null,
                            familyAlertErrorMessage =
                                result.message,
                            sessionExpired =
                                result.requiresLogin,
                        )
                    }
                }
            }
        }
    }

    private fun showFamilyAlertError(
        message: String,
    ) {
        _uiState.update {
            it.copy(
                familyAlertSuccessMessage = null,
                familyAlertErrorMessage = message,
            )
        }
    }

    /**
     * 重新读取当前事件。
     */
    fun retry() {
        val currentEventId =
            _uiState.value.eventId

        loadEvent(
            eventId = currentEventId,
            force = true,
        )
    }

    /**
     * 标记 Token 失效事件已经处理。
     */
    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(
                sessionExpired = false,
            )
        }
    }
}
