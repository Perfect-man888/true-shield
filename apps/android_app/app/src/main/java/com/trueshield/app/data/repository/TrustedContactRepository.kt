package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.contact.TrustedContactChannelValue
import com.trueshield.app.data.model.contact.TrustedContactCreateRequest
import com.trueshield.app.data.model.contact.TrustedContactDto
import com.trueshield.app.data.model.contact.TrustedContactListResponse
import com.trueshield.app.data.model.contact.TrustedContactUpdateRequest
import com.trueshield.app.data.network.TrustedContactApi
import retrofit2.Response
import java.io.IOException

/**
 * 可信联系人接口统一操作结果。
 */
sealed interface TrustedContactOperationResult<out T> {

    data class Success<T>(
        val data: T,
    ) : TrustedContactOperationResult<T>

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : TrustedContactOperationResult<Nothing>
}

/**
 * 可信联系人数据仓库。
 */
class TrustedContactRepository(
    private val trustedContactApi:
    TrustedContactApi,
) {

    /**
     * 查询当前成员在指定家庭中的可信联系人。
     */
    suspend fun getTrustedContacts(
        familyId: String,
    ): TrustedContactOperationResult<
            TrustedContactListResponse
            > {
        val cleanedFamilyId =
            familyId.trim()

        if (cleanedFamilyId.isBlank()) {
            return TrustedContactOperationResult.Error(
                message = "家庭编号不能为空。",
            )
        }

        return executeRequest(
            request = {
                trustedContactApi
                    .getTrustedContacts(
                        familyId =
                            cleanedFamilyId,
                    )
            },
            defaultErrorMessage =
                "读取可信联系人失败。",
            notFoundMessage =
                "家庭不存在，或者你无权查看该家庭的可信联系人。",
        )
    }

    /**
     * 创建可信联系人。
     */
    suspend fun createTrustedContact(
        familyId: String,
        subjectUserId: String,
        displayName: String,
        relationshipLabel: String,
        phone: String,
        email: String,
        preferredChannel: String,
        priority: Int,
        canReceiveAlerts: Boolean,
    ): TrustedContactOperationResult<
            TrustedContactDto
            > {
        val cleanedFamilyId =
            familyId.trim()

        val cleanedSubjectUserId =
            subjectUserId.trim()

        val cleanedDisplayName =
            displayName.trim()

        val cleanedRelationshipLabel =
            relationshipLabel.trim()

        val cleanedPhone =
            phone.trim()

        val cleanedEmail =
            email.trim()

        val cleanedPreferredChannel =
            preferredChannel
                .trim()
                .lowercase()

        validateContactInput(
            familyId =
                cleanedFamilyId,
            subjectUserId =
                cleanedSubjectUserId,
            displayName =
                cleanedDisplayName,
            relationshipLabel =
                cleanedRelationshipLabel,
            phone =
                cleanedPhone,
            email =
                cleanedEmail,
            preferredChannel =
                cleanedPreferredChannel,
            priority =
                priority,
        )?.let {
                message ->

            return TrustedContactOperationResult.Error(
                message = message,
            )
        }

        val request =
            TrustedContactCreateRequest(
                subjectUserId =
                    cleanedSubjectUserId,
                displayName =
                    cleanedDisplayName,
                relationshipLabel =
                    cleanedRelationshipLabel,
                phone =
                    cleanedPhone.takeIf {
                        it.isNotBlank()
                    },
                email =
                    cleanedEmail.takeIf {
                        it.isNotBlank()
                    },
                preferredChannel =
                    cleanedPreferredChannel,
                priority =
                    priority,
                canReceiveAlerts =
                    canReceiveAlerts,
            )

        return executeRequest(
            request = {
                trustedContactApi
                    .createTrustedContact(
                        familyId =
                            cleanedFamilyId,
                        request =
                            request,
                    )
            },
            defaultErrorMessage =
                "创建可信联系人失败。",
            notFoundMessage =
                "家庭或目标家庭成员不存在。",
        )
    }

    /**
     * 修改可信联系人。
     *
     * 第一版编辑页面会提交完整的可编辑字段，
     * 但网络接口本身仍然使用 PATCH。
     */
    suspend fun updateTrustedContact(
        familyId: String,
        contactId: String,
        displayName: String,
        relationshipLabel: String,
        phone: String,
        email: String,
        preferredChannel: String,
        priority: Int,
        canReceiveAlerts: Boolean,
    ): TrustedContactOperationResult<
            TrustedContactDto
            > {
        val cleanedFamilyId =
            familyId.trim()

        val cleanedContactId =
            contactId.trim()

        val cleanedDisplayName =
            displayName.trim()

        val cleanedRelationshipLabel =
            relationshipLabel.trim()

        val cleanedPhone =
            phone.trim()

        val cleanedEmail =
            email.trim()

        val cleanedPreferredChannel =
            preferredChannel
                .trim()
                .lowercase()

        if (cleanedContactId.isBlank()) {
            return TrustedContactOperationResult.Error(
                message =
                    "可信联系人编号不能为空。",
            )
        }

        validateContactInput(
            familyId =
                cleanedFamilyId,
            subjectUserId =
                "existing-contact",
            displayName =
                cleanedDisplayName,
            relationshipLabel =
                cleanedRelationshipLabel,
            phone =
                cleanedPhone,
            email =
                cleanedEmail,
            preferredChannel =
                cleanedPreferredChannel,
            priority =
                priority,
        )?.let {
                message ->

            return TrustedContactOperationResult.Error(
                message = message,
            )
        }

        val request =
            TrustedContactUpdateRequest(
                displayName =
                    cleanedDisplayName,
                relationshipLabel =
                    cleanedRelationshipLabel,
                phone =
                    cleanedPhone.takeIf {
                        it.isNotBlank()
                    },
                email =
                    cleanedEmail.takeIf {
                        it.isNotBlank()
                    },
                preferredChannel =
                    cleanedPreferredChannel,
                priority =
                    priority,
                canReceiveAlerts =
                    canReceiveAlerts,
            )

        return executeRequest(
            request = {
                trustedContactApi
                    .updateTrustedContact(
                        familyId =
                            cleanedFamilyId,
                        contactId =
                            cleanedContactId,
                        request =
                            request,
                    )
            },
            defaultErrorMessage =
                "修改可信联系人失败。",
            notFoundMessage =
                "可信联系人不存在，或者你无权修改。",
        )
    }

    /**
     * 停用可信联系人。
     */
    suspend fun disableTrustedContact(
        familyId: String,
        contactId: String,
    ): TrustedContactOperationResult<
            TrustedContactDto
            > {
        val cleanedFamilyId =
            familyId.trim()

        val cleanedContactId =
            contactId.trim()

        if (cleanedFamilyId.isBlank()) {
            return TrustedContactOperationResult.Error(
                message = "家庭编号不能为空。",
            )
        }

        if (cleanedContactId.isBlank()) {
            return TrustedContactOperationResult.Error(
                message =
                    "可信联系人编号不能为空。",
            )
        }

        return executeRequest(
            request = {
                trustedContactApi
                    .disableTrustedContact(
                        familyId =
                            cleanedFamilyId,
                        contactId =
                            cleanedContactId,
                    )
            },
            defaultErrorMessage =
                "停用可信联系人失败。",
            notFoundMessage =
                "可信联系人不存在，或者你无权停用。",
        )
    }

    /**
     * 校验可信联系人资料。
     */
    private fun validateContactInput(
        familyId: String,
        subjectUserId: String,
        displayName: String,
        relationshipLabel: String,
        phone: String,
        email: String,
        preferredChannel: String,
        priority: Int,
    ): String? {
        if (familyId.isBlank()) {
            return "家庭编号不能为空。"
        }

        if (subjectUserId.isBlank()) {
            return "被保护成员编号不能为空。"
        }

        if (displayName.isBlank()) {
            return "请输入联系人姓名。"
        }

        if (relationshipLabel.isBlank()) {
            return "请输入与联系人的关系。"
        }

        if (
            phone.isBlank() &&
            email.isBlank()
        ) {
            return "手机号和邮箱至少填写一项。"
        }

        if (
            email.isNotBlank() &&
            (
                    !email.contains("@") ||
                            email.startsWith("@") ||
                            email.endsWith("@")
                    )
        ) {
            return "请输入有效的联系人邮箱。"
        }

        if (
            preferredChannel ==
            TrustedContactChannelValue.PHONE &&
            phone.isBlank()
        ) {
            return "首选电话联系时必须填写手机号。"
        }

        if (
            preferredChannel ==
            TrustedContactChannelValue.EMAIL &&
            email.isBlank()
        ) {
            return "首选邮件联系时必须填写邮箱。"
        }

        if (preferredChannel.isBlank()) {
            return "请选择首选联系渠道。"
        }

        if (priority < 0) {
            return "联系人优先级不能小于 0。"
        }

        return null
    }

    /**
     * 执行 Retrofit 请求。
     */
    private suspend fun <T> executeRequest(
        request:
        suspend () -> Response<T>,
        defaultErrorMessage: String,
        notFoundMessage: String,
    ): TrustedContactOperationResult<T> {
        return try {
            val response =
                request()

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                TrustedContactOperationResult.Success(
                    data =
                        responseBody,
                )
            } else {
                createErrorResult(
                    response =
                        response,
                    defaultErrorMessage =
                        defaultErrorMessage,
                    notFoundMessage =
                        notFoundMessage,
                )
            }
        } catch (exception: IOException) {
            TrustedContactOperationResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            TrustedContactOperationResult.Error(
                message =
                    exception.message
                        ?: "${defaultErrorMessage}发生未知错误。",
            )
        }
    }

    /**
     * 根据状态码创建错误结果。
     */
    private fun createErrorResult(
        response: Response<*>,
        defaultErrorMessage: String,
        notFoundMessage: String,
    ): TrustedContactOperationResult.Error {
        val statusCode =
            response.code()

        val backendDetail =
            readBackendDetail(
                response =
                    response,
            )

        val message =
            backendDetail
                ?: when (statusCode) {
                    400 ->
                        "可信联系人请求格式不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户没有管理该可信联系人的权限。"

                    404 ->
                        notFoundMessage

                    409 ->
                        "可能已经存在相同的可信联系人记录。"

                    422 ->
                        "可信联系人资料未通过服务器校验。"

                    500 ->
                        "服务器处理可信联系人失败，请稍后重试。"

                    else ->
                        "$defaultErrorMessage" +
                                "服务器返回状态码：$statusCode。"
                }

        return TrustedContactOperationResult.Error(
            message =
                message,
            statusCode =
                statusCode,
            requiresLogin =
                statusCode == 401,
        )
    }

    /**
     * 读取 FastAPI 的 detail 字段。
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
                    .parseString(
                        errorText,
                    )
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