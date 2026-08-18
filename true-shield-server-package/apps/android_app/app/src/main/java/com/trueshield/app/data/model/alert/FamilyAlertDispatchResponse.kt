package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

/**
 * 发送家庭告警或重试失败通知后的统计结果。
 */
data class FamilyAlertDispatchResponse(
    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("alert_id")
    val alertId: String,

    @SerializedName("attempted_count")
    val attemptedCount: Int,

    @SerializedName("sent_count")
    val sentCount: Int,

    @SerializedName("failed_count")
    val failedCount: Int,

    @SerializedName("skipped_count")
    val skippedCount: Int,

    @SerializedName("already_completed_count")
    val alreadyCompletedCount: Int,

    @SerializedName("alert")
    val alert: FamilyAlertResponse,
)