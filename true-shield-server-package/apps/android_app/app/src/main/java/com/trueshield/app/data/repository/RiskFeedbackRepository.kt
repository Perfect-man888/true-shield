package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.risk.feedback.CorrectedRiskReanalysisResponse
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackRequest
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackResponse
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackStatisticsResponse
import com.trueshield.app.data.network.RiskApi
import retrofit2.Response
import java.io.IOException

/**
 * 创建、更新或读取反馈的结果。
 */
sealed interface RiskFeedbackResult {

    data class Success(
        val response: RiskFeedbackResponse,
    ) : RiskFeedbackResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : RiskFeedbackResult
}

/**
 * 风险反馈统计读取结果。
 */
sealed interface RiskFeedbackStatisticsResult {

    data class Success(
        val response: RiskFeedbackStatisticsResponse,
    ) : RiskFeedbackStatisticsResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : RiskFeedbackStatisticsResult
}

/**
 * OCR 修正后重新分析的结果。
 */
sealed interface CorrectedRiskReanalysisResult {

    data class Success(
        val response:
        CorrectedRiskReanalysisResponse,
    ) : CorrectedRiskReanalysisResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : CorrectedRiskReanalysisResult
}

/**
 * 风险反馈与 OCR 修正数据仓库。
 */
class RiskFeedbackRepository(
    private val riskApi: RiskApi,
) {

    /**
     * 创建或更新一条风险反馈。
     */
    suspend fun upsertFeedback(
        eventId: String,
        request: RiskFeedbackRequest,
    ): RiskFeedbackResult {
        val cleanedEventId =
            eventId.trim()

        if (cleanedEventId.isBlank()) {
            return RiskFeedbackResult.Error(
                message = "风险事件编号不能为空。",
            )
        }

        return try {
            val response =
                riskApi.upsertRiskFeedback(
                    eventId = cleanedEventId,
                    request = request,
                )

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                RiskFeedbackResult.Success(
                    response = responseBody,
                )
            } else {
                createFeedbackError(
                    response = response,
                    notFoundMessage =
                        "风险事件不存在或无权访问。",
                )
            }
        } catch (exception: IOException) {
            RiskFeedbackResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            RiskFeedbackResult.Error(
                message =
                    exception.message
                        ?: "提交风险反馈时发生未知错误。",
            )
        }
    }

    /**
     * 查询当前事件已有的风险反馈。
     */
    suspend fun getFeedback(
        eventId: String,
    ): RiskFeedbackResult {
        val cleanedEventId =
            eventId.trim()

        if (cleanedEventId.isBlank()) {
            return RiskFeedbackResult.Error(
                message = "风险事件编号不能为空。",
            )
        }

        return try {
            val response =
                riskApi.getRiskFeedback(
                    eventId = cleanedEventId,
                )

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                RiskFeedbackResult.Success(
                    response = responseBody,
                )
            } else {
                createFeedbackError(
                    response = response,
                    notFoundMessage =
                        "当前风险事件尚未提交反馈。",
                )
            }
        } catch (exception: IOException) {
            RiskFeedbackResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            RiskFeedbackResult.Error(
                message =
                    exception.message
                        ?: "读取风险反馈时发生未知错误。",
            )
        }
    }

    /**
     * 读取当前登录用户自己的风险反馈统计。
     */
    suspend fun getStatistics(
    ): RiskFeedbackStatisticsResult {
        return try {
            val response =
                riskApi
                    .getRiskFeedbackStatistics()

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                RiskFeedbackStatisticsResult.Success(
                    response = responseBody,
                )
            } else {
                createStatisticsError(
                    response = response,
                )
            }
        } catch (exception: IOException) {
            RiskFeedbackStatisticsResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            RiskFeedbackStatisticsResult.Error(
                message =
                    exception.message
                        ?: "读取风险反馈统计时发生未知错误。",
            )
        }
    }

    /**
     * 使用反馈中的 corrected_text 重新分析。
     */
    suspend fun reanalyzeCorrected(
        eventId: String,
    ): CorrectedRiskReanalysisResult {
        val cleanedEventId =
            eventId.trim()

        if (cleanedEventId.isBlank()) {
            return CorrectedRiskReanalysisResult.Error(
                message = "风险事件编号不能为空。",
            )
        }

        return try {
            val response =
                riskApi.reanalyzeCorrectedText(
                    eventId = cleanedEventId,
                )

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                CorrectedRiskReanalysisResult.Success(
                    response = responseBody,
                )
            } else {
                createReanalysisError(
                    response = response,
                )
            }
        } catch (exception: IOException) {
            CorrectedRiskReanalysisResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            CorrectedRiskReanalysisResult.Error(
                message =
                    exception.message
                        ?: "使用修正文字重新分析时发生未知错误。",
            )
        }
    }

    /**
     * 构建反馈接口错误。
     */
    private fun createFeedbackError(
        response: Response<*>,
        notFoundMessage: String,
    ): RiskFeedbackResult.Error {
        val statusCode =
            response.code()

        val backendDetail =
            readBackendDetail(
                response = response,
            )

        val message =
            backendDetail
                ?: when (statusCode) {
                    400 ->
                        "风险反馈请求格式不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户无权操作该风险事件。"

                    404 ->
                        notFoundMessage

                    422 ->
                        "反馈内容未通过校验，" +
                                "请检查反馈类型、OCR 状态和修正文字。"

                    500 ->
                        "服务器保存风险反馈失败，" +
                                "请稍后重试。"

                    else ->
                        "风险反馈操作失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return RiskFeedbackResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin =
                statusCode == 401,
        )
    }

    /**
     * 构建风险反馈统计接口错误。
     */
    private fun createStatisticsError(
        response: Response<*>,
    ): RiskFeedbackStatisticsResult.Error {
        val statusCode =
            response.code()

        val backendDetail =
            readBackendDetail(
                response = response,
            )

        val message =
            backendDetail
                ?: when (statusCode) {
                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户无权查看风险反馈统计。"

                    500 ->
                        "服务器生成反馈统计失败，" +
                                "请稍后重试。"

                    else ->
                        "读取风险反馈统计失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return RiskFeedbackStatisticsResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin =
                statusCode == 401,
        )
    }

    /**
     * 构建修正后重新分析错误。
     */
    private fun createReanalysisError(
        response: Response<*>,
    ): CorrectedRiskReanalysisResult.Error {
        val statusCode =
            response.code()

        val backendDetail =
            readBackendDetail(
                response = response,
            )

        val message =
            backendDetail
                ?: when (statusCode) {
                    400 ->
                        "重新分析请求格式不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户无权操作该风险事件。"

                    404 ->
                        "风险事件或风险反馈不存在。"

                    422 ->
                        "无法重新分析。" +
                                "请确认这是图片事件，" +
                                "并且已经提交修正后的 OCR 文字。"

                    500 ->
                        "服务器重新分析失败，请稍后重试。"

                    else ->
                        "重新分析失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return CorrectedRiskReanalysisResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin =
                statusCode == 401,
        )
    }

    /**
     * 尝试读取 FastAPI 返回的字符串 detail。
     */
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

            val detail =
                root.get("detail")
                    ?: return@runCatching null

            if (detail.isJsonPrimitive) {
                detail.asString
            } else {
                null
            }
        }.getOrNull()
    }
}