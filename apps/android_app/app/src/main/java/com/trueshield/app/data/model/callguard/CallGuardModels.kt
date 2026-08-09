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
    val source: String = "local_rule",
    val confidence: Double = 0.5,
    val confirmed: Boolean = false,
)

data class CallGuardRuleItemDto(
    @SerializedName("rule_id") val ruleId: String,
    @SerializedName("match_type") val matchType: String,
    val value: String,
    val score: Int,
    val title: String,
    val explanation: String,
    val source: String,
    val confidence: Double,
    val confirmed: Boolean = false,
)

data class CallGuardRuleBundleDto(
    val version: String,
    @SerializedName("updated_at") val updatedAt: String,
    val checksum: String,
    @SerializedName("expires_at") val expiresAt: String,
    val rules: List<CallGuardRuleItemDto>,
    val disclaimer: String,
)

data class CallGuardReportRequest(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("report_type") val reportType: String,
    val note: String? = null,
)

data class CallGuardReportResponse(
    val id: String,
    @SerializedName("report_type") val reportType: String,
    @SerializedName("masked_number") val maskedNumber: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    val message: String,
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
