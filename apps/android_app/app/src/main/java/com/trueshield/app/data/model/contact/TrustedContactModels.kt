package com.trueshield.app.data.model.contact

import com.google.gson.annotations.SerializedName

/**
 * 可信联系人当前已确认的状态。
 */
object TrustedContactStatusValue {

    /**
     * 正常启用。
     */
    const val ACTIVE =
        "active"

    /**
     * 已停用。
     */
    const val DISABLED =
        "disabled"
}

/**
 * 当前已确认的首选联系渠道。
 */
object TrustedContactChannelValue {

    const val PHONE =
        "phone"

    const val EMAIL =
        "email"
}

/**
 * 单条可信联系人。
 *
 * 对应创建、修改和停用接口的响应。
 */
data class TrustedContactDto(
    /**
     * 可信联系人记录编号。
     */
    val id: String,

    /**
     * 所属家庭编号。
     */
    @SerializedName("family_id")
    val familyId: String,

    /**
     * 该联系人保护的家庭成员。
     */
    @SerializedName("subject_user_id")
    val subjectUserId: String,

    /**
     * 如果邮箱或手机号对应真信盾注册用户，
     * 后端可能自动建立关联。
     *
     * 外部联系人可能为 null。
     */
    @SerializedName("linked_user_id")
    val linkedUserId: String? = null,

    /**
     * 创建该记录的用户。
     */
    @SerializedName("created_by_user_id")
    val createdByUserId: String,

    @SerializedName("display_name")
    val displayName: String,

    @SerializedName("relationship_label")
    val relationshipLabel: String,

    /**
     * 联系人可以只填写电话或只填写邮箱，
     * 因此使用可空类型。
     */
    val phone: String? = null,

    val email: String? = null,

    @SerializedName("preferred_channel")
    val preferredChannel: String,

    /**
     * 告警联系优先级。
     */
    val priority: Int,

    @SerializedName("can_receive_alerts")
    val canReceiveAlerts: Boolean,

    /**
     * 当前已确认：
     *
     * active
     * disabled
     */
    val status: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,
)

/**
 * 指定家庭和成员的可信联系人列表。
 */
data class TrustedContactListResponse(
    @SerializedName("family_id")
    val familyId: String,

    /**
     * 当前列表所属的被保护成员。
     *
     * 即使列表为空，后端仍会返回该字段，
     * 因此可以用它创建当前用户自己的联系人。
     */
    @SerializedName("subject_user_id")
    val subjectUserId: String,

    val items: List<TrustedContactDto> =
        emptyList(),

    val total: Int,
)