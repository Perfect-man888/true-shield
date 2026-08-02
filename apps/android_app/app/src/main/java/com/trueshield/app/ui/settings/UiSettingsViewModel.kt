package com.trueshield.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.trueshield.app.data.local.UiPreferencesStore
import com.trueshield.app.ui.theme.TrueShieldThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UiSettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val preferencesStore =
        UiPreferencesStore(application)

    private val _uiState =
        MutableStateFlow(
            UiSettingsUiState(
                themeMode =
                    preferencesStore.getThemeMode(),
                elderModeEnabled =
                    preferencesStore
                        .isElderModeEnabled(),
                highRiskVoiceAlertEnabled =
                    preferencesStore
                        .isHighRiskVoiceAlertEnabled(),
                onboardingVisible =
                    !preferencesStore
                        .hasCompletedOnboarding(),
            ),
        )

    val uiState: StateFlow<UiSettingsUiState> =
        _uiState.asStateFlow()

    fun setThemeMode(
        themeMode: TrueShieldThemeMode,
    ) {
        preferencesStore.saveThemeMode(themeMode)
        _uiState.value =
            _uiState.value.copy(
                themeMode = themeMode,
            )
    }

    fun setElderModeEnabled(
        enabled: Boolean,
    ) {
        preferencesStore.saveElderMode(enabled)
        _uiState.value =
            _uiState.value.copy(
                elderModeEnabled = enabled,
            )
    }

    fun setHighRiskVoiceAlertEnabled(
        enabled: Boolean,
    ) {
        preferencesStore.saveHighRiskVoiceAlert(enabled)
        _uiState.value =
            _uiState.value.copy(
                highRiskVoiceAlertEnabled = enabled,
            )
    }

    fun completeOnboarding() {
        preferencesStore.saveOnboardingCompleted(true)
        _uiState.value =
            _uiState.value.copy(
                onboardingVisible = false,
            )
    }

    fun showOnboarding() {
        _uiState.value =
            _uiState.value.copy(
                onboardingVisible = true,
            )
    }
}
