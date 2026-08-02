package com.trueshield.app.data.local

import android.content.Context
import com.trueshield.app.data.model.AuthTokenResponse

/**
 * 第一版登录令牌存储。
 *
 * 当前使用 SharedPreferences，后续正式发布前
 * 可以升级为更安全的令牌存储方案。
 */
class TokenStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun saveToken(
        token: AuthTokenResponse,
    ) {
        preferences
            .edit()
            .putString(
                KEY_ACCESS_TOKEN,
                token.accessToken,
            )
            .putString(
                KEY_TOKEN_TYPE,
                token.tokenType,
            )
            .putString(
                KEY_REFRESH_TOKEN,
                token.refreshToken,
            )
            .apply()
    }

    fun getAccessToken(): String? {
        return preferences.getString(
            KEY_ACCESS_TOKEN,
            null,
        )
    }

    fun hasToken(): Boolean {
        return !getAccessToken().isNullOrBlank()
    }

    fun clear() {
        preferences
            .edit()
            .clear()
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME =
            "true_shield_auth"

        const val KEY_ACCESS_TOKEN =
            "access_token"

        const val KEY_TOKEN_TYPE =
            "token_type"

        const val KEY_REFRESH_TOKEN =
            "refresh_token"
    }
}