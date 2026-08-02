package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

/**
 * 家庭告警自动触发策略。
 */
data class FamilyAlertPolicyResponse(

    @SerializedName("id")
    val id: String,

    @SerializedName("family_id")
    val familyId: String,

    /**
     * 是否根据风险检测结果自动创建家庭告警。
     */
    @SerializedName("auto_create_enabled")
    val autoCreateEnabled: Boolean,

    /**
     * 创建告警后是否自动向可信联系人发送通知。
     */
    @SerializedName("auto_dispatch_enabled")
    val autoDispatchEnabled: Boolean,

    /**
     * 自动触发所要求的最低风险等级。
     *
     * 可选值：
     * low
     * medium
     * high
     */
    @SerializedName("minimum_risk_level")
    val minimumRiskLevel: String,

    /**
     * 启用自动告警的风险来源。
     *
     * 可包含：
     * text
     * image
     * url
     */
    @SerializedName("enabled_source_types")
    val enabledSourceTypes: List<String> = emptyList(),

    /**
     * 每次自动告警最多选取多少名可信联系人。
     */
    @SerializedName("max_recipients")
    val maxRecipients: Int,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

/**
 * 更新家庭告警自动触发策略的请求体。
 *
 * 当前客户端每次保存完整策略，
 * 因此这里的字段全部使用非空类型。
 */
data class UpdateFamilyAlertPolicyRequest(

    @SerializedName("auto_create_enabled")
    val autoCreateEnabled: Boolean,

    @SerializedName("auto_dispatch_enabled")
    val autoDispatchEnabled: Boolean,

    @SerializedName("minimum_risk_level")
    val minimumRiskLevel: String,

    @SerializedName("enabled_source_types")
    val enabledSourceTypes: List<String>,

    @SerializedName("max_recipients")
    val maxRecipients: Int,
)