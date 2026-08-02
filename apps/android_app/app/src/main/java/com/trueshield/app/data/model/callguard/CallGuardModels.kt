package com.trueshield.app.data.model.callguard

import com.google.gson.annotations.SerializedName

/**
 * 号码风险筛查请求。
 */
data class CallNumberAnalyzeRequest(
    @SerializedName("phone_number")
    val phoneNumber: String,
    val direction: String,
    @SerializedName("verification_status")
    val verificationStatus: String,
)

/**
 * 一条号码风险证据。
 */
data class CallGuardSignalDto(
    @SerializedName("signal_id")
    val signalId: String,
    val title: String,
    val score: Int,
    val explanation: String,
)

/**
 * 号码风险筛查结果。
 */
data class CallNumberAnalyzeResponse(
    @SerializedName("normalized_number")
    val normalizedNumber: String,
    @SerializedName("masked_number")
    val maskedNumber: String,
    val direction: String,
    @SerializedName("verification_status")
    val verificationStatus: String,
    @SerializedName("risk_level")
    val riskLevel: String,
    val score: Int,
    @SerializedName("is_trusted_contact")
    val isTrustedContact: Boolean,
    @SerializedName("trusted_contact_name")
    val trustedContactName: String? = null,
    val summary: String,
    val signals: List<CallGuardSignalDto> = emptyList(),
    val actions: List<String> = emptyList(),
    @SerializedName("rule_version")
    val ruleVersion: String,
    val persisted: Boolean = false,
)

/**
 * 通话中一键家庭求助请求。
 */
data class CallGuardHelpRequest(
    @SerializedName("family_id")
    val familyId: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    val direction: String,
    @SerializedName("verification_status")
    val verificationStatus: String,
    @SerializedName("risk_level")
    val riskLevel: String,
    val score: Int,
    val summary: String,
)

/**
 * 家庭求助创建结果。
 */
data class CallGuardHelpResponse(
    @SerializedName("family_id")
    val familyId: String,
    @SerializedName("event_id")
    val eventId: String,
    @SerializedName("alert_id")
    val alertId: String,
    @SerializedName("recipient_count")
    val recipientCount: Int,
    @SerializedName("sent_count")
    val sentCount: Int,
    @SerializedName("failed_count")
    val failedCount: Int,
    @SerializedName("skipped_count")
    val skippedCount: Int,
    val message: String,
)
