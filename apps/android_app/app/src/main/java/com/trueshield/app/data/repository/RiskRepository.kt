package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.risk.RiskEventDetailResponse
import com.trueshield.app.data.model.risk.TextRiskContext
import com.trueshield.app.data.model.risk.TextRiskRequest
import com.trueshield.app.data.model.risk.TextRiskResponse
import com.trueshield.app.data.network.RiskApi
import retrofit2.Response
import com.trueshield.app.data.model.risk.ImageRiskResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.trueshield.app.data.model.risk.url.UrlRiskRequest
import com.trueshield.app.data.model.risk.url.UrlRiskResponse
import com.trueshield.app.data.model.risk.history.RiskEventListResponse

/**
 * 后端允许的最大图片大小：10 MB。
 */
private const val MAX_IMAGE_BYTES =
    10 * 1024 * 1024


/**
 * 与后端 URLRiskAnalysisRequest 保持一致。
 */
private const val MAX_URL_LENGTH =
    2_048

/**
 * 历史记录默认每页数量。
 */
private const val DEFAULT_HISTORY_LIMIT =
    20

/**
 * 后端允许的历史记录最小查询数量。
 */
private const val MIN_HISTORY_LIMIT =
    1

/**
 * 后端允许的历史记录最大查询数量。
 */
private const val MAX_HISTORY_LIMIT =
    100

/**
 * 后端允许上传的图片 MIME 类型。
 */
private val SUPPORTED_IMAGE_CONTENT_TYPES =
    setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/bmp",
    )

/**
 * 文本风险检测操作结果。
 */
sealed interface TextRiskAnalysisResult {

    data class Success(
        val response: TextRiskResponse,
    ) : TextRiskAnalysisResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : TextRiskAnalysisResult
}

/**
 * 风险事件详情读取结果。
 */
sealed interface RiskEventDetailResult {

    data class Success(
        val response: RiskEventDetailResponse,
    ) : RiskEventDetailResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : RiskEventDetailResult
}

/**
 * 风险历史记录查询结果。
 */
sealed interface RiskHistoryResult {

    data class Success(
        val response: RiskEventListResponse,
    ) : RiskHistoryResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : RiskHistoryResult
}

/**
 * 风险检测数据仓库。
 */

/**
 * 图片风险检测操作结果。
 */
sealed interface ImageRiskAnalysisResult {

    data class Success(
        val response: ImageRiskResponse,
    ) : ImageRiskAnalysisResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : ImageRiskAnalysisResult
}


/**
 * URL 风险检测操作结果。
 */
sealed interface UrlRiskAnalysisResult {

    data class Success(
        val response: UrlRiskResponse,
    ) : UrlRiskAnalysisResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : UrlRiskAnalysisResult
}


