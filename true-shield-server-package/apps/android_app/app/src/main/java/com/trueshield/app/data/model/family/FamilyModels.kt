package com.trueshield.app.data.model.family

import com.google.gson.annotations.SerializedName

/**
 * 创建家庭请求。
 *
 * 对应：
 * POST /api/v1/families
 */
data class FamilyCreateRequest(
    val name: String,
)

/**
 * 当前用户可访问的家庭。
 *
 * 创建家庭接口和家庭列表接口
 * 都使用这一结构。
 */
data class FamilyDto(
    val id: String,

    val name: String,

    @SerializedName("owner_user_id")
    val ownerUserId: String,

    /**
     * 当前已知状态：
     * active
     */
    val status: String,

    /**
     * 当前用户在家庭中的角色。
     *
     * 当前已知角色：
     * owner
     */
    @SerializedName("my_role")
    val myRole: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,
)

/**
 * 我的家庭列表响应。
 *
 * 对应：
 * GET /api/v1/families
 */
data class FamilyListResponse(
    val items: List<FamilyDto> =
        emptyList(),

    val total: Int,
)