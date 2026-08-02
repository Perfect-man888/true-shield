package com.trueshield.app.ui.alert

import com.trueshield.app.data.model.alert.AlertDeliveryAttemptResponse
import com.trueshield.app.data.model.alert.FamilyAlertResponse

/**
 * 家庭告警详情页面状态。
 */
data class FamilyAlertDetailUiState(

    /**
     * 是否正在读取告警详情。
     */
    val isLoading: Boolean = false,

    /**
     * 是否正在执行确认、发送、重试或完成操作。
     */
    val isOperating: Boolean = false,

    /**
     * 当前家庭告警详情。
     */
    val alert: FamilyAlertResponse? = null,

    /**
     * 是否正在读取该告警的发送尝试记录。
     */
    val isLoadingDeliveryAttempts: Boolean = false,

    /**
     * 每一次发送或重试产生的投递记录。
     */
    val deliveryAttempts: List<AlertDeliveryAttemptResponse> =
        emptyList(),

    /**
     * 读取发送尝试记录时的独立错误提示。
     *
     * 该错误不会覆盖告警详情本身，避免因为记录接口失败
     * 导致整个详情页无法使用。
     */
    val deliveryAttemptsErrorMessage: String? = null,

    /**
     * 完成告警时填写的处理说明。
     */
    val resolutionNote: String = "",

    /**
     * 普通错误提示。
     */
    val errorMessage: String? = null,

    /**
     * 操作成功提示。
     */
    val operationMessage: String? = null,

    /**
     * 登录是否已经过期。
     */
    val sessionExpired: Boolean = false,
)