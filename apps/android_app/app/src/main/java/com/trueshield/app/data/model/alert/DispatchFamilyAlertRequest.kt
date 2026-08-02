package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

/**
 * 手动发送家庭告警时提交的数据。
 *
 * simulatedFailureRecipientIds 仅用于当前开发阶段，
 * 可以指定哪些告警接收记录模拟发送失败。
 *
 * 正常发送时传入空列表即可。
 */
data class DispatchFamilyAlertRequest(
    @SerializedName(
        "simulated_failure_recipient_ids"
    )
    val simulatedFailureRecipientIds:
    List<String> = emptyList(),
)