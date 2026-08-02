package com.trueshield.app.ui.family

import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.data.model.family.FamilyMemberDto
import com.trueshield.app.data.model.family.ReceivedFamilyInvitationDto

/**
 * 邮箱地址最大长度。
 */
const val FAMILY_INVITEE_EMAIL_MAX_LENGTH =
    254

/**
 * 家庭中心页面状态。
 */
data class FamilyCenterUiState(

    val families: List<FamilyDto> =
        emptyList(),

    val totalFamilies: Int = 0,

    /**
     * 当前用户收到的待处理邀请。
     */
    val receivedInvitations:
    List<ReceivedFamilyInvitationDto> =
        emptyList(),

    val receivedInvitationCount: Int = 0,

    val familyNameInput: String = "",

    /**
     * 向当前选中家庭邀请成员时输入的邮箱。
     */
    val inviteeEmailInput: String = "",

    val isInitialLoading: Boolean = false,

    val isRefreshing: Boolean = false,

    val isCreatingFamily: Boolean = false,

    val isLoadingMembers: Boolean = false,

    val isSendingInvitation: Boolean = false,

    /**
     * 当前正在接受或拒绝的邀请编号。
     */
    val processingInvitationId: String? = null,

    val selectedFamilyId: String? = null,

    val selectedFamilyMembers:
    List<FamilyMemberDto> =
        emptyList(),

    val selectedFamilyMemberTotal: Int = 0,

    val successMessage: String? = null,

    val errorMessage: String? = null,

    val sessionExpired: Boolean = false,
) {

    val selectedFamily: FamilyDto?
        get() =
            families.firstOrNull {
                it.id == selectedFamilyId
            }

    /**
     * 当前用户是否可以向选中家庭邀请成员。
     */
    val selectedFamilyCanInvite: Boolean
        get() {
            val role =
                selectedFamily
                    ?.myRole
                    ?.lowercase()
                    ?: return false

            return role == "owner" ||
                    role == "admin"
        }

    val isBusy: Boolean
        get() =
            isInitialLoading ||
                    isRefreshing ||
                    isCreatingFamily ||
                    isLoadingMembers ||
                    isSendingInvitation ||
                    processingInvitationId != null

    val canCreateFamily: Boolean
        get() =
            familyNameInput.isNotBlank() &&
                    !isBusy

    val canSendInvitation: Boolean
        get() =
            selectedFamilyCanInvite &&
                    inviteeEmailInput.isNotBlank() &&
                    !isBusy
}