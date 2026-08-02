package com.trueshield.app.ui.alert

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.FamilyAlertOperationResult
import com.trueshield.app.data.repository.FamilyAlertRepository
import com.trueshield.app.data.repository.FamilyOperationResult
import com.trueshield.app.data.repository.FamilyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.trueshield.app.data.local.TokenStore

/**
 * 家庭告警列表 ViewModel。
 *
 * 主要职责：
 *
 * 1. 读取当前账户的家庭列表；
 * 2. 自动选择一个家庭；
 * 3. 读取所选家庭的告警；
 * 4. 切换家庭；
 * 5. 切换告警状态筛选条件；
 * 6. 处理刷新、错误和登录失效。
 */
class FamilyAlertViewModel(
    application: Application,
) : AndroidViewModel(application) {

    /**
     * 获取整个 App 共用的 ApiClient。
     */
    private val trueShieldApplication =
        application as TrueShieldApplication

    /**
     * 用于读取当前用户的家庭列表。
     */
    private val familyRepository =
        FamilyRepository(
            familyApi =
                trueShieldApplication
                    .apiClient
                    .familyApi,
        )

    /**
     * 用于读取和操作家庭告警。
     */
    private val familyAlertRepository =
        FamilyAlertRepository(
            familyApi =
                trueShieldApplication
                    .apiClient
                    .familyApi,
        )

    /**
     * ViewModel 内部可以修改的状态。
     */
    private val _uiState =
        MutableStateFlow(
            FamilyAlertUiState(),
        )

    /**
     * 页面只能观察，不能直接修改。
     */
    val uiState: StateFlow<FamilyAlertUiState> =
        _uiState.asStateFlow()

    /**
     * 第一次进入家庭告警页面。
     *
     * force 为 false 时：
     * 如果已经完成过初始化，就不会重复请求。
     *
     * force 为 true 时：
     * 强制重新请求。
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
                            currentState.hasLoadedOnce
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
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            loadFamiliesAndAlerts()
        }
    }

    /**
     * 刷新家庭列表和当前家庭的告警。
     */
    fun refresh() {
        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                isRefreshing = true,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            loadFamiliesAndAlerts()
        }
    }

    /**
     * 读取家庭列表，然后读取所选家庭的告警。
     */
    private suspend fun loadFamiliesAndAlerts() {
        when (
            val familyResult =
                familyRepository.getFamilies()
        ) {
            /**
             * 读取家庭列表失败。
             */
            is FamilyOperationResult.Error -> {
                finishWithError(
                    message =
                        familyResult.message,
                    requiresLogin =
                        familyResult.requiresLogin,
                )
            }

            /**
             * 读取家庭列表成功。
             */
            is FamilyOperationResult.Success -> {
                val loadedFamilies =
                    familyResult.data.items

                /**
                 * 当前账户没有家庭。
                 */
                if (loadedFamilies.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            families = emptyList(),
                            selectedFamilyId = null,
                            alerts = emptyList(),
                            totalAlerts = 0,
                            hasLoadedOnce = true,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingAlerts = false,
                            successMessage = null,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }

                    return
                }

                /**
                 * 刷新时优先保留之前选中的家庭。
                 *
                 * 如果之前选择的家庭已经不存在，
                 * 则自动选择家庭列表中的第一个家庭。
                 */
                val previousSelectedFamilyId =
                    _uiState.value
                        .selectedFamilyId

                val selectedFamilyId =
                    previousSelectedFamilyId
                        ?.takeIf {
                                previousId ->

                            loadedFamilies.any {
                                    family ->

                                family.id ==
                                        previousId
                            }
                        }
                        ?: loadedFamilies.first().id

                _uiState.update {
                    it.copy(
                        families =
                            loadedFamilies,
                        selectedFamilyId =
                            selectedFamilyId,
                        isLoadingAlerts = true,
                        errorMessage = null,
                        sessionExpired = false,
                    )
                }

                /**
                 * 家庭确定后，读取该家庭的告警。
                 */
                loadAlertsInternal(
                    familyId =
                        selectedFamilyId,
                )
            }
        }
    }

    /**
     * 用户切换家庭。
     */
    fun selectFamily(
        familyId: String,
    ) {
        val cleanedFamilyId =
            familyId.trim()

        val currentState =
            _uiState.value

        if (
            cleanedFamilyId.isBlank() ||
            currentState.isBusy ||
            currentState.selectedFamilyId ==
            cleanedFamilyId
        ) {
            return
        }

        /**
         * 确认这个家庭确实位于当前家庭列表中。
         */
        val familyExists =
            currentState.families.any {
                it.id == cleanedFamilyId
            }

        if (!familyExists) {
            showError(
                message =
                    "没有找到所选家庭。",
            )
            return
        }

        /**
         * 切换家庭时，先清空上一个家庭的告警，
         * 避免旧数据暂时显示在新家庭下面。
         */
        _uiState.update {
            it.copy(
                selectedFamilyId =
                    cleanedFamilyId,
                alerts = emptyList(),
                totalAlerts = 0,
                isLoadingAlerts = true,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            loadAlertsInternal(
                familyId =
                    cleanedFamilyId,
            )
        }
    }

    /**
     * 修改告警筛选条件。
     *
     * 此操作只修改本地状态，
     * 不会重新请求后端。
     */
    fun selectFilter(
        filter: FamilyAlertFilter,
    ) {
        _uiState.update {
            it.copy(
                selectedFilter = filter,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    /**
     * 只刷新当前选中家庭的告警。
     *
     * 不重新请求家庭列表。
     */
    fun refreshCurrentFamilyAlerts() {
        val currentState =
            _uiState.value

        val familyId =
            currentState.selectedFamilyId

        if (
            familyId.isNullOrBlank() ||
            currentState.isBusy
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isLoadingAlerts = true,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            loadAlertsInternal(
                familyId =
                    familyId,
            )
        }
    }

    /**
     * 真正调用 FamilyAlertRepository
     * 获取家庭告警列表。
     */
    private suspend fun loadAlertsInternal(
        familyId: String,
    ) {
        when (
            val result =
                familyAlertRepository
                    .getAlerts(
                        familyId =
                            familyId,
                    )
        ) {
            /**
             * 告警列表加载成功。
             */
            is FamilyAlertOperationResult.Success -> {
                /**
                 * 按创建时间倒序排列，
                 * 最新告警显示在最前面。
                 *
                 * 后端时间为 ISO 格式，
                 * 可以直接进行字符串倒序排序。
                 */
                val loadedAlerts =
                    result.data.items
                        .sortedByDescending {
                            it.createdAt
                        }

                _uiState.update {
                    it.copy(
                        alerts =
                            loadedAlerts,
                        totalAlerts =
                            result.data.total,
                        hasLoadedOnce = true,
                        isInitialLoading = false,
                        isRefreshing = false,
                        isLoadingAlerts = false,
                        successMessage = null,
                        errorMessage = null,
                        sessionExpired = false,
                    )
                }
            }

            /**
             * 告警列表加载失败。
             */
            is FamilyAlertOperationResult.Error -> {
                finishWithError(
                    message =
                        result.message,
                    requiresLogin =
                        result.requiresLogin,
                )
            }
        }
    }

    /**
     * 页面加载失败后的重试。
     */
    fun retry() {
        val currentState =
            _uiState.value

        if (currentState.isBusy) {
            return
        }

        val selectedFamilyId =
            currentState.selectedFamilyId

        /**
         * 家庭列表都没有加载出来：
         * 重新执行完整初始化。
         */
        if (
            currentState.families.isEmpty() ||
            selectedFamilyId.isNullOrBlank()
        ) {
            loadInitial(
                force = true,
            )
        } else {
            /**
             * 家庭已经加载成功：
             * 只重新请求当前家庭告警。
             */
            refreshCurrentFamilyAlerts()
        }
    }

    /**
     * 导航层已经处理完登录失效事件。
     */
    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(
                sessionExpired = false,
            )
        }
    }

    /**
     * 清除成功或错误提示。
     */
    fun clearMessage() {
        _uiState.update {
            it.copy(
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    /**
     * 显示普通页面错误。
     */
    private fun showError(
        message: String,
    ) {
        _uiState.update {
            it.copy(
                errorMessage =
                    message,
                successMessage = null,
            )
        }
    }

    /**
     * 统一结束加载并显示错误。
     */
    private fun finishWithError(
        message: String,
        requiresLogin: Boolean,
    ) {
        _uiState.update {
            it.copy(
                hasLoadedOnce = true,
                isInitialLoading = false,
                isRefreshing = false,
                isLoadingAlerts = false,
                successMessage = null,
                errorMessage =
                    message,
                sessionExpired =
                    requiresLogin,
            )
        }
    }
}