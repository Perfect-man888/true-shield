package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

/**
 * 完成家庭告警处理时提交的数据。
 */
data class ResolveFamilyAlertRequest(
    @SerializedName("resolution_note")
    val resolutionNote: String,
)