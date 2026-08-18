package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

data class AlertDeliveryAttemptResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("recipient_id")
    val recipientId: String,

    @SerializedName("attempt_number")
    val attemptNumber: Int,

    @SerializedName("channel")
    val channel: String,

    @SerializedName("destination")
    val destination: String?,

    @SerializedName("provider")
    val provider: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("external_message_id")
    val externalMessageId: String?,

    @SerializedName("failure_reason")
    val failureReason: String?,

    @SerializedName("attempted_at")
    val attemptedAt: String,
)