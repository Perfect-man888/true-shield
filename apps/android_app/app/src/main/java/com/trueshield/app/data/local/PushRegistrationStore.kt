package com.trueshield.app.data.local

import android.content.Context

/**
 * 保存 Firebase Installation ID。
 *
 * 它不是登录凭证，只用于在登录后重新同步推送设备。
 */
class PushRegistrationStore(
    context: Context,
) {

    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun saveInstallationId(
        installationId: String,
    ) {
        preferences
            .edit()
            .putString(
                KEY_INSTALLATION_ID,
                installationId,
            )
            .apply()
    }

    fun getInstallationId(): String? {
        return preferences
            .getString(
                KEY_INSTALLATION_ID,
                null,
            )
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val PREFERENCES_NAME =
            "true_shield_push"

        const val KEY_INSTALLATION_ID =
            "firebase_installation_id"
    }
}
