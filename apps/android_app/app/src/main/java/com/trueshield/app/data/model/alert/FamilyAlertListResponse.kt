package com.trueshield.app.data.model.alert

import com.google.gson.annotations.SerializedName

data class FamilyAlertListResponse(
    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("items")
    val items: List<FamilyAlertResponse>,

    @SerializedName("total")
    val total: Int,
)