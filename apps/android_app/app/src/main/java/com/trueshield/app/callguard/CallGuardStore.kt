package com.trueshield.app.callguard

import android.content.Context

/**
 * 通话护航的最小本地配置。
 *
 * 这里只保存用户选择的求助家庭，不保存通话号码、通话历史或录音。
 */
class CallGuardStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun saveSelectedFamily(
        familyId: String,
        familyName: String,
    ) {
        preferences.edit()
            .putString(KEY_FAMILY_ID, familyId)
            .putString(KEY_FAMILY_NAME, familyName)
            .apply()
    }

    fun clearSelectedFamily() {
        preferences.edit()
            .remove(KEY_FAMILY_ID)
            .remove(KEY_FAMILY_NAME)
            .apply()
    }

    fun getSelectedFamilyId(): String? =
        preferences.getString(KEY_FAMILY_ID, null)
            ?.takeIf { it.isNotBlank() }

    fun getSelectedFamilyName(): String? =
        preferences.getString(KEY_FAMILY_NAME, null)
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val PREFERENCES_NAME = "true_shield_call_guard"
        const val KEY_FAMILY_ID = "selected_family_id"
        const val KEY_FAMILY_NAME = "selected_family_name"
    }
}
