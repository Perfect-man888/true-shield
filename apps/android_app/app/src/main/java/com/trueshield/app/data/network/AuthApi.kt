package com.trueshield.app.data.network

import com.trueshield.app.data.model.AuthTokenResponse
import com.trueshield.app.data.model.account.PasswordResetCodeRequest
import com.trueshield.app.data.model.account.PasswordResetCodeResponse
import com.trueshield.app.data.model.account.PasswordResetConfirmRequest
import com.trueshield.app.data.model.account.RegisterUserRequest
import com.trueshield.app.data.model.account.UserAccountResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * 真信盾账户认证接口。
 */
interface AuthApi {

    /**
     * 创建新用户账户。
     */
    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterUserRequest,
    ): Response<UserAccountResponse>

    /**
     * 向用户邮箱发送密码重置验证码。
     */
    @POST("api/v1/auth/password-reset/request")
    suspend fun requestPasswordResetCode(
        @Body request: PasswordResetCodeRequest,
    ): Response<PasswordResetCodeResponse>

    /**
     * 校验验证码并设置新密码。
     */
    @POST("api/v1/auth/password-reset/confirm")
    suspend fun confirmPasswordReset(
        @Body request: PasswordResetConfirmRequest,
    ): Response<Unit>

    /**
     * FastAPI OAuth2 登录接口使用表单格式，
     * username 字段中填写用户邮箱。
     */
    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Field("username")
        username: String,

        @Field("password")
        password: String,

        @Field("grant_type")
        grantType: String = "password",
    ): Response<AuthTokenResponse>
}