package com.trueshield.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 登录成功后，后端返回的令牌信息。
 *
 * refreshToken 和 expiresIn 使用可空类型，
 * 即使后端当前没有返回这些字段也不会解析失败。
 */
data class AuthTokenResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("token_type")
    val tokenType: String,

    @SerializedName("refresh_token")
    val refreshToken: String? = null,

    @SerializedName("expires_in")
    val expiresIn: Long? = null,
)