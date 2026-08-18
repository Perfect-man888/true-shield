package com.trueshield.app.data.repository

import android.content.Context
import com.google.gson.JsonParser
import com.trueshield.app.data.model.risk.report.RiskReportSummaryResponse
import com.trueshield.app.data.network.RiskApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface RiskReportResult<out T> {

    data class Success<T>(
        val data: T,
    ) : RiskReportResult<T>

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : RiskReportResult<Nothing>
}

data class GeneratedRiskReportPdf(
    val file: File,
    val displayName: String,
)

class RiskReportRepository(
    context: Context,
    private val riskApi: RiskApi,
) {

    private val applicationContext =
        context.applicationContext

    /**
     * 获取风险报告预览。
     *
     * 即使 Retrofit 的 suspend 请求本身不会阻塞主线程，
     * 错误响应读取等操作仍统一放入 IO 线程，保证 Repository
     * 从任何调用位置使用时都是主线程安全的。
     */
    suspend fun getSummary(
        periodDays: Int,
        familyId: String?,
    ): RiskReportResult<RiskReportSummaryResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    riskApi.getRiskReportSummary(
                        periodDays =
                            periodDays.coerceIn(1, 365),
                        familyId =
                            familyId
                                ?.trim()
                                ?.takeIf { it.isNotBlank() },
                    )

                val body = response.body()

                if (response.isSuccessful && body != null) {
                    RiskReportResult.Success(body)
                } else {
                    createError(
                        response = response,
                        defaultMessage =
                            "生成风险报告预览失败。",
                    )
                }
            } catch (exception: IOException) {
                RiskReportResult.Error(
                    message =
                        "无法连接真信盾服务器，请检查后端是否已经启动。",
                )
            } catch (exception: Exception) {
                RiskReportResult.Error(
                    message =
                        exception.message
                            ?: "生成风险报告预览时发生未知错误。",
                )
            }
        }

    /**
     * 下载并保存 PDF 到应用缓存目录。
     *
     * 网络响应读取、PDF 校验和文件写入全部在 IO 线程中完成，
     * 避免 NetworkOnMainThreadException。
     */
    suspend fun downloadPdf(
        periodDays: Int,
        familyId: String?,
    ): RiskReportResult<GeneratedRiskReportPdf> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedFamilyId =
                    familyId
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                val response =
                    riskApi.downloadRiskReportPdf(
                        periodDays =
                            periodDays.coerceIn(1, 365),
                        familyId = normalizedFamilyId,
                    )

                val body = response.body()

                if (!response.isSuccessful || body == null) {
                    return@withContext createError(
                        response = response,
                        defaultMessage = "导出 PDF 失败。",
                    )
                }

                /*
                 * ResponseBody.bytes() 是阻塞式读取。
                 * 当前代码已经位于 Dispatchers.IO 中。
                 */
                val pdfBytes =
                    body.use { responseBody ->
                        responseBody.bytes()
                    }

                val hasPdfHeader =
                    pdfBytes.size >= 5 &&
                            pdfBytes[0] == '%'.code.toByte() &&
                            pdfBytes[1] == 'P'.code.toByte() &&
                            pdfBytes[2] == 'D'.code.toByte() &&
                            pdfBytes[3] == 'F'.code.toByte() &&
                            pdfBytes[4] == '-'.code.toByte()

                if (!hasPdfHeader) {
                    return@withContext RiskReportResult.Error(
                        message =
                            "服务器返回的内容不是有效 PDF，请查看后端日志。",
                    )
                }

                if (pdfBytes.size > 20 * 1024 * 1024) {
                    return@withContext RiskReportResult.Error(
                        message =
                            "风险报告 PDF 超过 20 MB，无法保存。",
                    )
                }

                val reportDirectory =
                    File(
                        applicationContext.cacheDir,
                        "risk_reports",
                    )

                if (
                    !reportDirectory.exists() &&
                    !reportDirectory.mkdirs()
                ) {
                    return@withContext RiskReportResult.Error(
                        message =
                            "无法创建 PDF 临时目录。",
                    )
                }

                /*
                 * 删除上一次生成的临时报告。
                 * 只删除应用自身缓存目录中的文件。
                 */
                reportDirectory
                    .listFiles()
                    ?.filter { it.isFile }
                    ?.forEach { oldFile ->
                        runCatching {
                            oldFile.delete()
                        }
                    }

                val timestamp =
                    SimpleDateFormat(
                        "yyyyMMdd-HHmmss",
                        Locale.US,
                    ).format(Date())

                val prefix =
                    if (normalizedFamilyId == null) {
                        "true-shield-personal-risk-report"
                    } else {
                        "true-shield-family-risk-report"
                    }

                val displayName =
                    "$prefix-$timestamp.pdf"

                val destination =
                    File(
                        reportDirectory,
                        displayName,
                    )

                /*
                 * 文件写入也是阻塞式操作。
                 * 当前代码已经位于 Dispatchers.IO 中。
                 */
                destination.outputStream().use { output ->
                    output.write(pdfBytes)
                    output.flush()
                }

                if (
                    !destination.exists() ||
                    destination.length() !=
                    pdfBytes.size.toLong()
                ) {
                    runCatching {
                        destination.delete()
                    }

                    return@withContext RiskReportResult.Error(
                        message =
                            "PDF 临时文件写入不完整，请重试。",
                    )
                }

                RiskReportResult.Success(
                    GeneratedRiskReportPdf(
                        file = destination,
                        displayName = displayName,
                    ),
                )
            } catch (exception: IOException) {
                RiskReportResult.Error(
                    message =
                        "下载或保存 PDF 失败：" +
                                (
                                        exception.message
                                            ?: "网络或文件读写异常"
                                        ),
                )
            } catch (exception: Exception) {
                RiskReportResult.Error(
                    message =
                        "导出 PDF 失败" +
                                "（${exception.javaClass.simpleName}）：" +
                                (
                                        exception.message
                                            ?: "无详细信息"
                                        ),
                )
            }
        }

    /**
     * 将后端错误响应转换为统一错误对象。
     *
     * 该方法会读取 errorBody，因此应当只在 IO 线程调用。
     */
    private fun createError(
        response: Response<*>,
        defaultMessage: String,
    ): RiskReportResult.Error {
        val statusCode = response.code()
        val backendDetail =
            readBackendDetail(response)

        val message =
            backendDetail
                ?: when (statusCode) {
                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户无权生成该报告。"

                    404 ->
                        "家庭不存在或无权生成该家庭报告。"

                    422 ->
                        "报告周期或家庭编号不正确。"

                    500 ->
                        "服务器生成报告失败，请稍后重试。"

                    else ->
                        "$defaultMessage 服务器返回状态码：$statusCode。"
                }

        return RiskReportResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin = statusCode == 401,
        )
    }

    private fun readBackendDetail(
        response: Response<*>,
    ): String? {
        val errorText =
            runCatching {
                response
                    .errorBody()
                    ?.string()
                    .orEmpty()
            }.getOrDefault("")

        if (errorText.isBlank()) {
            return null
        }

        return runCatching {
            val detail =
                JsonParser
                    .parseString(errorText)
                    .asJsonObject
                    .get("detail")

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