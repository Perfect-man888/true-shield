package com.trueshield.app.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.FamilyOperationResult
import com.trueshield.app.data.repository.FamilyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 家庭中心 ViewModel。
 */
class FamilyCenterViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val trueShieldApplication =
        application as TrueShieldApplication

    private val familyRepository =
        FamilyRepository(
            familyApi =
                trueShieldApplication
                    .apiClient
                    .familyApi,
        )

    private val _uiState =
        MutableStateFlow(
            FamilyCenterUiState(),
        )

    val uiState: StateFlow<FamilyCenterUiState> =
        _uiState.asStateFlow()

    /**
     * 第一次进入家庭中心。
     */
    fun loadCenter(
        force: Boolean = false,
    ) {
        val state =
            _uiState.value

        if (
            !force &&
            (
                    state.isInitialLoading ||
                            state.families.isNotEmpty() ||
                            state.receivedInvitations.isNotEmpty()
                    )
        ) {
            return
        }

        if (state.isBusy) {
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
            requestCenterData()
        }
    }

    /**
     * 刷新家庭和收到的邀请。
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
            requestCenterData()
        }
    }

    /**
     * 统一读取家庭列表和收到的邀请。
     */
    private suspend fun requestCenterData(
        successMessage: String? = null,
    ) {
        val familyResult =
            familyRepository.getFamilies()

        if (
            familyResult is
                    FamilyOperationResult.Error
        ) {
            finishWithError(
                message =
                    familyResult.message,
                requiresLogin =
                    familyResult.requiresLogin,
            )
            return
        }

        familyResult as
                FamilyOperationResult.Success

        val invitationResult =
            familyRepository
                .getReceivedInvitations()

        if (
            invitationResult is
                    FamilyOperationResult.Error
        ) {
            _uiState.update {
                it.copy(
                    families =
                        familyResult.data.items,
                    totalFamilies =
                        familyResult.data.total,
                    isInitialLoading = false,
                    isRefreshing = false,
                    processingInvitationId = null,
                    errorMessage =
                        invitationResult.message,
                    sessionExpired =
                        invitationResult
                            .requiresLogin,
                )
            }
            return
        }

        invitationResult as
                FamilyOperationResult.Success

        val loadedFamilies =
            familyResult.data.items

        _uiState.update {
                currentState ->

            val retainedFamilyId =
                currentState
                    .selectedFamilyId
                    ?.takeIf {
                            selectedId ->

                        loadedFamilies.any {
                                family ->
                            family.id ==
                                    selectedId
                        }
                    }

            currentState.copy(
                families =
                    loadedFamilies,
                totalFamilies =
                    familyResult.data.total,
                receivedInvitations =
                    invitationResult.data.items,
                receivedInvitationCount =
                    invitationResult.data.total,
                selectedFamilyId =
                    retainedFamilyId,
                selectedFamilyMembers =
                    if (
                        retainedFamilyId != null
                    ) {
                        currentState
                            .selectedFamilyMembers
                    } else {
                        emptyList()
                    },
                selectedFamilyMemberTotal =
                    if (
                        retainedFamilyId != null
                    ) {
                        currentState
                            .selectedFamilyMemberTotal
                    } else {
                        0
                    },
                isInitialLoading = false,
                isRefreshing = false,
                isSendingInvitation = false,
                processingInvitationId = null,
                successMessage =
                    successMessage,
                errorMessage = null,
                sessionExpired = false,
            )
        }
    }

    fun onFamilyNameChange(
        name: String,
    ) {
        _uiState.update {
            it.copy(
                familyNameInput = name,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun createFamily() {
        val familyName =
            _uiState.value
                .familyNameInput
                .trim()

        if (familyName.isBlank()) {
            showError(
                message =
                    "请输入家庭名称。",
            )
            return
        }

        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                isCreatingFamily = true,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    familyRepository
                        .createFamily(
                            name = familyName,
                        )
            ) {
                is FamilyOperationResult.Success -> {
                    val createdFamily =
                        result.data

                    _uiState.update {
                            currentState ->

                        val alreadyExists =
                            currentState
                                .families
                                .any {
                                        family ->

                                    family.id ==
                                            createdFamily.id
                                }

                        currentState.copy(
                            families =
                                listOf(
                                    createdFamily,
                                ) +
                                        currentState
                                            .families
                                            .filterNot {
                                                    family ->

                                                family.id ==
                                                        createdFamily.id
                                            },
                            totalFamilies =
                                if (alreadyExists) {
                                    currentState
                                        .totalFamilies
                                } else {
                                    currentState
                                        .totalFamilies + 1
                                },
                            familyNameInput = "",
                            isCreatingFamily = false,
                            successMessage =
                                "家庭“${createdFamily.name}”创建成功。",
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }

                    loadFamilyMembers(
                        familyId =
                            createdFamily.id,
                        force = true,
                    )
                }

                is FamilyOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isCreatingFamily = false,
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
     * 点击家庭后读取成员。
     */
    fun loadFamilyMembers(
        familyId: String,
        force: Boolean = false,
    ) {
        val cleanedFamilyId =
            familyId.trim()

        if (cleanedFamilyId.isBlank()) {
            showError(
                message =
                    "家庭编号不能为空。",
            )
            return
        }

        val state =
            _uiState.value

        if (
            !force &&
            state.selectedFamilyId ==
            cleanedFamilyId &&
            (
                    state.isLoadingMembers ||
                            state
                                .selectedFamilyMembers
                                .isNotEmpty()
                    )
        ) {
            return
        }

        if (state.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                selectedFamilyId =
                    cleanedFamilyId,
                selectedFamilyMembers =
                    emptyList(),
                selectedFamilyMemberTotal = 0,
                inviteeEmailInput = "",
                isLoadingMembers = true,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    familyRepository
                        .getFamilyMembers(
                            familyId =
                                cleanedFamilyId,
                        )
            ) {
                is FamilyOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedFamilyId =
                                result.data.familyId,
                            selectedFamilyMembers =
                                result.data.items,
                            selectedFamilyMemberTotal =
                                result.data.total,
                            isLoadingMembers = false,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is FamilyOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingMembers = false,
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
     * 更新成员邀请邮箱。
     */
    fun onInviteeEmailChange(
        email: String,
    ) {
        val limitedEmail =
            email.take(
                FAMILY_INVITEE_EMAIL_MAX_LENGTH,
            )

        _uiState.update {
            it.copy(
                inviteeEmailInput =
                    limitedEmail,
                successMessage = null,
                errorMessage =
                    if (
                        email.length >
                        FAMILY_INVITEE_EMAIL_MAX_LENGTH
                    ) {
                        "邮箱地址长度不能超过 " +
                                "$FAMILY_INVITEE_EMAIL_MAX_LENGTH 个字符。"
                    } else {
                        null
                    },
            )
        }
    }

    /**
     * 向当前选中的家庭发送邀请。
     */
    fun sendInvitation() {
        val state =
            _uiState.value

        val family =
            state.selectedFamily

        if (family == null) {
            showError(
                message =
                    "请先选择需要邀请成员的家庭。",
            )
            return
        }

        if (!state.selectedFamilyCanInvite) {
            showError(
                message =
                    "当前账户没有邀请家庭成员的权限。",
            )
            return
        }

        val email =
            state.inviteeEmailInput.trim()

        if (email.isBlank()) {
            showError(
                message =
                    "请输入被邀请人的邮箱。",
            )
            return
        }

        if (state.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                isSendingInvitation = true,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    familyRepository
                        .createInvitation(
                            familyId =
                                family.id,
                            inviteeEmail =
                                email,
                        )
            ) {
                is FamilyOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            inviteeEmailInput = "",
                            isSendingInvitation = false,
                            successMessage =
                                "已向 ${result.data.inviteeEmail} " +
                                        "发送家庭邀请。",
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }
                }

                is FamilyOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSendingInvitation = false,
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
     * 接受家庭邀请。
     */
    fun acceptInvitation(
        invitationId: String,
    ) {
        val cleanedInvitationId =
            invitationId.trim()

        if (cleanedInvitationId.isBlank()) {
            showError(
                message =
                    "家庭邀请编号不能为空。",
            )
            return
        }

        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                processingInvitationId =
                    cleanedInvitationId,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    familyRepository
                        .acceptInvitation(
                            invitationId =
                                cleanedInvitationId,
                        )
            ) {
                is FamilyOperationResult.Success -> {
                    requestCenterData(
                        successMessage =
                            "已加入家庭“${result.data.familyName}”。",
                    )
                }

                is FamilyOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            processingInvitationId =
                                null,
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
     * 拒绝家庭邀请。
     */
    fun declineInvitation(
        invitationId: String,
    ) {
        val cleanedInvitationId =
            invitationId.trim()

        if (cleanedInvitationId.isBlank()) {
            showError(
                message =
                    "家庭邀请编号不能为空。",
            )
            return
        }

        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                processingInvitationId =
                    cleanedInvitationId,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    familyRepository
                        .declineInvitation(
                            invitationId =
                                cleanedInvitationId,
                        )
            ) {
                is FamilyOperationResult.Success -> {
                    requestCenterData(
                        successMessage =
                            "已拒绝该家庭邀请。",
                    )
                }

                is FamilyOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            processingInvitationId =
                                null,
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

    fun clearSelectedFamily() {
        _uiState.update {
            it.copy(
                selectedFamilyId = null,
                selectedFamilyMembers =
                    emptyList(),
                selectedFamilyMemberTotal = 0,
                inviteeEmailInput = "",
                isLoadingMembers = false,
                isSendingInvitation = false,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun retry() {
        val state =
            _uiState.value

        if (
            state.selectedFamilyId != null &&
            state
                .selectedFamilyMembers
                .isEmpty()
        ) {
            loadFamilyMembers(
                familyId =
                    state.selectedFamilyId,
                force = true,
            )
        } else {
            loadCenter(
                force = true,
            )
        }
    }

    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(
                sessionExpired = false,
            )
        }
    }

    private fun finishWithError(
        message: String,
        requiresLogin: Boolean,
    ) {
        _uiState.update {
            it.copy(
                isInitialLoading = false,
                isRefreshing = false,
                isCreatingFamily = false,
                isLoadingMembers = false,
                isSendingInvitation = false,
                processingInvitationId = null,
                errorMessage = message,
                sessionExpired =
                    requiresLogin,
            )
        }
    }

    private fun showError(
        message: String,
    ) {
        finishWithError(
            message = message,
            requiresLogin = false,
        )
    }
}