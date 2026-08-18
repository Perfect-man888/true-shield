package com.trueshield.app.data.model.account

import com.google.gson.annotations.SerializedName

/**
 * 用户注册请求。
 */
data class RegisterUserRequest(
    @SerializedName("email")
    val email: String,

    @SerializedName("phone")
    val phone: String? = null,

    @SerializedName("display_name")
    val displayName: String,

    @SerializedName("password")
    val password: String,
)

/**
 * 修改当前登录密码请求。
 */
data class ChangePasswordRequest(
    @SerializedName("current_password")
    val currentPassword: String,

    @SerializedName("new_password")
    val newPassword: String,
)

/**
 * 后端返回的当前用户资料。
 */
data class UserAccountResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("phone")
    val phone: String? = null,

    @SerializedName("display_name")
    val displayName: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,
)

/**
 * 申请发送密码重置验证码。
 */
data class PasswordResetCodeRequest(
    @SerializedName("email")
    val email: String,
)

/**
 * 密码重置验证码申请结果。
 */
data class PasswordResetCodeResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("expires_in")
    val expiresIn: Int,

    @SerializedName("debug_code")
    val debugCode: String? = null,
)

/**
 * 使用验证码确认密码重置。
 */
data class PasswordResetConfirmRequest(
    @SerializedName("email")
    val email: String,

    @SerializedName("code")
    val code: String,

    @SerializedName("new_password")
    val newPassword: String,
)
