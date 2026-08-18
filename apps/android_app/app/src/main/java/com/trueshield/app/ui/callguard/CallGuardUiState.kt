package com.trueshield.app.ui.callguard

import com.trueshield.app.data.model.callguard.CallNumberAnalyzeResponse
import com.trueshield.app.data.model.family.FamilyDto

/**
 * 通话安全护航页面状态。
 */
data class CallGuardUiState(
    val initialized: Boolean = false,
    val roleSupported: Boolean = false,
    val roleAvailable: Boolean = false,
    val roleHeld: Boolean = false,
    val families: List<FamilyDto> = emptyList(),
    val selectedFamilyId: String? = null,
    val selectedFamilyName: String? = null,
    val phoneNumber: String = "",
    val analyzedPhoneNumber: String? = null,
    val analyzeResult: CallNumberAnalyzeResponse? = null,
    val isLoadingFamilies: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isSendingHelp: Boolean = false,
    val isSyncingRules: Boolean = false,
    val isSubmittingReport: Boolean = false,
    val ruleVersion: String? = null,
    val ruleUpdatedAt: String? = null,
    val ruleExpiresAt: String? = null,
    val silenceHighRisk: Boolean = false,
    val blockConfirmedRisk: Boolean = false,
    val helpSent: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val sessionExpired: Boolean = false,
)
