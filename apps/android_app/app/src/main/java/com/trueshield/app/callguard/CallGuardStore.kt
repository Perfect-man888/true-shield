package com.trueshield.app.callguard

import android.content.Context
import com.google.gson.Gson
import com.trueshield.app.data.model.callguard.CallGuardRuleBundleDto
import java.time.Instant

/**
 * 通话护航的最小本地配置。
 *
 * 这里只保存用户选择的求助家庭，不保存通话号码、通话历史或录音。
 */
class CallGuardStore(
    context: Context,
) {
    private val gson = Gson()
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

    fun saveRuleBundle(bundle: CallGuardRuleBundleDto) {
        preferences.edit()
            .putString(KEY_RULE_BUNDLE, gson.toJson(bundle))
            .apply()
    }

    fun getRuleBundle(): CallGuardRuleBundleDto? =
        preferences.getString(KEY_RULE_BUNDLE, null)
            ?.let { json ->
                runCatching {
                    gson.fromJson(json, CallGuardRuleBundleDto::class.java)
                }.getOrNull()
            }

    fun getUsableRuleBundle(): CallGuardRuleBundleDto? =
        getRuleBundle()?.takeUnless { bundle ->
            runCatching {
                Instant.parse(bundle.expiresAt).isBefore(Instant.now())
            }.getOrDefault(true)
        }

    fun setSilenceHighRisk(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SILENCE_HIGH_RISK, enabled).apply()
    }

    fun shouldSilenceHighRisk(): Boolean =
        preferences.getBoolean(KEY_SILENCE_HIGH_RISK, false)

    fun setBlockConfirmedRisk(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BLOCK_CONFIRMED_RISK, enabled).apply()
    }

    fun shouldBlockConfirmedRisk(): Boolean =
        preferences.getBoolean(KEY_BLOCK_CONFIRMED_RISK, false)

    private companion object {
        const val PREFERENCES_NAME = "true_shield_call_guard"
        const val KEY_FAMILY_ID = "selected_family_id"
        const val KEY_FAMILY_NAME = "selected_family_name"
        const val KEY_RULE_BUNDLE = "rule_bundle"
        const val KEY_SILENCE_HIGH_RISK = "silence_high_risk"
        const val KEY_BLOCK_CONFIRMED_RISK = "block_confirmed_risk"
    }
}
