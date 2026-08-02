package com.trueshield.app.ui.main

data class MessageCenterUiState(
    val isLoading: Boolean = false,
    val initialized: Boolean = false,
    val familyCount: Int = 0,
    val pendingAlertCount: Int = 0,
    val acknowledgedAlertCount: Int = 0,
    val invitationCount: Int = 0,
    val recentRiskCount: Int = 0,
    val recentHighRiskCount: Int = 0,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val activeAlertCount: Int
        get() = pendingAlertCount + acknowledgedAlertCount

    val actionableCount: Int
        get() = activeAlertCount + invitationCount

    val hasActionableMessages: Boolean
        get() = actionableCount > 0
}
