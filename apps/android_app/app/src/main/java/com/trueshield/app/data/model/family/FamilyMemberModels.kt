package com.trueshield.app.data.model.family

import com.google.gson.annotations.SerializedName

/**
 * 家庭成员。
 */
data class FamilyMemberDto(
    /**
     * 家庭成员关系记录 ID。
     */
    val id: String,

    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("user_id")
    val userId: String,

    /**
     * 用户可能没有设置显示名称，
     * 因此使用可空类型。
     */
    @SerializedName("display_name")
    val displayName: String? = null,

    /**
     * 用户可能仅使用手机号或邮箱注册，
     * 因此两者都使用可空类型。
     */
    val email: String? = null,

    val phone: String? = null,

    /**
     * 当前已知角色：
     * owner
     *
     * 后续可能还有 admin、member 等。
     */
    val role: String,

    /**
     * 当前已知状态：
     * active
     */
    val status: String,

    @SerializedName("joined_at")
    val joinedAt: String,
)

/**
 * 指定家庭的成员列表。
 *
 * 对应：
 * GET /api/v1/families/{family_id}/members
 */
data class FamilyMemberListResponse(
    @SerializedName("family_id")
    val familyId: String,

    val items: List<FamilyMemberDto> =
        emptyList(),

    val total: Int,
)