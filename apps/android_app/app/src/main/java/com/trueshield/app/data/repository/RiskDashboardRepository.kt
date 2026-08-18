package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.risk.dashboard.RiskDashboardResponse
import com.trueshield.app.data.network.RiskApi
import retrofit2.Response
import java.io.IOException

/**
 * 风险统计仪表盘请求结果。
 */
sealed interface RiskDashboardResult {

    data class Success(
        val response: RiskDashboardResponse,
    ) : RiskDashboardResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : RiskDashboardResult
}

/**
 * 风险统计仪表盘数据仓库。
 */
class RiskDashboardRepository(
    private val riskApi: RiskApi,
) {

    suspend fun getDashboard(
        periodDays: Int,
        familyId: String?,
    ): RiskDashboardResult {
        val safePeriodDays =
            periodDays.coerceIn(
                minimumValue = 1,
                maximumValue = 90,
            )

        return try {
            val response =
                riskApi.getRiskDashboard(
                    periodDays = safePeriodDays,
                    familyId =
                        familyId
                            ?.trim()
                            ?.takeIf { it.isNotBlank() },
                )

            val body = response.body()

            if (
                response.isSuccessful &&
                body != null
            ) {
                RiskDashboardResult.Success(
                    response = body,
                )
            } else {
                createError(response)
            }
        } catch (exception: IOException) {
            RiskDashboardResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            RiskDashboardResult.Error(
                message =
                    exception.message
                        ?: "读取风险统计仪表盘时发生未知错误。",
            )
        }
    }

    private fun createError(
        response: Response<*>,
    ): RiskDashboardResult.Error {
        val statusCode = response.code()
        val backendDetail =
            readBackendDetail(response)

        val message =
            backendDetail
                ?: when (statusCode) {
                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户无权查看该统计数据。"

                    404 ->
                        "家庭不存在或无权查看该家庭统计。"

                    422 ->
                        "统计周期或家庭编号不正确。"

                    500 ->
                        "服务器生成风险统计失败，请稍后重试。"

                    else ->
                        "读取风险统计失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return RiskDashboardResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin = statusCode == 401,
        )
    }

    private fun readBackendDetail(
        response: Response<*>,
    ): String? {
        val errorText =
            response
                .errorBody()
                ?.string()
                .orEmpty()

        if (errorText.isBlank()) {
            return null
        }

        return runCatching {
            val root =
                JsonParser
                    .parseString(errorText)
                    .asJsonObject

            val detail = root.get("detail")

            if (
                detail != null &&
                detail.isJsonPrimitive
            ) {
                detail.asString
            } else {
                null
            }
        }.getOrNull()
    }
}
