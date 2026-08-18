package com.trueshield.app.data.repository

import com.trueshield.app.data.model.AuthTokenResponse
import com.trueshield.app.data.network.ApiClient
import com.trueshield.app.data.network.AuthApi
import java.io.IOException

/**
 * 登录操作可能产生的结果。
 */
sealed interface LoginResult {

    data class Success(
        val token: AuthTokenResponse,
    ) : LoginResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
    ) : LoginResult
}

/**
 * 认证数据仓库。
 *
 * UI 和 ViewModel 不直接访问 Retrofit，
 * 统一通过 Repository 调用后端。
 */
class AuthRepository(
    private val authApi: AuthApi,
){

    suspend fun login(
        email: String,
        password: String,
    ): LoginResult {
        return try {
            val response = authApi.login(
                username = email.trim(),
                password = password,
            )

            val responseBody = response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                LoginResult.Success(
                    token = responseBody,
                )
            } else {
                LoginResult.Error(
                    message = buildErrorMessage(
                        statusCode = response.code(),
                    ),
                    statusCode = response.code(),
                )
            }
        } catch (exception: IOException) {
            LoginResult.Error(
                message = (
                        "无法连接真信盾服务器，" +
                                "请检查后端是否已启动。"
                        ),
            )
        } catch (exception: Exception) {
            LoginResult.Error(
                message = (
                        exception.message
                            ?: "登录过程中发生未知错误。"
                        ),
            )
        }
    }

    private fun buildErrorMessage(
        statusCode: Int,
    ): String {
        return when (statusCode) {
            400 -> "登录信息格式不正确。"
            401 -> "邮箱或密码错误。"
            403 -> "当前账户没有访问权限。"
            404 -> "登录接口不存在。"
            422 -> "邮箱或密码未通过格式校验。"
            500 -> "服务器内部错误，请稍后重试。"
            else -> {
                "登录失败，服务器返回状态码：$statusCode。"
            }
        }
    }
}