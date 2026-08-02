package com.trueshield.app.ui.home

data class HomeUiState(
    val isLoading: Boolean = false,
    val initialized: Boolean = false,
    val displayName: String = "",
    val callGuardEnabled: Boolean = false,
    val todayDetectionCount: Int = 0,
    val pendingAlertCount: Int = 0,
    val familyCount: Int = 0,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
)
