package com.trueshield.app.data.model.family

import com.google.gson.annotations.SerializedName

/**
 * 当前已经通过后端验证的家庭邀请角色。
 */
object FamilyInvitationRoleValue {

    /**
     * 普通家庭成员。
     */
    const val MEMBER =
        "member"
}

/**
 * 创建家庭邀请请求。
 *
 * 对应：
 * POST /api/v1/families/{family_id}/invitations
 */
data class FamilyInvitationCreateRequest(
    @SerializedName("invitee_email")
    val inviteeEmail: String,

    /**
     * 第一版只开放 member。
     */
    val role: String =
        FamilyInvitationRoleValue.MEMBER,
)

/**
 * 创建邀请、拒绝邀请时返回的完整邀请记录。
 */
data class FamilyInvitationDto(
    /**
     * 邀请编号。
     */
    val id: String,

    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("inviter_user_id")
    val inviterUserId: String,

    @SerializedName("invitee_email")
    val inviteeEmail: String,

    val role: String,

    /**
     * 当前已确认：
     *
     * pending
     * declined
     */
    val status: String,

    @SerializedName("expires_at")
    val expiresAt: String,

    /**
     * pending 时为 null；
     * 接受或拒绝后为处理时间。
     */
    @SerializedName("responded_at")
    val respondedAt: String? = null,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,
)

/**
 * 当前用户收到的单条家庭邀请。
 *
 * 收到邀请接口会额外返回家庭名称
 * 和邀请人的显示名称。
 */
data class ReceivedFamilyInvitationDto(
    val id: String,

    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("family_name")
    val familyName: String,

    @SerializedName("inviter_user_id")
    val inviterUserId: String,

    /**
     * 用户可能没有设置显示名称，
     * 因此使用可空类型。
     */
    @SerializedName("inviter_display_name")
    val inviterDisplayName: String? = null,

    @SerializedName("invitee_email")
    val inviteeEmail: String,

    val role: String,

    val status: String,

    @SerializedName("expires_at")
    val expiresAt: String,

    @SerializedName("created_at")
    val createdAt: String,
)

/**
 * 当前用户收到的家庭邀请列表。
 */
data class ReceivedFamilyInvitationListResponse(
    val items:
    List<ReceivedFamilyInvitationDto> =
        emptyList(),

    val total: Int,
)

/**
 * 接受家庭邀请后的响应。
 *
 * 接受成功后，当前用户已经正式加入家庭。
 */
data class FamilyInvitationAcceptResponse(
    @SerializedName("invitation_id")
    val invitationId: String,

    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("family_name")
    val familyName: String,

    val role: String,

    /**
     * 成功时为 accepted。
     */
    val status: String,

    @SerializedName("joined_at")
    val joinedAt: String,
)