package com.trueshield.app.ui.alert.policy

import com.trueshield.app.data.model.alert.FamilyAlertPolicyResponse

/**
 * 家庭告警自动触发策略页面状态。
 */
data class FamilyAlertPolicyUiState(

    /**
     * 当前正在读取策略。
     */
    val isLoading: Boolean = false,

    /**
     * 当前正在保存策略。
     */
    val isSaving: Boolean = false,

    /**
     * 后端返回的原始策略。
     */
    val policy: FamilyAlertPolicyResponse? = null,

    /**
     * 当前选中的家庭编号。
     */
    val familyId: String? = null,

    /**
     * 是否自动根据高风险事件创建家庭告警。
     */
    val autoCreateEnabled: Boolean = false,

    /**
     * 创建告警后是否自动向可信联系人发送。
     */
    val autoDispatchEnabled: Boolean = false,

    /**
     * 自动触发告警的最低风险等级。
     *
     * 后端可接受：
     * low
     * medium
     * high
     */
    val minimumRiskLevel: String = "high",

    /**
     * 启用自动告警的检测来源。
     *
     * 可选值：
     * text
     * image
     * url
     */
    val enabledSourceTypes: Set<String> = setOf(
        "text",
        "image",
        "url",
    ),

    /**
     * 每次告警最多选择多少名接收人。
     *
     * 使用字符串保存，便于 TextField 直接绑定。
     */
    val maxRecipientsText: String = "3",

    /**
     * 是否存在尚未保存的修改。
     */
    val hasUnsavedChanges: Boolean = false,

    /**
     * 普通错误信息。
     */
    val errorMessage: String? = null,

    /**
     * 操作成功提示。
     */
    val successMessage: String? = null,

    /**
     * Token 是否已经失效。
     */
    val sessionExpired: Boolean = false,
) {

    /**
     * 当前是否允许点击保存按钮。
     */
    val canSave: Boolean
        get() {
            if (isLoading || isSaving) {
                return false
            }

            if (!hasUnsavedChanges) {
                return false
            }

            val maxRecipients = maxRecipientsText.toIntOrNull()

            if (maxRecipients == null || maxRecipients <= 0) {
                return false
            }

            /*
             * 自动发送必须建立在自动创建告警的基础上。
             */
            if (autoDispatchEnabled && !autoCreateEnabled) {
                return false
            }

            /*
             * 开启自动创建时，至少需要选择一种检测来源。
             */
            if (
                autoCreateEnabled &&
                enabledSourceTypes.isEmpty()
            ) {
                return false
            }

            return minimumRiskLevel in setOf(
                "low",
                "medium",
                "high",
            )
        }
}