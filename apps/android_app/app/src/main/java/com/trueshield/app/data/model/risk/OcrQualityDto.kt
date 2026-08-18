package com.trueshield.app.data.model.risk

import com.google.gson.annotations.SerializedName

/**
 * OCR 识别质量评估。
 */
data class OcrQualityDto(
    @SerializedName("line_count")
    val lineCount: Int,

    @SerializedName("average_confidence")
    val averageConfidence: Double,

    @SerializedName("minimum_confidence")
    val minimumConfidence: Double,

    @SerializedName("needs_manual_review")
    val needsManualReview: Boolean,

    @SerializedName("review_reason")
    val reviewReason: String? = null,
)