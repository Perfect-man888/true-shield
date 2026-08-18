package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.alert.AlertDeliveryAttemptListResponse
import com.trueshield.app.data.model.alert.DispatchFamilyAlertRequest
import com.trueshield.app.data.model.alert.FamilyAlertDispatchResponse
import com.trueshield.app.data.model.alert.FamilyAlertListResponse
import com.trueshield.app.data.model.alert.FamilyAlertResponse
import com.trueshield.app.data.model.alert.ResolveFamilyAlertRequest
import com.trueshield.app.data.network.FamilyApi
import retrofit2.Response
import java.io.IOException
import com.trueshield.app.data.model.alert.FamilyAlertPolicyResponse
import com.trueshield.app.data.model.alert.UpdateFamilyAlertPolicyRequest

/**
 * 家庭告警接口统一操作结果。
 */
sealed interface FamilyAlertOperationResult<out T> {

    data class Success<T>(
        val data: T,
    ) : FamilyAlertOperationResult<T>

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : FamilyAlertOperationResult<Nothing>
}

/**
 * 家庭告警数据仓库。
 *
 * ViewModel 和页面不直接访问 Retrofit，
 * 所有家庭告警请求统一通过本仓库执行。
 */
class FamilyAlertRepository(
    private val familyApi: FamilyApi,
) {

    /**
     * 根据风险事件手动创建家庭告警。
     */
    suspend fun createAlertFromEvent(
        familyId: String,
        eventId: String,
    ): FamilyAlertOperationResult<FamilyAlertResponse> {
        val cleanedFamilyId = familyId.trim()
        val cleanedEventId = eventId.trim()

        validateFamilyAndAlertId(
            familyId = cleanedFamilyId,
            alertId = cleanedEventId,
            secondIdName = "风险事件编号",
        )?.let { message ->
            return FamilyAlertOperationResult.Error(
                message = message,
            )
        }

        return executeRequest(
            request = {
                familyApi.createFamilyAlertFromEvent(
                    familyId = cleanedFamilyId,
                    eventId = cleanedEventId,
                )
            },
            defaultErrorMessage = "创建家庭告警失败。",
            notFoundMessage =
                "家庭或风险事件不存在，或者你无权创建家庭告警。",
            conflictMessage =
                "该风险事件可能已经生成家庭告警。",
        )
    }

    /**
     * 查询指定家庭的告警列表。
     */
    suspend fun getAlerts(
        familyId: String,
    ): FamilyAlertOperationResult<FamilyAlertListResponse> {
        val cleanedFamilyId = familyId.trim()

        if (cleanedFamilyId.isBlank()) {
            return FamilyAlertOperationResult.Error(
                message = "家庭编号不能为空。",
            )
        }

        return executeRequest(
            request = {
                familyApi.getFamilyAlerts(
                    familyId = cleanedFamilyId,
                )
            },
            defaultErrorMessage = "读取家庭告警列表失败。",
            notFoundMessage =
                "家庭不存在，或者你无权查看该家庭的告警。",
        )
    }

    /**
     * 查询单条家庭告警详情。
     */
    suspend fun getAlertDetail(
        familyId: String,
        alertId: String,
    ): FamilyAlertOperationResult<FamilyAlertResponse> {
        val cleanedFamilyId = familyId.trim()
        val cleanedAlertId = alertId.trim()

        validateFamilyAndAlertId(
            familyId = cleanedFamilyId,
            alertId = cleanedAlertId,
        )?.let { message ->
            return FamilyAlertOperationResult.Error(
                message = message,
            )
        }

        return executeRequest(
            request = {
                familyApi.getFamilyAlertDetail(
                    familyId = cleanedFamilyId,
                    alertId = cleanedAlertId,
                )
            },
            defaultErrorMessage = "读取家庭告警详情失败。",
            notFoundMessage =
                "家庭告警不存在，或者你无权查看。",
        )
    }

    /**
     * 确认已经查看并开始处理家庭告警。
     */
    suspend fun acknowledgeAlert(
        familyId: String,
        alertId: String,
    ): FamilyAlertOperationResult<FamilyAlertResponse> {
        val cleanedFamilyId = familyId.trim()
        val cleanedAlertId = alertId.trim()

        validateFamilyAndAlertId(
            familyId = cleanedFamilyId,
            alertId = cleanedAlertId,
        )?.let { message ->
            return FamilyAlertOperationResult.Error(
                message = message,
            )
        }

        return executeRequest(
            request = {
                familyApi.acknowledgeFamilyAlert(
                    familyId = cleanedFamilyId,
                    alertId = cleanedAlertId,
                )
            },
            defaultErrorMessage = "确认家庭告警失败。",
            notFoundMessage =
                "家庭告警不存在，或者你无权确认。",
            conflictMessage =
                "当前家庭告警状态不能重复确认。",
        )
    }

    /**
     * 完成家庭告警处理。
     */
    suspend fun resolveAlert(
        familyId: String,
        alertId: String,
        resolutionNote: String,
    ): FamilyAlertOperationResult<FamilyAlertResponse> {
        val cleanedFamilyId = familyId.trim()
        val cleanedAlertId = alertId.trim()
        val cleanedResolutionNote = resolutionNote.trim()

        validateFamilyAndAlertId(
            familyId = cleanedFamilyId,
            alertId = cleanedAlertId,
        )?.let { message ->
            return FamilyAlertOperationResult.Error(
                message = message,
            )
        }

        if (cleanedResolutionNote.isBlank()) {
            return FamilyAlertOperationResult.Error(
                message = "请输入告警处理说明。",
            )
        }

        return executeRequest(
            request = {
                familyApi.resolveFamilyAlert(
                    familyId = cleanedFamilyId,
                    alertId = cleanedAlertId,
                    request = ResolveFamilyAlertRequest(
                        resolutionNote = cleanedResolutionNote,
                    ),
                )
            },
            defaultErrorMessage = "完成家庭告警处理失败。",
            notFoundMessage =
                "家庭告警不存在，或者你无权处理。",
            conflictMessage =
                "该家庭告警可能已经处理完成。",
        )
    }

    /**
     * 手动发送家庭告警。
     *
     * 正常发送时不传模拟失败接收人编号，
     * 开发测试时可以传入 recipient_id 列表。
     */
    suspend fun dispatchAlert(
        familyId: String,
        alertId: String,
        simulatedFailureRecipientIds: List<String> = emptyList(),
    ): FamilyAlertOperationResult<FamilyAlertDispatchResponse> {
        val cleanedFamilyId = familyId.trim()
        val cleanedAlertId = alertId.trim()

        validateFamilyAndAlertId(
            familyId = cleanedFamilyId,
            alertId = cleanedAlertId,
        )?.let { message ->
            return FamilyAlertOperationResult.Error(
                message = message,
            )
        }

        val cleanedRecipientIds =
            simulatedFailureRecipientIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

        return executeRequest(
            request = {
                familyApi.dispatchFamilyAlert(
                    familyId = cleanedFamilyId,
                    alertId = cleanedAlertId,
                    request = DispatchFamilyAlertRequest(
                        simulatedFailureRecipientIds =
                            cleanedRecipientIds,
                    ),
                )
            },
            defaultErrorMessage = "发送家庭告警失败。",
            notFoundMessage =
                "家庭告警不存在，或者你无权发送。",
            conflictMessage =
                "当前家庭告警状态不能继续发送。",
        )
    }

    /**
     * 查询家庭告警的发送尝试记录。
     */
    suspend fun getDeliveryAttempts(
        familyId: String,
        alertId: String,
    ): FamilyAlertOperationResult<AlertDeliveryAttemptListResponse> {
        val cleanedFamilyId = familyId.trim()
        val cleanedAlertId = alertId.trim()

        validateFamilyAndAlertId(
            familyId = cleanedFamilyId,
            alertId = cleanedAlertId,
        )?.let { message ->
            return FamilyAlertOperationResult.Error(
                message = message,
            )
        }

        return executeRequest(
            request = {
                familyApi.getFamilyAlertDeliveryAttempts(
                    familyId = cleanedFamilyId,
                    alertId = cleanedAlertId,
                )
            },
            defaultErrorMessage = "读取告警发送记录失败。",
            notFoundMessage =
                "家庭告警不存在，或者你无权查看发送记录。",
        )
    }

    /**
     * 重试当前告警中发送失败的通知。
     */
    suspend fun retryFailedDeliveries(
        familyId: String,
        alertId: String,
    ): FamilyAlertOperationResult<FamilyAlertDispatchResponse> {
        val cleanedFamilyId = familyId.trim()
        val cleanedAlertId = alertId.trim()

        validateFamilyAndAlertId(
            familyId = cleanedFamilyId,
            alertId = cleanedAlertId,
        )?.let { message ->
            return FamilyAlertOperationResult.Error(
                message = message,
            )
        }

        return executeRequest(
            request = {
                familyApi.retryFailedFamilyAlertDeliveries(
                    familyId = cleanedFamilyId,
                    alertId = cleanedAlertId,
                )
            },
            defaultErrorMessage = "重试失败通知失败。",
            notFoundMessage =
                "家庭告警不存在，或者你无权重试通知。",
            conflictMessage =
                "当前没有可重试的失败通知，或者告警已经处理完成。",
        )
    }

    /**
     * 查询家庭告警自动触发策略。
     */
    suspend fun getAlertPolicy(
        familyId: String,
    ): FamilyAlertOperationResult<FamilyAlertPolicyResponse> {
        val cleanedFamilyId = familyId.trim()

        if (cleanedFamilyId.isBlank()) {
            return FamilyAlertOperationResult.Error(
                message = "家庭编号不能为空。",
            )
        }

        return executeRequest(
            request = {
                familyApi.getFamilyAlertPolicy(
                    familyId = cleanedFamilyId,
                )
            },
            defaultErrorMessage =
                "读取家庭告警自动触发策略失败。",
            notFoundMessage =
                "家庭不存在，或者你无权查看该家庭的告警策略。",
        )
    }

    /**
     * 更新家庭告警自动触发策略。
     */
    suspend fun updateAlertPolicy(
        familyId: String,
        autoCreateEnabled: Boolean,
        autoDispatchEnabled: Boolean,
        minimumRiskLevel: String,
        enabledSourceTypes: List<String>,
        maxRecipients: Int,
    ): FamilyAlertOperationResult<FamilyAlertPolicyResponse> {
        val cleanedFamilyId = familyId.trim()

        val cleanedRiskLevel =
            minimumRiskLevel
                .trim()
                .lowercase()

        val cleanedSourceTypes =
            enabledSourceTypes
                .map { sourceType ->
                    sourceType
                        .trim()
                        .lowercase()
                }
                .filter { sourceType ->
                    sourceType.isNotBlank()
                }
                .distinct()

        if (cleanedFamilyId.isBlank()) {
            return FamilyAlertOperationResult.Error(
                message = "家庭编号不能为空。",
            )
        }

        val supportedRiskLevels =
            setOf(
                "low",
                "medium",
                "high",
            )

        if (cleanedRiskLevel !in supportedRiskLevels) {
            return FamilyAlertOperationResult.Error(
                message =
                    "最低风险等级只能是低风险、" +
                            "中风险或高风险。",
            )
        }

        val supportedSourceTypes =
            setOf(
                "text",
                "image",
                "url",
            )

        if (
            cleanedSourceTypes.any { sourceType ->
                sourceType !in supportedSourceTypes
            }
        ) {
            return FamilyAlertOperationResult.Error(
                message =
                    "风险来源只能包含文本、图片或链接。",
            )
        }

        if (
            autoCreateEnabled &&
            cleanedSourceTypes.isEmpty()
        ) {
            return FamilyAlertOperationResult.Error(
                message =
                    "启用自动创建告警后，" +
                            "至少需要选择一种风险来源。",
            )
        }

        if (
            autoDispatchEnabled &&
            !autoCreateEnabled
        ) {
            return FamilyAlertOperationResult.Error(
                message =
                    "启用自动发送前，" +
                            "必须先启用自动创建告警。",
            )
        }

        if (maxRecipients <= 0) {
            return FamilyAlertOperationResult.Error(
                message =
                    "最大告警接收人数必须大于 0。",
            )
        }

        return executeRequest(
            request = {
                familyApi.updateFamilyAlertPolicy(
                    familyId = cleanedFamilyId,
                    request =
                        UpdateFamilyAlertPolicyRequest(
                            autoCreateEnabled =
                                autoCreateEnabled,
                            autoDispatchEnabled =
                                autoDispatchEnabled,
                            minimumRiskLevel =
                                cleanedRiskLevel,
                            enabledSourceTypes =
                                cleanedSourceTypes,
                            maxRecipients =
                                maxRecipients,
                        ),
                )
            },
            defaultErrorMessage =
                "更新家庭告警自动触发策略失败。",
            notFoundMessage =
                "家庭不存在，或者你无权修改该家庭的告警策略。",
        )
    }

    /**
     * 校验家庭编号及另一个业务编号。
     */
    private fun validateFamilyAndAlertId(
        familyId: String,
        alertId: String,
        secondIdName: String = "家庭告警编号",
    ): String? {
        if (familyId.isBlank()) {
            return "家庭编号不能为空。"
        }

        if (alertId.isBlank()) {
            return "${secondIdName}不能为空。"
        }

        return null
    }

    /**
     * 执行 Retrofit 请求并统一处理错误。
     */
    private suspend fun <T> executeRequest(
        request: suspend () -> Response<T>,
        defaultErrorMessage: String,
        notFoundMessage: String,
        conflictMessage: String = "当前家庭告警状态不允许执行该操作。",
    ): FamilyAlertOperationResult<T> {
        return try {
            val response = request()
            val responseBody = response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                FamilyAlertOperationResult.Success(
                    data = responseBody,
                )
            } else {
                createErrorResult(
                    response = response,
                    defaultErrorMessage = defaultErrorMessage,
                    notFoundMessage = notFoundMessage,
                    conflictMessage = conflictMessage,
                )
            }
        } catch (exception: IOException) {
            FamilyAlertOperationResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            FamilyAlertOperationResult.Error(
                message =
                    exception.message
                        ?: "${defaultErrorMessage}发生未知错误。",
            )
        }
    }

    /**
     * 根据 HTTP 状态码生成可读错误。
     */
    private fun createErrorResult(
        response: Response<*>,
        defaultErrorMessage: String,
        notFoundMessage: String,
        conflictMessage: String,
    ): FamilyAlertOperationResult.Error {
        val statusCode = response.code()
        val backendDetail =
            readBackendDetail(
                response = response,
            )

        val message =
            backendDetail
                ?: when (statusCode) {
                    400 ->
                        "家庭告警请求格式不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户没有执行该家庭告警操作的权限。"

                    404 ->
                        notFoundMessage

                    409 ->
                        conflictMessage

                    422 ->
                        "家庭告警信息未通过服务器校验。"

                    500 ->
                        "服务器处理家庭告警失败，请稍后重试。"

                    else ->
                        "$defaultErrorMessage" +
                                "服务器返回状态码：$statusCode。"
                }

        return FamilyAlertOperationResult.Error(
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