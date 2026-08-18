package com.trueshield.app.data.repository

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.trueshield.app.data.model.account.ChangePasswordRequest
import com.trueshield.app.data.model.account.PasswordResetCodeRequest
import com.trueshield.app.data.model.account.PasswordResetCodeResponse
import com.trueshield.app.data.model.account.PasswordResetConfirmRequest
import com.trueshield.app.data.model.account.RegisterUserRequest
import com.trueshield.app.data.model.account.UserAccountResponse
import com.trueshield.app.data.network.AuthApi
import com.trueshield.app.data.network.UserApi
import retrofit2.Response
import java.io.IOException

/**
 * 注册、读取资料和修改资料的统一结果。
 */
sealed interface AccountOperationResult<out T> {

    data class Success<T>(
        val data: T,
    ) : AccountOperationResult<T>

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : AccountOperationResult<Nothing>
}

/**
 * 账户资料仓库。
 */
class AccountRepository(
    private val authApi: AuthApi,
    private val userApi: UserApi,
) {

    suspend fun register(
        email: String,
        phone: String,
        displayName: String,
        password: String,
    ): AccountOperationResult<UserAccountResponse> {
        return try {
            val response = authApi.register(
                RegisterUserRequest(
                    email = email.trim().lowercase(),
                    phone = phone
                        .trim()
                        .ifBlank { null },
                    displayName = displayName.trim(),
                    password = password,
                ),
            )

            handleResponse(
                response = response,
                defaultErrorMessage =
                    "注册失败，请稍后重试。",
            )
        } catch (exception: IOException) {
            AccountOperationResult.Error(
                message =
                    "无法连接真信盾服务器，请检查后端是否已启动。",
            )
        } catch (exception: Exception) {
            AccountOperationResult.Error(
                message = exception.message
                    ?: "注册过程中发生未知错误。",
            )
        }
    }

    suspend fun requestPasswordResetCode(
        email: String,
    ): AccountOperationResult<PasswordResetCodeResponse> {
        return try {
            handleResponse(
                response = authApi.requestPasswordResetCode(
                    PasswordResetCodeRequest(
                        email = email.trim().lowercase(),
                    ),
                ),
                defaultErrorMessage =
                    "发送密码重置验证码失败，请稍后重试。",
            )
        } catch (exception: IOException) {
            AccountOperationResult.Error(
                message =
                    "无法连接真信盾服务器，请检查网络和后端服务。",
            )
        } catch (exception: Exception) {
            AccountOperationResult.Error(
                message = exception.message
                    ?: "申请密码重置时发生未知错误。",
            )
        }
    }

    suspend fun confirmPasswordReset(
        email: String,
        code: String,
        newPassword: String,
    ): AccountOperationResult<Unit> {
        return try {
            val response = authApi.confirmPasswordReset(
                PasswordResetConfirmRequest(
                    email = email.trim().lowercase(),
                    code = code.trim(),
                    newPassword = newPassword,
                ),
            )

            if (response.isSuccessful) {
                AccountOperationResult.Success(Unit)
            } else {
                handleErrorResponse(
                    response = response,
                    defaultErrorMessage =
                        "重置密码失败，请稍后重试。",
                )
            }
        } catch (exception: IOException) {
            AccountOperationResult.Error(
                message =
                    "无法连接真信盾服务器，请检查网络和后端服务。",
            )
        } catch (exception: Exception) {
            AccountOperationResult.Error(
                message = exception.message
                    ?: "重置密码时发生未知错误。",
            )
        }
    }

    suspend fun getCurrentUser():
            AccountOperationResult<UserAccountResponse> {
        return try {
            handleResponse(
                response = userApi.getCurrentUser(),
                defaultErrorMessage =
                    "读取个人资料失败，请稍后重试。",
            )
        } catch (exception: IOException) {
            AccountOperationResult.Error(
                message =
                    "无法连接真信盾服务器，请检查网络和后端服务。",
            )
        } catch (exception: Exception) {
            AccountOperationResult.Error(
                message = exception.message
                    ?: "读取个人资料时发生未知错误。",
            )
        }
    }

    suspend fun updateCurrentUser(
        displayName: String,
        phone: String,
    ): AccountOperationResult<UserAccountResponse> {
        val request = JsonObject().apply {
            addProperty(
                "display_name",
                displayName.trim(),
            )

            val normalizedPhone = phone.trim()

            if (normalizedPhone.isBlank()) {
                add("phone", JsonNull.INSTANCE)
            } else {
                addProperty(
                    "phone",
                    normalizedPhone,
                )
            }
        }

        return try {
            handleResponse(
                response = userApi.updateCurrentUser(
                    request = request,
                ),
                defaultErrorMessage =
                    "保存个人资料失败，请稍后重试。",
            )
        } catch (exception: IOException) {
            AccountOperationResult.Error(
                message =
                    "无法连接真信盾服务器，请检查网络和后端服务。",
            )
        } catch (exception: Exception) {
            AccountOperationResult.Error(
                message = exception.message
                    ?: "保存个人资料时发生未知错误。",
            )
        }
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): AccountOperationResult<Unit> {
        return try {
            val response = userApi.changePassword(
                ChangePasswordRequest(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                ),
            )

            if (response.isSuccessful) {
                AccountOperationResult.Success(Unit)
            } else {
                handleErrorResponse(
                    response = response,
                    defaultErrorMessage =
                        "修改密码失败，请稍后重试。",
                )
            }
        } catch (exception: IOException) {
            AccountOperationResult.Error(
                message =
                    "无法连接真信盾服务器，请检查网络和后端服务。",
            )
        } catch (exception: Exception) {
            AccountOperationResult.Error(
                message = exception.message
                    ?: "修改密码时发生未知错误。",
            )
        }
    }

    private fun <T> handleResponse(
        response: Response<T>,
        defaultErrorMessage: String,
    ): AccountOperationResult<T> {
        val body = response.body()

        if (
            response.isSuccessful
            && body != null
        ) {
            return AccountOperationResult.Success(
                data = body,
            )
        }

        return handleErrorResponse(
            response = response,
            defaultErrorMessage = defaultErrorMessage,
        )
    }

    private fun handleErrorResponse(
        response: Response<*>,
        defaultErrorMessage: String,
    ): AccountOperationResult.Error {
        val statusCode = response.code()
        val backendDetail = readBackendDetail(
            response = response,
        )

        val message = backendDetail
            ?: when (statusCode) {
                400 -> "提交的账户信息不正确。"
                401 -> "登录状态已失效，请重新登录。"
                403 -> "当前账户不可用或没有访问权限。"
                404 -> "账户接口不存在，请确认后端版本。"
                409 -> "邮箱或手机号已被使用。"
                422 -> "账户信息未通过格式校验。"
                500 -> "服务器内部错误，请稍后重试。"
                else ->
                    "$defaultErrorMessage" +
                            "服务器返回状态码：$statusCode。"
            }

        return AccountOperationResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin = statusCode == 401,
        )
    }

    /**
     * 读取 FastAPI 返回的字符串 detail。
     */
    private fun readBackendDetail(
        response: Response<*>,
    ): String? {
        val errorText = response
            .errorBody()
            ?.string()
            .orEmpty()

        if (errorText.isBlank()) {
            return null
        }

        return runCatching {
            val root = JsonParser
                .parseString(errorText)
                .asJsonObject

            val detail = root.get("detail")
                ?: return@runCatching null

            if (detail.isJsonPrimitive) {
                detail.asString
            } else {
                null
            }
        }.getOrNull()
    }
}
