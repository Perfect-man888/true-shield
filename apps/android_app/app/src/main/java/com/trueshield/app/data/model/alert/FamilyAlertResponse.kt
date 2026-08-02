package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

data class FamilyAlertResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("risk_event_id")
    val riskEventId: String,

    @SerializedName("subject_user_id")
    val subjectUserId: String,

    @SerializedName("risk_level")
    val riskLevel: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("summary")
    val summary: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("acknowledged_by_user_id")
    val acknowledgedByUserId: String?,

    @SerializedName("acknowledged_at")
    val acknowledgedAt: String?,

    @SerializedName("resolved_by_user_id")
    val resolvedByUserId: String?,

    @SerializedName("resolved_at")
    val resolvedAt: String?,

    @SerializedName("resolution_note")
    val resolutionNote: String?,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,

    @SerializedName("recipients")
    val recipients: List<FamilyAlertRecipientResponse> = emptyList(),
)