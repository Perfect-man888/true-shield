package com.trueshield.app.ui.settings

import com.trueshield.app.ui.theme.TrueShieldThemeMode

data class UiSettingsUiState(
    val themeMode: TrueShieldThemeMode =
        TrueShieldThemeMode.SYSTEM,
    val elderModeEnabled: Boolean = false,
    val highRiskVoiceAlertEnabled: Boolean = true,
    val onboardingVisible: Boolean = false,
)
