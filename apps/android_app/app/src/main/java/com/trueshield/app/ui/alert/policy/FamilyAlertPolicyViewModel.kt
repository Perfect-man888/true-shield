package com.trueshield.app.ui.alert.policy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.data.local.TokenStore
import com.trueshield.app.data.model.alert.FamilyAlertPolicyResponse
import com.trueshield.app.data.model.alert.UpdateFamilyAlertPolicyRequest
import com.trueshield.app.data.network.ApiClient
import com.trueshield.app.data.repository.FamilyAlertPolicyRepository
import com.trueshield.app.data.repository.FamilyAlertPolicyRepositoryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 家庭告警自动触发策略 ViewModel。
 *
 * 主要职责：
 * 1. 从后端读取当前家庭策略。
 * 2. 保存用户在页面中的修改。
 * 3. 校验策略参数。
 * 4. 处理登录失效和网络错误。
 */
class FamilyAlertPolicyViewModel(
    application: Application,
) : AndroidViewModel(application) {

    /**
     * 创建带 Token 拦截器的 FamilyApi。
     */
    private val familyApi =
        ApiClient(
            tokenStore = TokenStore(application),
        ).familyApi

    /**
     * 告警策略 Repository。
     */
    private val repository =
        FamilyAlertPolicyRepository(
            familyApi = familyApi,
        )

    private val _uiState =
        MutableStateFlow(
            FamilyAlertPolicyUiState(),
        )

    val uiState: StateFlow<FamilyAlertPolicyUiState> =
        _uiState.asStateFlow()

    /**
     * 读取指定家庭的告警自动触发策略。
     */
    fun loadPolicy(
        familyId: String,
        forceRefresh: Boolean = false,
    ) {
        if (familyId.isBlank()) {
            _uiState.value =
                _uiState.value.copy(
                    errorMessage = "家庭编号不能为空。",
                )
            return
        }

        /*
         * 已经加载过相同家庭时，不重复请求。
         */
        if (
            !forceRefresh &&
            _uiState.value.familyId == familyId &&
            _uiState.value.policy != null
        ) {
            return
        }

        /*
         * 防止用户连续点击刷新导致并发请求。
         */
        if (_uiState.value.isLoading) {
            return
        }

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    familyId = familyId,
                    errorMessage = null,
                    successMessage = null,
                    sessionExpired = false,
                )

            when (
                val result =
                    repository.getAlertPolicy(
                        familyId = familyId,
                    )
            ) {
                is FamilyAlertPolicyRepositoryResult.Success -> {
                    applyPolicy(
                        policy = result.data,
                    )
                }

                is FamilyAlertPolicyRepositoryResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                }

                FamilyAlertPolicyRepositoryResult.SessionExpired -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            sessionExpired = true,
                            errorMessage =
                                "登录状态已经失效，请重新登录。",
                        )
                }
            }
        }
    }

    /**
     * 手动刷新当前家庭策略。
     */
    fun refreshPolicy() {
        val familyId =
            _uiState.value.familyId

        if (familyId.isNullOrBlank()) {
            _uiState.value =
                _uiState.value.copy(
                    errorMessage = "当前没有选中的家庭。",
                )
            return
        }

        loadPolicy(
            familyId = familyId,
            forceRefresh = true,
        )
    }

    /**
     * 修改是否自动创建家庭告警。
     */
    fun setAutoCreateEnabled(
        enabled: Boolean,
    ) {
        _uiState.value =
            _uiState.value.copy(
                autoCreateEnabled = enabled,

                /*
                 * 关闭自动创建后，自动发送也必须关闭。
                 */
                autoDispatchEnabled =
                    if (enabled) {
                        _uiState.value.autoDispatchEnabled
                    } else {
                        false
                    },

                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
            )
    }

    /**
     * 修改是否自动发送家庭告警。
     */
    fun setAutoDispatchEnabled(
        enabled: Boolean,
    ) {
        _uiState.value =
            _uiState.value.copy(
                /*
                 * 开启自动发送时，同时开启自动创建。
                 */
                autoCreateEnabled =
                    if (enabled) {
                        true
                    } else {
                        _uiState.value.autoCreateEnabled
                    },

                autoDispatchEnabled = enabled,
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
            )
    }

    /**
     * 修改最低风险等级。
     */
    fun setMinimumRiskLevel(
        riskLevel: String,
    ) {
        val normalizedRiskLevel =
            riskLevel
                .trim()
                .lowercase()

        if (
            normalizedRiskLevel !in
            setOf(
                "low",
                "medium",
                "high",
            )
        ) {
            _uiState.value =
                _uiState.value.copy(
                    errorMessage =
                        "风险等级只能是 low、medium 或 high。",
                )
            return
        }

        _uiState.value =
            _uiState.value.copy(
                minimumRiskLevel = normalizedRiskLevel,
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
            )
    }

    /**
     * 选中或取消一种风险检测来源。
     *
     * 支持：
     * text
     * image
     * url
     */
    fun toggleSourceType(
        sourceType: String,
    ) {
        val normalizedSourceType =
            sourceType
                .trim()
                .lowercase()

        if (
            normalizedSourceType !in
            setOf(
                "text",
                "image",
                "url",
            )
        ) {
            _uiState.value =
                _uiState.value.copy(
                    errorMessage =
                        "不支持的检测来源：$sourceType",
                )
            return
        }

        val currentSourceTypes =
            _uiState.value.enabledSourceTypes

        val newSourceTypes =
            if (
                normalizedSourceType in
                currentSourceTypes
            ) {
                currentSourceTypes -
                        normalizedSourceType
            } else {
                currentSourceTypes +
                        normalizedSourceType
            }

        _uiState.value =
            _uiState.value.copy(
                enabledSourceTypes = newSourceTypes,
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
            )
    }

    /**
     * 修改最大接收人数。
     */
    fun setMaxRecipientsText(
        value: String,
    ) {
        /*
         * 只保留数字，防止输入字母或符号。
         */
        val filteredValue =
            value
                .filter { character ->
                    character.isDigit()
                }
                .take(3)

        _uiState.value =
            _uiState.value.copy(
                maxRecipientsText = filteredValue,
                hasUnsavedChanges = true,
                errorMessage = null,
                successMessage = null,
            )
    }

    /**
     * 保存告警自动触发策略。
     */
    fun savePolicy() {
        val currentState =
            _uiState.value

        val familyId =
            currentState.familyId

        if (familyId.isNullOrBlank()) {
            _uiState.value =
                currentState.copy(
                    errorMessage =
                        "当前没有选中的家庭。",
                )
            return
        }

        if (currentState.isSaving) {
            return
        }

        val maxRecipients =
            currentState.maxRecipientsText
                .toIntOrNull()

        if (
            maxRecipients == null ||
            maxRecipients <= 0
        ) {
            _uiState.value =
                currentState.copy(
                    errorMessage =
                        "最大接收人数必须是大于 0 的整数。",
                )
            return
        }

        if (
            currentState.autoDispatchEnabled &&
            !currentState.autoCreateEnabled
        ) {
            _uiState.value =
                currentState.copy(
                    errorMessage =
                        "开启自动发送前，必须先开启自动创建告警。",
                )
            return
        }

        if (
            currentState.autoCreateEnabled &&
            currentState.enabledSourceTypes.isEmpty()
        ) {
            _uiState.value =
                currentState.copy(
                    errorMessage =
                        "开启自动创建告警后，至少要选择一种检测来源。",
                )
            return
        }

        val request =
            UpdateFamilyAlertPolicyRequest(
                autoCreateEnabled =
                    currentState.autoCreateEnabled,

                autoDispatchEnabled =
                    currentState.autoDispatchEnabled,

                minimumRiskLevel =
                    currentState.minimumRiskLevel,

                enabledSourceTypes =
                    currentState.enabledSourceTypes
                        .sorted(),

                maxRecipients =
                    maxRecipients,
            )

        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isSaving = true,
                    errorMessage = null,
                    successMessage = null,
                    sessionExpired = false,
                )

            when (
                val result =
                    repository.updateAlertPolicy(
                        familyId = familyId,
                        request = request,
                    )
            ) {
                is FamilyAlertPolicyRepositoryResult.Success -> {
                    applyPolicy(
                        policy = result.data,
                        successMessage =
                            "家庭告警自动触发策略已成功保存。",
                    )
                }

                is FamilyAlertPolicyRepositoryResult.Error -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            errorMessage = result.message,
                        )
                }

                FamilyAlertPolicyRepositoryResult.SessionExpired -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            sessionExpired = true,
                            errorMessage =
                                "登录状态已经失效，请重新登录。",
                        )
                }
            }
        }
    }

    /**
     * 放弃当前尚未保存的修改，
     * 恢复成后端最后一次返回的策略。
     */
    fun resetDraft() {
        val policy =
            _uiState.value.policy

        if (policy == null) {
            _uiState.value =
                _uiState.value.copy(
                    errorMessage =
                        "当前没有可以恢复的策略数据。",
                )
            return
        }

        applyPolicy(
            policy = policy,
            successMessage =
                "尚未保存的修改已经撤销。",
        )
    }

    /**
     * 将后端策略同步到页面草稿。
     */
    private fun applyPolicy(
        policy: FamilyAlertPolicyResponse,
        successMessage: String? = null,
    ) {
        _uiState.value =
            _uiState.value.copy(
                isLoading = false,
                isSaving = false,

                policy = policy,
                familyId = policy.familyId,

                autoCreateEnabled =
                    policy.autoCreateEnabled,

                autoDispatchEnabled =
                    policy.autoDispatchEnabled,

                minimumRiskLevel =
                    policy.minimumRiskLevel
                        .trim()
                        .lowercase(),

                enabledSourceTypes =
                    policy.enabledSourceTypes
                        .map { sourceType ->
                            sourceType
                                .trim()
                                .lowercase()
                        }
                        .filter { sourceType ->
                            sourceType in
                                    setOf(
                                        "text",
                                        "image",
                                        "url",
                                    )
                        }
                        .toSet(),

                maxRecipientsText =
                    policy.maxRecipients.toString(),

                hasUnsavedChanges = false,
                errorMessage = null,
                successMessage = successMessage,
                sessionExpired = false,
            )
    }

    /**
     * 清除错误提示。
     */
    fun consumeErrorMessage() {
        _uiState.value =
            _uiState.value.copy(
                errorMessage = null,
            )
    }

    /**
     * 清除成功提示。
     */
    fun consumeSuccessMessage() {
        _uiState.value =
            _uiState.value.copy(
                successMessage = null,
            )
    }

    /**
     * 消费登录过期事件。
     */
    fun consumeSessionExpired() {
        _uiState.value =
            _uiState.value.copy(
                sessionExpired = false,
            )
    }
}