class RiskRepository(
    private val riskApi: RiskApi,
) {

    /**
     * 提交文本风险检测。
     */
    suspend fun analyzeText(
        text: String,
        senderIsKnown: Boolean? = null,
        claimedIdentity: String? = null,
        amount: Double? = null,
    ): TextRiskAnalysisResult {
        return try {
            val request =
                TextRiskRequest(
                    text = text.trim(),
                    context =
                        TextRiskContext(
                            senderIsKnown =
                                senderIsKnown,
                            claimedIdentity =
                                claimedIdentity,
                            amount = amount,
                        ),
                )

            val response =
                riskApi.analyzeText(
                    request = request,
                )

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                TextRiskAnalysisResult.Success(
                    response = responseBody,
                )
            } else {
                createTextErrorResult(
                    response = response,
                )
            }
        } catch (exception: IOException) {
            TextRiskAnalysisResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动，" +
                            "并确认网络地址配置正确。",
            )
        } catch (exception: Exception) {
            TextRiskAnalysisResult.Error(
                message =
                    exception.message
                        ?: "文本风险检测过程中发生未知错误。",
            )
        }
    }

    /**
     * 上传聊天截图并执行 OCR 与风险检测。
     */
    suspend fun analyzeImage(
        imageBytes: ByteArray,
        fileName: String,
        contentType: String,
    ): ImageRiskAnalysisResult {
        if (imageBytes.isEmpty()) {
            return ImageRiskAnalysisResult.Error(
                message = "选择的图片内容为空。",
            )
        }

        if (imageBytes.size > MAX_IMAGE_BYTES) {
            return ImageRiskAnalysisResult.Error(
                message = "图片不能超过 10 MB。",
            )
        }

        val normalizedContentType =
            contentType
                .substringBefore(";")
                .trim()
                .lowercase()

        if (
            normalizedContentType !in
            SUPPORTED_IMAGE_CONTENT_TYPES
        ) {
            return ImageRiskAnalysisResult.Error(
                message =
                    "仅支持 JPEG、PNG、WEBP 和 BMP 图片。",
            )
        }

        val mediaType =
            normalizedContentType
                .toMediaTypeOrNull()
                ?: return ImageRiskAnalysisResult.Error(
                    message = "无法识别图片文件类型。",
                )

        val safeFileName =
            fileName
                .trim()
                .ifBlank {
                    "chat_image"
                }

        return try {
            val requestBody =
                imageBytes.toRequestBody(
                    contentType = mediaType,
                )

            val imagePart =
                MultipartBody.Part.createFormData(
                    name = "image",
                    filename = safeFileName,
                    body = requestBody,
                )

            val response =
                riskApi.analyzeImage(
                    image = imagePart,
                )

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                ImageRiskAnalysisResult.Success(
                    response = responseBody,
                )
            } else {
                createImageErrorResult(
                    response = response,
                )
            }
        } catch (exception: IOException) {
            ImageRiskAnalysisResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            ImageRiskAnalysisResult.Error(
                message =
                    exception.message
                        ?: "图片风险检测过程中发生未知错误。",
            )
        }
    }

    /**
     * 根据事件编号获取风险事件完整详情。
     */
    suspend fun getEventDetail(
        eventId: String,
    ): RiskEventDetailResult {
        val cleanedEventId =
            eventId.trim()

        if (cleanedEventId.isBlank()) {
            return RiskEventDetailResult.Error(
                message = "风险事件编号不能为空。",
            )
        }

        return try {
            val response =
                riskApi.getRiskEventDetail(
                    eventId = cleanedEventId,
                )

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                RiskEventDetailResult.Success(
                    response = responseBody,
                )
            } else {
                createEventDetailErrorResult(
                    response = response,
                )
            }
        } catch (exception: IOException) {
            RiskEventDetailResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            RiskEventDetailResult.Error(
                message =
                    exception.message
                        ?: "读取风险事件详情时发生未知错误。",
            )
        }
    }

    /**
     * 创建文本检测错误结果。
     */
    private fun createTextErrorResult(
        response: Response<*>,
    ): TextRiskAnalysisResult.Error {
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
                        "文本检测请求格式不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户没有执行该操作的权限。"

                    404 ->
                        "文本风险检测接口不存在。"

                    413 ->
                        "提交的内容过大。"

                    422 ->
                        "文本内容未通过服务器校验。"

                    500 ->
                        "服务器内部错误，请稍后重试。"

                    else ->
                        "文本风险检测失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return TextRiskAnalysisResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin =
                statusCode == 401,
        )
    }

    /**
     * 创建事件详情错误结果。
     */
    private fun createEventDetailErrorResult(
        response: Response<*>,
    ): RiskEventDetailResult.Error {
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
                        "风险事件编号格式不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "你没有查看该风险事件的权限。"

                    404 ->
                        "风险事件不存在，或者已经被删除。"

                    422 ->
                        "风险事件编号未通过服务器校验。"

                    500 ->
                        "服务器读取事件详情失败，请稍后重试。"

                    else ->
                        "读取风险事件详情失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return RiskEventDetailResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin =
                statusCode == 401,
        )
    }

    /**
     * 创建图片检测错误结果。
     */
    private fun createImageErrorResult(
        response: Response<*>,
    ): ImageRiskAnalysisResult.Error {
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
                        "图片无效或图片内容损坏。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户没有执行该操作的权限。"

                    413 ->
                        "图片文件过大，请选择不超过 10 MB 的图片。"

                    415 ->
                        "图片格式不受支持，" +
                                "请选择 JPEG、PNG、WEBP 或 BMP 图片。"

                    422 ->
                        "图片中没有识别到有效文字，" +
                                "请换一张更清晰的聊天截图。"

                    500 ->
                        "OCR 服务暂时无法完成识别，请稍后重试。"

                    else ->
                        "图片风险检测失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return ImageRiskAnalysisResult.Error(
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

    /**
     * 提交 URL 风险检测。
     */
    suspend fun analyzeUrl(
        url: String,
        resolveRedirects: Boolean = false,
    ): UrlRiskAnalysisResult {
        val cleanedUrl =
            url.trim()

        if (cleanedUrl.isBlank()) {
            return UrlRiskAnalysisResult.Error(
                message = "请输入需要检测的网址。",
            )
        }

        if (cleanedUrl.length > MAX_URL_LENGTH) {
            return UrlRiskAnalysisResult.Error(
                message = "网址不能超过 2048 个字符。",
            )
        }

        return try {
            val request =
                UrlRiskRequest(
                    url = cleanedUrl,
                    resolveRedirects =
                        resolveRedirects,
                )

            val response =
                riskApi.analyzeUrl(
                    request = request,
                )

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                UrlRiskAnalysisResult.Success(
                    response = responseBody,
                )
            } else {
                createUrlErrorResult(
                    response = response,
                )
            }
        } catch (exception: IOException) {
            UrlRiskAnalysisResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            UrlRiskAnalysisResult.Error(
                message =
                    exception.message
                        ?: "链接风险检测过程中发生未知错误。",
            )
        }
    }

    /**
     * 分页获取当前用户的风险检测历史。
     */
    suspend fun getRiskHistory(
        limit: Int = DEFAULT_HISTORY_LIMIT,
        offset: Int = 0,
    ): RiskHistoryResult {
        if (
            limit < MIN_HISTORY_LIMIT ||
            limit > MAX_HISTORY_LIMIT
        ) {
            return RiskHistoryResult.Error(
                message =
                    "每次读取数量必须在 " +
                            "$MIN_HISTORY_LIMIT～$MAX_HISTORY_LIMIT 之间。",
            )
        }

        if (offset < 0) {
            return RiskHistoryResult.Error(
                message = "历史记录偏移量不能小于 0。",
            )
        }

        return try {
            val response =
                riskApi.getRiskEvents(
                    limit = limit,
                    offset = offset,
                )

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                RiskHistoryResult.Success(
                    response = responseBody,
                )
            } else {
                createHistoryErrorResult(
                    response = response,
                )
            }
        } catch (exception: IOException) {
            RiskHistoryResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            RiskHistoryResult.Error(
                message =
                    exception.message
                        ?: "读取历史检测记录时发生未知错误。",
            )
        }
    }

    /**
     * 创建历史记录查询错误。
     */
    private fun createHistoryErrorResult(
        response: Response<*>,
    ): RiskHistoryResult.Error {
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
                        "历史记录查询参数不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户没有查看历史记录的权限。"

                    404 ->
                        "历史记录接口不存在。"

                    422 ->
                        "分页参数未通过服务器校验。"

                    500 ->
                        "服务器读取历史记录失败，请稍后重试。"

                    else ->
                        "读取历史记录失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return RiskHistoryResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin =
                statusCode == 401,
        )
    }

    /**
     * 创建 URL 检测错误结果。
     */
    private fun createUrlErrorResult(
        response: Response<*>,
    ): UrlRiskAnalysisResult.Error {
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
                        "链接请求格式不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户没有执行该操作的权限。"

                    404 ->
                        "链接风险检测接口不存在。"

                    408 ->
                        "链接检查超时，请稍后重试。"

                    422 ->
                        "链接格式无效，无法识别有效主机名。"

                    500 ->
                        "服务器内部错误，请稍后重试。"

                    502,
                    503,
                    504 ->
                        "目标网站暂时无法访问，" +
                                "请关闭重定向检查后重试。"

                    else ->
                        "链接风险检测失败，" +
                                "服务器返回状态码：$statusCode。"
                }

        return UrlRiskAnalysisResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin =
                statusCode == 401,
        )
    }

}