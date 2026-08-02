package com.trueshield.app.data.local

import android.content.Context
import com.trueshield.app.ui.theme.TrueShieldThemeMode

class UiPreferencesStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun getThemeMode(): TrueShieldThemeMode {
        val storedValue =
            preferences.getString(
                KEY_THEME_MODE,
                TrueShieldThemeMode.SYSTEM.name,
            )

        return runCatching {
            TrueShieldThemeMode.valueOf(
                storedValue.orEmpty(),
            )
        }.getOrDefault(
            TrueShieldThemeMode.SYSTEM,
        )
    }

    fun saveThemeMode(
        themeMode: TrueShieldThemeMode,
    ) {
        preferences.edit()
            .putString(
                KEY_THEME_MODE,
                themeMode.name,
            )
            .apply()
    }

    fun isElderModeEnabled(): Boolean =
        preferences.getBoolean(
            KEY_ELDER_MODE,
            false,
        )

    fun saveElderMode(
        enabled: Boolean,
    ) {
        preferences.edit()
            .putBoolean(
                KEY_ELDER_MODE,
                enabled,
            )
            .apply()
    }

    fun isHighRiskVoiceAlertEnabled(): Boolean =
        preferences.getBoolean(
            KEY_HIGH_RISK_VOICE_ALERT,
            true,
        )

    fun saveHighRiskVoiceAlert(
        enabled: Boolean,
    ) {
        preferences.edit()
            .putBoolean(
                KEY_HIGH_RISK_VOICE_ALERT,
                enabled,
            )
            .apply()
    }

    fun hasCompletedOnboarding(): Boolean =
        preferences.getBoolean(
            KEY_ONBOARDING_COMPLETED,
            false,
        )

    fun saveOnboardingCompleted(
        completed: Boolean,
    ) {
        preferences.edit()
            .putBoolean(
                KEY_ONBOARDING_COMPLETED,
                completed,
            )
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME =
            "true_shield_ui_preferences"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ELDER_MODE = "elder_mode"
        const val KEY_HIGH_RISK_VOICE_ALERT =
            "high_risk_voice_alert"
        const val KEY_ONBOARDING_COMPLETED =
            "onboarding_completed"
    }
}
