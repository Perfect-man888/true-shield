package com.trueshield.app.ui.contact

import com.trueshield.app.data.model.contact.TrustedContactChannelValue
import com.trueshield.app.data.model.contact.TrustedContactDto
import com.trueshield.app.data.model.family.FamilyDto

/**
 * 可信联系人页面状态。
 */
data class TrustedContactUiState(

    /**
     * 当前用户创建或加入的家庭。
     */
    val families: List<FamilyDto> =
        emptyList(),

    /**
     * 当前选中的家庭编号。
     */
    val selectedFamilyId: String? = null,

    /**
     * 后端根据当前登录用户返回的被保护成员编号。
     */
    val subjectUserId: String? = null,

    /**
     * 当前家庭下属于当前成员的可信联系人。
     */
    val contacts: List<TrustedContactDto> =
        emptyList(),

    val totalContacts: Int = 0,

    /**
     * 不为空时表示正在编辑联系人。
     * 为空时表示正在创建联系人。
     */
    val editingContactId: String? = null,

    val displayNameInput: String = "",

    val relationshipInput: String = "",

    val phoneInput: String = "",

    val emailInput: String = "",

    val preferredChannel:
    String = TrustedContactChannelValue.PHONE,

    /**
     * 使用字符串接收输入，
     * 提交时再转换成 Int。
     */
    val priorityInput: String = "1",

    val canReceiveAlertsInput: Boolean = true,

    val isInitialLoading: Boolean = false,

    val isRefreshing: Boolean = false,

    val isLoadingContacts: Boolean = false,

    val isSavingContact: Boolean = false,

    /**
     * 当前正在修改告警状态或停用的联系人编号。
     */
    val processingContactId: String? = null,

    val successMessage: String? = null,

    val errorMessage: String? = null,

    val sessionExpired: Boolean = false,
) {

    val selectedFamily: FamilyDto?
        get() =
            families.firstOrNull {
                it.id == selectedFamilyId
            }

    val isEditing: Boolean
        get() =
            editingContactId != null

    val isBusy: Boolean
        get() =
            isInitialLoading ||
                    isRefreshing ||
                    isLoadingContacts ||
                    isSavingContact ||
                    processingContactId != null

    /**
     * 当前表单是否满足基本提交条件。
     *
     * 更完整的格式验证仍由 Repository 执行。
     */
    val canSubmit: Boolean
        get() {
            if (isBusy) {
                return false
            }

            if (
                selectedFamilyId.isNullOrBlank() ||
                subjectUserId.isNullOrBlank()
            ) {
                return false
            }

            if (
                displayNameInput.isBlank() ||
                relationshipInput.isBlank()
            ) {
                return false
            }

            if (
                phoneInput.isBlank() &&
                emailInput.isBlank()
            ) {
                return false
            }

            val priority =
                priorityInput
                    .trim()
                    .toIntOrNull()
                    ?: return false

            if (priority < 0) {
                return false
            }

            if (
                preferredChannel ==
                TrustedContactChannelValue.PHONE &&
                phoneInput.isBlank()
            ) {
                return false
            }

            if (
                preferredChannel ==
                TrustedContactChannelValue.EMAIL &&
                emailInput.isBlank()
            ) {
                return false
            }

            return true
        }
}