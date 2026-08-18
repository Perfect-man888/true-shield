package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

data class AlertDeliveryAttemptListResponse(
    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("alert_id")
    val alertId: String,

    @SerializedName("items")
    val items: List<AlertDeliveryAttemptResponse>,

    @SerializedName("total")
    val total: Int,
)