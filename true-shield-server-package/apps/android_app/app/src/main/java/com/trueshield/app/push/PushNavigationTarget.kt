package com.trueshield.app.push

import android.content.Intent

/**
 * 从系统推送进入 App 后需要打开的业务页面。
 */
data class PushNavigationTarget(
    val familyId: String,
    val alertId: String,
) {
    companion object {
        const val EXTRA_TYPE =
            "type"
        const val EXTRA_FAMILY_ID =
            "family_id"
        const val EXTRA_ALERT_ID =
            "alert_id"

        private const val FAMILY_ALERT_TYPE =
            "family_alert"

        fun fromIntent(
            intent: Intent?,
        ): PushNavigationTarget? {
            if (intent == null) {
                return null
            }

            val type = intent
                .getStringExtra(EXTRA_TYPE)
                ?.trim()

            if (type != FAMILY_ALERT_TYPE) {
                return null
            }

            val familyId = intent
                .getStringExtra(EXTRA_FAMILY_ID)
                ?.trim()
                .orEmpty()

            val alertId = intent
                .getStringExtra(EXTRA_ALERT_ID)
                ?.trim()
                .orEmpty()

            if (
                familyId.isBlank() ||
                alertId.isBlank()
            ) {
                return null
            }

            return PushNavigationTarget(
                familyId = familyId,
                alertId = alertId,
            )
        }
    }
}
