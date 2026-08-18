package com.trueshield.app.ui.alert

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.data.local.TokenStore
import com.trueshield.app.data.network.ApiClient
import com.trueshield.app.data.repository.FamilyAlertOperationResult
import com.trueshield.app.data.repository.FamilyAlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 家庭告警详情 ViewModel。
 *
 * 负责：
 * 1. 读取告警详情
 * 2. 确认告警
 * 3. 发送告警
 * 4. 重试失败通知
 * 5. 完成告警处理
 */
class FamilyAlertDetailViewModel(
    application: Application,
) : AndroidViewModel(application) {

    /**
     * 使用带 Token 拦截器的 FamilyApi。
     */
    private val repository =
        FamilyAlertRepository(
            familyApi =
                ApiClient(
                    tokenStore =
                        TokenStore(application),
                ).familyApi,
        )

    private val _uiState =
        MutableStateFlow(
            FamilyAlertDetailUiState()
        )

    val uiState: StateFlow<FamilyAlertDetailUiState> =
        _uiState.asStateFlow()

    /**
     * 保存当前正在查看的家庭编号。
     *
     * 后续刷新、确认、发送和完成处理时直接使用。
     */
    private var currentFamilyId: String = ""

    /**
     * 保存当前正在查看的告警编号。
     */
    private var currentAlertId: String = ""

    /**
     * 读取家庭告警详情。
     */
    fun loadAlert(
        familyId: String,
        alertId: String,
    ) {
        val cleanedFamilyId = familyId.trim()
        val cleanedAlertId = alertId.trim()

        if (
            cleanedFamilyId.isBlank() ||
            cleanedAlertId.isBlank()
        ) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage =
                        "家庭编号或告警编号不能为空。",
                )
            }

            return
        }

        currentFamilyId = cleanedFamilyId
        currentAlertId = cleanedAlertId

        _uiState.update {
            it.copy(
                isLoading = true,
                isLoadingDeliveryAttempts = true,
                errorMessage = null,
                deliveryAttemptsErrorMessage = null,
                operationMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    repository.getAlertDetail(
                        familyId = cleanedFamilyId,
                        alertId = cleanedAlertId,
                    )
            ) {
                is FamilyAlertOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            alert = result.data,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }

                    loadDeliveryAttempts(
                        familyId = cleanedFamilyId,
                        alertId = cleanedAlertId,
                    )
                }

                is FamilyAlertOperationResult.Error -> {
                    handleError(
                        result = result,
                        loadingOperation = false,
                    )
                }
            }
        }
    }

    /**
     * 刷新当前告警。
     */
    fun refresh() {
        if (
            currentFamilyId.isBlank() ||
            currentAlertId.isBlank()
        ) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "尚未获得当前告警编号，无法刷新。",
                )
            }

            return
        }

        loadAlert(
            familyId = currentFamilyId,
            alertId = currentAlertId,
        )
    }

    /**
     * 确认告警。
     *
     * pending -> acknowledged
     */
    fun acknowledgeAlert() {
        if (!canStartOperation()) {
            return
        }

        beginOperation()

        viewModelScope.launch {
            when (
                val result =
                    repository.acknowledgeAlert(
                        familyId = currentFamilyId,
                        alertId = currentAlertId,
                    )
            ) {
                is FamilyAlertOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            alert = result.data,
                            operationMessage =
                                "家庭告警已确认，现已进入处理阶段。",
                            errorMessage = null,
                        )
                    }
                }

                is FamilyAlertOperationResult.Error -> {
                    handleError(
                        result = result,
                        loadingOperation = true,
                    )
                }
            }
        }
    }

    /**
     * 手动发送家庭告警。
     *
     * 当前使用正常模拟发送，不主动制造发送失败。
     */
    fun dispatchAlert() {
        if (!canStartOperation()) {
            return
        }

        beginOperation()

        viewModelScope.launch {
            when (
                val result =
                    repository.dispatchAlert(
                        familyId = currentFamilyId,
                        alertId = currentAlertId,
                        simulatedFailureRecipientIds =
                            emptyList(),
                    )
            ) {
                is FamilyAlertOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            alert = result.data.alert,
                            operationMessage =
                                buildString {
                                    append("家庭告警发送完成。")
                                    append("成功发送 ")
                                    append(result.data.sentCount)
                                    append(" 条，失败 ")
                                    append(result.data.failedCount)
                                    append(" 条。")
                                },
                            errorMessage = null,
                        )
                    }

                    loadDeliveryAttempts(
                        familyId = currentFamilyId,
                        alertId = currentAlertId,
                    )
                }

                is FamilyAlertOperationResult.Error -> {
                    handleError(
                        result = result,
                        loadingOperation = true,
                    )
                }
            }
        }
    }

    /**
     * 重试当前告警中发送失败的通知。
     */
    fun retryFailedDeliveries() {
        if (!canStartOperation()) {
            return
        }

        beginOperation()

        viewModelScope.launch {
            when (
                val result =
                    repository.retryFailedDeliveries(
                        familyId = currentFamilyId,
                        alertId = currentAlertId,
                    )
            ) {
                is FamilyAlertOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            alert = result.data.alert,
                            operationMessage =
                                buildString {
                                    append("失败通知重试完成。")
                                    append("成功发送 ")
                                    append(result.data.sentCount)
                                    append(" 条，仍失败 ")
                                    append(result.data.failedCount)
                                    append(" 条。")
                                },
                            errorMessage = null,
                        )
                    }

                    loadDeliveryAttempts(
                        familyId = currentFamilyId,
                        alertId = currentAlertId,
                    )
                }

                is FamilyAlertOperationResult.Error -> {
                    handleError(
                        result = result,
                        loadingOperation = true,
                    )
                }
            }
        }
    }

    /**
     * 修改告警处理说明。
     */
    fun onResolutionNoteChange(
        resolutionNote: String,
    ) {
        _uiState.update {
            it.copy(
                resolutionNote =
                    resolutionNote.take(
                        MAX_RESOLUTION_NOTE_LENGTH
                    ),
                errorMessage = null,
                operationMessage = null,
            )
        }
    }

    /**
     * 完成告警处理。
     *
     * acknowledged -> resolved
     */
    fun resolveAlert() {
        if (!canStartOperation()) {
            return
        }

        val resolutionNote =
            _uiState.value.resolutionNote.trim()

        if (resolutionNote.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "请输入告警处理说明。",
                )
            }

            return
        }

        beginOperation()

        viewModelScope.launch {
            when (
                val result =
                    repository.resolveAlert(
                        familyId = currentFamilyId,
                        alertId = currentAlertId,
                        resolutionNote = resolutionNote,
                    )
            ) {
                is FamilyAlertOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isOperating = false,
                            alert = result.data,
                            resolutionNote = "",
                            operationMessage =
                                "家庭告警已经处理完成。",
                            errorMessage = null,
                        )
                    }
                }

                is FamilyAlertOperationResult.Error -> {
                    handleError(
                        result = result,
                        loadingOperation = true,
                    )
                }
            }
        }
    }

    /**
     * 读取该告警的全部发送尝试记录。
     *
     * 发送记录是详情页的附加信息。即使该接口读取失败，
     * 告警详情、确认、发送和完成处理功能仍然可以继续使用。
     */
    private suspend fun loadDeliveryAttempts(
        familyId: String,
        alertId: String,
    ) {
        _uiState.update {
            it.copy(
                isLoadingDeliveryAttempts = true,
                deliveryAttemptsErrorMessage = null,
            )
        }

        when (
            val result =
                repository.getDeliveryAttempts(
                    familyId = familyId,
                    alertId = alertId,
                )
        ) {
            is FamilyAlertOperationResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoadingDeliveryAttempts = false,
                        deliveryAttempts = result.data.items,
                        deliveryAttemptsErrorMessage = null,
                        sessionExpired = false,
                    )
                }
            }

            is FamilyAlertOperationResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoadingDeliveryAttempts = false,
                        deliveryAttemptsErrorMessage =
                            result.message,
                        sessionExpired =
                            result.requiresLogin,
                    )
                }
            }
        }
    }

    /**
     * 开始业务操作前进行统一校验。
     */
    private fun canStartOperation(): Boolean {
        if (_uiState.value.isOperating) {
            return false
        }

        if (
            currentFamilyId.isBlank() ||
            currentAlertId.isBlank()
        ) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "家庭编号或告警编号不存在。",
                )
            }

            return false
        }

        return true
    }

    /**
     * 标记操作开始。
     */
    private fun beginOperation() {
        _uiState.update {
            it.copy(
                isOperating = true,
                errorMessage = null,
                operationMessage = null,
            )
        }
    }

    /**
     * 统一处理仓库错误。
     */
    private fun handleError(
        result: FamilyAlertOperationResult.Error,
        loadingOperation: Boolean,
    ) {
        _uiState.update {
            it.copy(
                isLoading =
                    if (loadingOperation) {
                        it.isLoading
                    } else {
                        false
                    },
                isOperating = false,
                isLoadingDeliveryAttempts =
                    if (loadingOperation) {
                        it.isLoadingDeliveryAttempts
                    } else {
                        false
                    },
                errorMessage = result.message,
                operationMessage = null,
                sessionExpired =
                    result.requiresLogin,
            )
        }
    }

    /**
     * 页面完成退出登录处理后调用。
     */
    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(
                sessionExpired = false,
            )
        }
    }

    /**
     * 清除提示信息。
     */
    fun clearMessages() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                operationMessage = null,
            )
        }
    }

    private companion object {

        /**
         * 告警处理说明最大长度。
         */
        const val MAX_RESOLUTION_NOTE_LENGTH =
            1000
    }
}