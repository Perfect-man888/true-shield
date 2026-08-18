package com.trueshield.app.ui.contact

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.model.contact.TrustedContactChannelValue
import com.trueshield.app.data.model.contact.TrustedContactDto
import com.trueshield.app.data.repository.FamilyOperationResult
import com.trueshield.app.data.repository.FamilyRepository
import com.trueshield.app.data.repository.TrustedContactOperationResult
import com.trueshield.app.data.repository.TrustedContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 可信联系人 ViewModel。
 */
class TrustedContactViewModel(
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

    private val trustedContactRepository =
        TrustedContactRepository(
            trustedContactApi =
                trueShieldApplication
                    .apiClient
                    .trustedContactApi,
        )

    private val _uiState =
        MutableStateFlow(
            TrustedContactUiState(),
        )

    val uiState: StateFlow<TrustedContactUiState> =
        _uiState.asStateFlow()

    /**
     * 第一次进入页面：
     *
     * 1. 读取家庭列表；
     * 2. 自动选择第一个家庭；
     * 3. 读取当前成员的可信联系人。
     */
    fun loadInitial(
        force: Boolean = false,
    ) {
        val state =
            _uiState.value

        if (
            !force &&
            (
                    state.isInitialLoading ||
                            state.families.isNotEmpty()
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
            loadFamiliesAndContacts()
        }
    }

    /**
     * 刷新家庭列表和联系人。
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
            loadFamiliesAndContacts()
        }
    }

    /**
     * 统一读取家庭列表和选中家庭联系人。
     */
    private suspend fun loadFamiliesAndContacts() {
        when (
            val result =
                familyRepository.getFamilies()
        ) {
            is FamilyOperationResult.Error -> {
                finishWithError(
                    message = result.message,
                    requiresLogin =
                        result.requiresLogin,
                )
            }

            is FamilyOperationResult.Success -> {
                val families =
                    result.data.items

                if (families.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            families = emptyList(),
                            selectedFamilyId = null,
                            subjectUserId = null,
                            contacts = emptyList(),
                            totalContacts = 0,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingContacts = false,
                            errorMessage = null,
                            sessionExpired = false,
                        )
                    }

                    return
                }

                val currentSelectedId =
                    _uiState.value
                        .selectedFamilyId

                val selectedFamilyId =
                    currentSelectedId
                        ?.takeIf {
                                selectedId ->

                            families.any {
                                    family ->

                                family.id ==
                                        selectedId
                            }
                        }
                        ?: families.first().id

                _uiState.update {
                    it.copy(
                        families = families,
                        selectedFamilyId =
                            selectedFamilyId,
                        isLoadingContacts = true,
                    )
                }

                loadContactsInternal(
                    familyId =
                        selectedFamilyId,
                )
            }
        }
    }

    /**
     * 切换家庭。
     */
    fun selectFamily(
        familyId: String,
    ) {
        val cleanedFamilyId =
            familyId.trim()

        if (
            cleanedFamilyId.isBlank() ||
            _uiState.value.isBusy
        ) {
            return
        }

        if (
            _uiState.value
                .selectedFamilyId ==
            cleanedFamilyId
        ) {
            return
        }

        _uiState.update {
            it.copy(
                selectedFamilyId =
                    cleanedFamilyId,
                subjectUserId = null,
                contacts = emptyList(),
                totalContacts = 0,
                isLoadingContacts = true,
                editingContactId = null,
                displayNameInput = "",
                relationshipInput = "",
                phoneInput = "",
                emailInput = "",
                preferredChannel =
                    TrustedContactChannelValue
                        .PHONE,
                priorityInput = "1",
                canReceiveAlertsInput = true,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            loadContactsInternal(
                familyId =
                    cleanedFamilyId,
            )
        }
    }

    /**
     * 重新读取当前家庭联系人。
     */
    fun reloadContacts() {
        val familyId =
            _uiState.value
                .selectedFamilyId
                ?: return

        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                isLoadingContacts = true,
                successMessage = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            loadContactsInternal(
                familyId =
                    familyId,
            )
        }
    }

    /**
     * 实际读取联系人。
     */
    private suspend fun loadContactsInternal(
        familyId: String,
        successMessage: String? = null,
    ) {
        when (
            val result =
                trustedContactRepository
                    .getTrustedContacts(
                        familyId =
                            familyId,
                    )
        ) {
            is TrustedContactOperationResult.Success -> {
                _uiState.update {
                    it.copy(
                        selectedFamilyId =
                            result.data.familyId,
                        subjectUserId =
                            result.data.subjectUserId,
                        contacts =
                            result.data.items,
                        totalContacts =
                            result.data.total,
                        isInitialLoading = false,
                        isRefreshing = false,
                        isLoadingContacts = false,
                        isSavingContact = false,
                        processingContactId = null,
                        successMessage =
                            successMessage,
                        errorMessage = null,
                        sessionExpired = false,
                    )
                }
            }

            is TrustedContactOperationResult.Error -> {
                finishWithError(
                    message = result.message,
                    requiresLogin =
                        result.requiresLogin,
                )
            }
        }
    }

    /**
     * 开始创建新联系人。
     */
    fun startCreate() {
        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                editingContactId = null,
                displayNameInput = "",
                relationshipInput = "",
                phoneInput = "",
                emailInput = "",
                preferredChannel =
                    TrustedContactChannelValue
                        .PHONE,
                priorityInput = "1",
                canReceiveAlertsInput = true,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    /**
     * 开始编辑联系人。
     */
    fun startEdit(
        contact: TrustedContactDto,
    ) {
        if (_uiState.value.isBusy) {
            return
        }

        _uiState.update {
            it.copy(
                editingContactId =
                    contact.id,
                displayNameInput =
                    contact.displayName,
                relationshipInput =
                    contact.relationshipLabel,
                phoneInput =
                    contact.phone.orEmpty(),
                emailInput =
                    contact.email.orEmpty(),
                preferredChannel =
                    contact.preferredChannel,
                priorityInput =
                    contact.priority.toString(),
                canReceiveAlertsInput =
                    contact.canReceiveAlerts,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    /**
     * 取消编辑并清空表单。
     */
    fun cancelEdit() {
        if (_uiState.value.isBusy) {
            return
        }

        resetForm()
    }

    fun onDisplayNameChange(
        value: String,
    ) {
        _uiState.update {
            it.copy(
                displayNameInput = value,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onRelationshipChange(
        value: String,
    ) {
        _uiState.update {
            it.copy(
                relationshipInput = value,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onPhoneChange(
        value: String,
    ) {
        _uiState.update {
            it.copy(
                phoneInput = value,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onEmailChange(
        value: String,
    ) {
        _uiState.update {
            it.copy(
                emailInput = value,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onPreferredChannelChange(
        value: String,
    ) {
        if (
            value != TrustedContactChannelValue.PHONE &&
            value != TrustedContactChannelValue.EMAIL
        ) {
            return
        }

        _uiState.update {
            it.copy(
                preferredChannel = value,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onPriorityChange(
        value: String,
    ) {
        val filteredValue =
            value.filter {
                    character ->
                character.isDigit()
            }

        _uiState.update {
            it.copy(
                priorityInput =
                    filteredValue,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onCanReceiveAlertsChange(
        value: Boolean,
    ) {
        _uiState.update {
            it.copy(
                canReceiveAlertsInput =
                    value,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    /**
     * 创建或更新联系人。
     */
    fun saveContact() {
        val state =
            _uiState.value

        if (state.isBusy) {
            return
        }

        val familyId =
            state.selectedFamilyId

        val subjectUserId =
            state.subjectUserId

        if (
            familyId.isNullOrBlank() ||
            subjectUserId.isNullOrBlank()
        ) {
            showError(
                message =
                    "尚未获取当前家庭成员信息。",
            )
            return
        }

        val priority =
            state.priorityInput
                .trim()
                .toIntOrNull()

        if (priority == null) {
            showError(
                message =
                    "请输入有效的联系人优先级。",
            )
            return
        }

        _uiState.update {
            it.copy(
                isSavingContact = true,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            val editingContactId =
                state.editingContactId

            val result =
                if (editingContactId == null) {
                    trustedContactRepository
                        .createTrustedContact(
                            familyId =
                                familyId,
                            subjectUserId =
                                subjectUserId,
                            displayName =
                                state.displayNameInput,
                            relationshipLabel =
                                state.relationshipInput,
                            phone =
                                state.phoneInput,
                            email =
                                state.emailInput,
                            preferredChannel =
                                state.preferredChannel,
                            priority =
                                priority,
                            canReceiveAlerts =
                                state.canReceiveAlertsInput,
                        )
                } else {
                    trustedContactRepository
                        .updateTrustedContact(
                            familyId =
                                familyId,
                            contactId =
                                editingContactId,
                            displayName =
                                state.displayNameInput,
                            relationshipLabel =
                                state.relationshipInput,
                            phone =
                                state.phoneInput,
                            email =
                                state.emailInput,
                            preferredChannel =
                                state.preferredChannel,
                            priority =
                                priority,
                            canReceiveAlerts =
                                state.canReceiveAlertsInput,
                        )
                }

            when (result) {
                is TrustedContactOperationResult.Success -> {
                    val message =
                        if (editingContactId == null) {
                            "可信联系人“${result.data.displayName}”创建成功。"
                        } else {
                            "可信联系人“${result.data.displayName}”修改成功。"
                        }

                    resetForm(
                        clearMessage = false,
                    )

                    _uiState.update {
                        it.copy(
                            isLoadingContacts = true,
                            isSavingContact = false,
                        )
                    }

                    loadContactsInternal(
                        familyId =
                            familyId,
                        successMessage =
                            message,
                    )
                }

                is TrustedContactOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingContact = false,
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
     * 快速开启或关闭联系人接收告警。
     *
     * 实际使用 PATCH 修改 can_receive_alerts。
     */
    fun toggleAlerts(
        contact: TrustedContactDto,
    ) {
        val state =
            _uiState.value

        val familyId =
            state.selectedFamilyId
                ?: return

        if (
            state.isBusy ||
            contact.status.lowercase() != "active"
        ) {
            return
        }

        _uiState.update {
            it.copy(
                processingContactId =
                    contact.id,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    trustedContactRepository
                        .updateTrustedContact(
                            familyId =
                                familyId,
                            contactId =
                                contact.id,
                            displayName =
                                contact.displayName,
                            relationshipLabel =
                                contact.relationshipLabel,
                            phone =
                                contact.phone.orEmpty(),
                            email =
                                contact.email.orEmpty(),
                            preferredChannel =
                                contact.preferredChannel,
                            priority =
                                contact.priority,
                            canReceiveAlerts =
                                !contact.canReceiveAlerts,
                        )
            ) {
                is TrustedContactOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            processingContactId = null,
                            isLoadingContacts = true,
                        )
                    }

                    loadContactsInternal(
                        familyId =
                            familyId,
                        successMessage =
                            if (
                                result.data
                                    .canReceiveAlerts
                            ) {
                                "已允许“${result.data.displayName}”接收家庭告警。"
                            } else {
                                "已暂停“${result.data.displayName}”接收家庭告警。"
                            },
                    )
                }

                is TrustedContactOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            processingContactId = null,
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
     * 软停用联系人。
     */
    fun disableContact(
        contact: TrustedContactDto,
    ) {
        val familyId =
            _uiState.value
                .selectedFamilyId
                ?: return

        if (
            _uiState.value.isBusy ||
            contact.status.lowercase() != "active"
        ) {
            return
        }

        _uiState.update {
            it.copy(
                processingContactId =
                    contact.id,
                successMessage = null,
                errorMessage = null,
                sessionExpired = false,
            )
        }

        viewModelScope.launch {
            when (
                val result =
                    trustedContactRepository
                        .disableTrustedContact(
                            familyId =
                                familyId,
                            contactId =
                                contact.id,
                        )
            ) {
                is TrustedContactOperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            processingContactId = null,
                            isLoadingContacts = true,
                        )
                    }

                    loadContactsInternal(
                        familyId =
                            familyId,
                        successMessage =
                            "可信联系人“${result.data.displayName}”已停用。",
                    )
                }

                is TrustedContactOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            processingContactId = null,
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

    fun retry() {
        val familyId =
            _uiState.value
                .selectedFamilyId

        if (familyId == null) {
            loadInitial(
                force = true,
            )
        } else {
            reloadContacts()
        }
    }

    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(
                sessionExpired = false,
            )
        }
    }

    private fun resetForm(
        clearMessage: Boolean = true,
    ) {
        _uiState.update {
            it.copy(
                editingContactId = null,
                displayNameInput = "",
                relationshipInput = "",
                phoneInput = "",
                emailInput = "",
                preferredChannel =
                    TrustedContactChannelValue
                        .PHONE,
                priorityInput = "1",
                canReceiveAlertsInput = true,
                successMessage =
                    if (clearMessage) {
                        null
                    } else {
                        it.successMessage
                    },
                errorMessage = null,
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
                isLoadingContacts = false,
                isSavingContact = false,
                processingContactId = null,
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