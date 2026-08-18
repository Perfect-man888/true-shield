package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.alert.FamilyAlertPolicyResponse
import com.trueshield.app.data.model.alert.UpdateFamilyAlertPolicyRequest
import com.trueshield.app.data.network.FamilyApi
import java.io.IOException
import retrofit2.Response

/**
 * 家庭告警自动触发策略 Repository。
 *
 * 作用：
 * 1. 查询家庭当前的告警触发策略。
 * 2. 更新家庭告警触发策略。
 * 3. 统一处理 HTTP 错误、登录过期和网络异常。
 *
 * ViewModel 不应直接调用 FamilyApi，
 * 而应该通过本 Repository 访问后端。
 */
class FamilyAlertPolicyRepository(
    private val familyApi: FamilyApi,
) {

    /**
     * 查询指定家庭的自动告警策略。
     */
    suspend fun getAlertPolicy(
        familyId: String,
    ): FamilyAlertPolicyRepositoryResult<FamilyAlertPolicyResponse> {
        return executeRequest {
            familyApi.getFamilyAlertPolicy(
                familyId = familyId,
            )
        }
    }

    /**
     * 更新指定家庭的自动告警策略。
     */
    suspend fun updateAlertPolicy(
        familyId: String,
        request: UpdateFamilyAlertPolicyRequest,
    ): FamilyAlertPolicyRepositoryResult<FamilyAlertPolicyResponse> {
        return executeRequest {
            familyApi.updateFamilyAlertPolicy(
                familyId = familyId,
                request = request,
            )
        }
    }

    /**
     * 统一执行 Retrofit 请求。
     */
    private suspend fun <T> executeRequest(
        requestBlock: suspend () -> Response<T>,
    ): FamilyAlertPolicyRepositoryResult<T> {
        return try {
            val response = requestBlock()

            when {
                response.isSuccessful -> {
                    val responseBody = response.body()

                    if (responseBody != null) {
                        FamilyAlertPolicyRepositoryResult.Success(
                            data = responseBody,
                        )
                    } else {
                        FamilyAlertPolicyRepositoryResult.Error(
                            message = "服务器返回成功，但响应内容为空。",
                            statusCode = response.code(),
                        )
                    }
                }

                response.code() == 401 -> {
                    FamilyAlertPolicyRepositoryResult.SessionExpired
                }

                else -> {
                    FamilyAlertPolicyRepositoryResult.Error(
                        message = parseErrorMessage(response),
                        statusCode = response.code(),
                    )
                }
            }
        } catch (exception: IOException) {
            FamilyAlertPolicyRepositoryResult.Error(
                message =
                    "无法连接真信盾服务器，请检查后端是否已经启动，" +
                            "并检查手机或模拟器的网络连接。",
            )
        } catch (exception: Exception) {
            FamilyAlertPolicyRepositoryResult.Error(
                message =
                    exception.message
                        ?: "读取家庭告警策略时发生未知错误。",
            )
        }
    }

    /**
     * 从 FastAPI 错误响应中读取 detail 字段。
     *
     * FastAPI 常见错误结构：
     *
     * {
     *   "detail": "错误说明"
     * }
     */
    private fun parseErrorMessage(
        response: Response<*>,
    ): String {
        val rawErrorBody =
            try {
                response.errorBody()
                    ?.string()
                    .orEmpty()
            } catch (_: Exception) {
                ""
            }

        if (rawErrorBody.isBlank()) {
            return "请求失败，HTTP 状态码：${response.code()}。"
        }

        return try {
            val jsonElement =
                JsonParser.parseString(rawErrorBody)

            if (!jsonElement.isJsonObject) {
                rawErrorBody
            } else {
                val detailElement =
                    jsonElement
                        .asJsonObject
                        .get("detail")

                when {
                    detailElement == null -> {
                        rawErrorBody
                    }

                    detailElement.isJsonPrimitive -> {
                        detailElement.asString
                    }

                    else -> {
                        detailElement.toString()
                    }
                }
            }
        } catch (_: Exception) {
            rawErrorBody
        }
    }
}

/**
 * 告警策略 Repository 返回结果。
 */
sealed class FamilyAlertPolicyRepositoryResult<out T> {

    /**
     * 请求成功。
     */
    data class Success<T>(
        val data: T,
    ) : FamilyAlertPolicyRepositoryResult<T>()

    /**
     * 普通请求错误。
     */
    data class Error(
        val message: String,
        val statusCode: Int? = null,
    ) : FamilyAlertPolicyRepositoryResult<Nothing>()

    /**
     * Token 已失效，需要重新登录。
     */
    data object SessionExpired :
        FamilyAlertPolicyRepositoryResult<Nothing>()
}