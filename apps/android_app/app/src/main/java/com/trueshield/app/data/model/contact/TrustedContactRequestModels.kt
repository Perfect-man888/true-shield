package com.trueshield.app.data.model.contact

import com.google.gson.annotations.SerializedName

/**
 * 创建可信联系人请求。
 *
 * 对应：
 * POST /api/v1/families/{family_id}/trusted-contacts
 */
data class TrustedContactCreateRequest(
    /**
     * 该联系人保护的家庭成员编号。
     *
     * 普通家庭成员只能填写自己的 user_id。
     */
    @SerializedName("subject_user_id")
    val subjectUserId: String,

    @SerializedName("display_name")
    val displayName: String,

    @SerializedName("relationship_label")
    val relationshipLabel: String,

    val phone: String? = null,

    val email: String? = null,

    @SerializedName("preferred_channel")
    val preferredChannel: String,

    val priority: Int,

    @SerializedName("can_receive_alerts")
    val canReceiveAlerts: Boolean,
)

/**
 * 修改可信联系人请求。
 *
 * 对应：
 * PATCH /api/v1/families/{family_id}/trusted-contacts/{contact_id}
 *
 * 后端使用 PATCH，因此字段设计为可空。
 * Gson 默认不会发送 null 字段。
 */
data class TrustedContactUpdateRequest(
    @SerializedName("display_name")
    val displayName: String? = null,

    @SerializedName("relationship_label")
    val relationshipLabel: String? = null,

    val phone: String? = null,

    val email: String? = null,

    @SerializedName("preferred_channel")
    val preferredChannel: String? = null,

    val priority: Int? = null,

    @SerializedName("can_receive_alerts")
    val canReceiveAlerts: Boolean? = null,
)