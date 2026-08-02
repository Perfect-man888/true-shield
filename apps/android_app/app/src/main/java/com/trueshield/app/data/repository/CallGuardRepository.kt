package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.callguard.CallGuardHelpRequest
import com.trueshield.app.data.model.callguard.CallGuardHelpResponse
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeRequest
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeResponse
import com.trueshield.app.data.network.CallGuardApi
import retrofit2.Response
import java.io.IOException

sealed interface CallGuardOperationResult<out T> {
    data class Success<T>(
        val data: T,
    ) : CallGuardOperationResult<T>

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : CallGuardOperationResult<Nothing>
}

/**
 * 通话护航数据仓库。
 */
class CallGuardRepository(
    private val callGuardApi: CallGuardApi,
) {

    suspend fun analyzeNumber(
        request: CallNumberAnalyzeRequest,
    ): CallGuardOperationResult<CallNumberAnalyzeResponse> {
        return execute(
            request = {
                callGuardApi.analyzeNumber(request)
            },
            defaultMessage = "号码风险筛查失败。",
        )
    }

    suspend fun requestFamilyHelp(
        request: CallGuardHelpRequest,
    ): CallGuardOperationResult<CallGuardHelpResponse> {
        return execute(
            request = {
                callGuardApi.requestFamilyHelp(request)
            },
            defaultMessage = "发送家庭求助失败。",
        )
    }

    private suspend fun <T> execute(
        request: suspend () -> Response<T>,
        defaultMessage: String,
    ): CallGuardOperationResult<T> {
        return try {
            val response = request()
            val body = response.body()

            if (response.isSuccessful && body != null) {
                CallGuardOperationResult.Success(body)
            } else {
                val statusCode = response.code()
                CallGuardOperationResult.Error(
                    message =
                        readBackendDetail(response)
                            ?: when (statusCode) {
                                401 -> "登录状态已失效，请重新登录。"
                                404 -> "家庭不存在或无权访问。"
                                422 -> "号码或求助参数不正确。"
                                else -> "$defaultMessage（状态码 $statusCode）"
                            },
                    statusCode = statusCode,
                    requiresLogin = statusCode == 401,
                )
            }
        } catch (exception: IOException) {
            CallGuardOperationResult.Error(
                message = "无法连接真信盾服务器，请检查后端是否已启动。",
            )
        } catch (exception: Exception) {
            CallGuardOperationResult.Error(
                message = exception.message ?: defaultMessage,
            )
        }
    }

    private fun readBackendDetail(
        response: Response<*>,
    ): String? {
        val text = response.errorBody()?.string().orEmpty()
        if (text.isBlank()) {
            return null
        }

        return runCatching {
            JsonParser
                .parseString(text)
                .asJsonObject
                .get("detail")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
        }.getOrNull()
    }
}
