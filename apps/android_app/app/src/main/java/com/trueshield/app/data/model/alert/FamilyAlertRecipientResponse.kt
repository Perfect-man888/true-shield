package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

data class FamilyAlertRecipientResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("alert_id")
    val alertId: String,

    @SerializedName("trusted_contact_id")
    val trustedContactId: String?,

    @SerializedName("linked_user_id")
    val linkedUserId: String?,

    @SerializedName("recipient_name")
    val recipientName: String,

    @SerializedName("channel")
    val channel: String,

    @SerializedName("destination")
    val destination: String?,

    @SerializedName("delivery_status")
    val deliveryStatus: String,

    @SerializedName("delivery_provider")
    val deliveryProvider: String?,

    @SerializedName("external_message_id")
    val externalMessageId: String?,

    @SerializedName("failure_reason")
    val failureReason: String?,

    @SerializedName("sent_at")
    val sentAt: String?,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,
)