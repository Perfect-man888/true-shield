package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.family.FamilyCreateRequest
import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.data.model.family.FamilyListResponse
import com.trueshield.app.data.model.family.FamilyMemberListResponse
import com.trueshield.app.data.model.family.ReceivedFamilyInvitationListResponse
import com.trueshield.app.data.network.FamilyApi
import retrofit2.Response
import java.io.IOException
import com.trueshield.app.data.model.family.FamilyInvitationAcceptResponse
import com.trueshield.app.data.model.family.FamilyInvitationCreateRequest
import com.trueshield.app.data.model.family.FamilyInvitationDto
import com.trueshield.app.data.model.family.FamilyInvitationRoleValue

/**
 * 家庭接口的统一调用结果。
 */
sealed interface FamilyOperationResult<out T> {

    data class Success<T>(
        val data: T,
    ) : FamilyOperationResult<T>

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : FamilyOperationResult<Nothing>
}

/**
 * 家庭中心数据仓库。
 *
 * ViewModel 和页面不直接访问 Retrofit。
 */
class FamilyRepository(
    private val familyApi: FamilyApi,
) {

    /**
     * 创建家庭。
     */
    suspend fun createFamily(
        name: String,
    ): FamilyOperationResult<FamilyDto> {
        val cleanedName =
            name.trim()

        if (cleanedName.isBlank()) {
            return FamilyOperationResult.Error(
                message = "请输入家庭名称。",
            )
        }

        return executeRequest(
            request = {
                familyApi.createFamily(
                    request =
                        FamilyCreateRequest(
                            name = cleanedName,
                        ),
                )
            },
            defaultErrorMessage =
                "创建家庭失败。",
            notFoundMessage =
                "创建家庭接口不存在。",
        )
    }

    /**
     * 查询当前用户的家庭。
     */
    suspend fun getFamilies():
            FamilyOperationResult<FamilyListResponse> {
        return executeRequest(
            request = {
                familyApi.getFamilies()
            },
            defaultErrorMessage =
                "读取家庭列表失败。",
            notFoundMessage =
                "家庭列表接口不存在。",
        )
    }

    /**
     * 查询指定家庭的成员。
     */
    suspend fun getFamilyMembers(
        familyId: String,
    ): FamilyOperationResult<FamilyMemberListResponse> {
        val cleanedFamilyId =
            familyId.trim()

        if (cleanedFamilyId.isBlank()) {
            return FamilyOperationResult.Error(
                message = "家庭编号不能为空。",
            )
        }

        return executeRequest(
            request = {
                familyApi.getFamilyMembers(
                    familyId =
                        cleanedFamilyId,
                )
            },
            defaultErrorMessage =
                "读取家庭成员失败。",
            notFoundMessage =
                "家庭不存在，或者你无权查看该家庭。",
        )
    }

    /**
     * 通过邮箱邀请用户加入家庭。
     */
    suspend fun createInvitation(
        familyId: String,
        inviteeEmail: String,
    ): FamilyOperationResult<FamilyInvitationDto> {
        val cleanedFamilyId =
            familyId.trim()

        val cleanedEmail =
            inviteeEmail.trim()

        if (cleanedFamilyId.isBlank()) {
            return FamilyOperationResult.Error(
                message = "家庭编号不能为空。",
            )
        }

        if (cleanedEmail.isBlank()) {
            return FamilyOperationResult.Error(
                message = "请输入被邀请人的邮箱。",
            )
        }

        if (
            !cleanedEmail.contains("@") ||
            cleanedEmail.startsWith("@") ||
            cleanedEmail.endsWith("@")
        ) {
            return FamilyOperationResult.Error(
                message = "请输入有效的邮箱地址。",
            )
        }

        return executeRequest(
            request = {
                familyApi.createInvitation(
                    familyId =
                        cleanedFamilyId,
                    request =
                        FamilyInvitationCreateRequest(
                            inviteeEmail =
                                cleanedEmail,
                            role =
                                FamilyInvitationRoleValue
                                    .MEMBER,
                        ),
                )
            },
            defaultErrorMessage =
                "发送家庭邀请失败。",
            notFoundMessage =
                "家庭不存在、被邀请用户不存在，" +
                        "或者你无权邀请成员。",
        )
    }

    /**
     * 接受家庭邀请。
     *
     * 成功后当前用户会成为该家庭的正式成员。
     */
    suspend fun acceptInvitation(
        invitationId: String,
    ): FamilyOperationResult<FamilyInvitationAcceptResponse> {
        val cleanedInvitationId =
            invitationId.trim()

        if (cleanedInvitationId.isBlank()) {
            return FamilyOperationResult.Error(
                message = "家庭邀请编号不能为空。",
            )
        }

        return executeRequest(
            request = {
                familyApi.acceptInvitation(
                    invitationId =
                        cleanedInvitationId,
                )
            },
            defaultErrorMessage =
                "接受家庭邀请失败。",
            notFoundMessage =
                "家庭邀请不存在、已过期，" +
                        "或者该邀请不属于当前用户。",
        )
    }

    /**
     * 拒绝家庭邀请。
     */
    suspend fun declineInvitation(
        invitationId: String,
    ): FamilyOperationResult<FamilyInvitationDto> {
        val cleanedInvitationId =
            invitationId.trim()

        if (cleanedInvitationId.isBlank()) {
            return FamilyOperationResult.Error(
                message = "家庭邀请编号不能为空。",
            )
        }

        return executeRequest(
            request = {
                familyApi.declineInvitation(
                    invitationId =
                        cleanedInvitationId,
                )
            },
            defaultErrorMessage =
                "拒绝家庭邀请失败。",
            notFoundMessage =
                "家庭邀请不存在、已过期，" +
                        "或者该邀请不属于当前用户。",
        )
    }

    /**
     * 查询当前用户收到的家庭邀请。
     *
     * 目前仅确认了列表外层结构。
     */
    suspend fun getReceivedInvitations():
            FamilyOperationResult<ReceivedFamilyInvitationListResponse> {
        return executeRequest(
            request = {
                familyApi
                    .getReceivedInvitations()
            },
            defaultErrorMessage =
                "读取家庭邀请失败。",
            notFoundMessage =
                "家庭邀请接口不存在。",
        )
    }

    /**
     * 执行一个 Retrofit 请求并统一处理错误。
     */
    private suspend fun <T> executeRequest(
        request:
        suspend () -> Response<T>,
        defaultErrorMessage: String,
        notFoundMessage: String,
    ): FamilyOperationResult<T> {
        return try {
            val response =
                request()

            val responseBody =
                response.body()

            if (
                response.isSuccessful &&
                responseBody != null
            ) {
                FamilyOperationResult.Success(
                    data = responseBody,
                )
            } else {
                createErrorResult(
                    response = response,
                    defaultErrorMessage =
                        defaultErrorMessage,
                    notFoundMessage =
                        notFoundMessage,
                )
            }
        } catch (exception: IOException) {
            FamilyOperationResult.Error(
                message =
                    "无法连接真信盾服务器，" +
                            "请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            FamilyOperationResult.Error(
                message =
                    exception.message ?: "${defaultErrorMessage}发生未知错误。",
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
    ): FamilyOperationResult.Error {
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
                        "家庭请求格式不正确。"

                    401 ->
                        "登录状态已经过期，请重新登录。"

                    403 ->
                        "当前账户没有执行该家庭操作的权限。"

                    404 ->
                        notFoundMessage

                    409 ->
                        "该用户可能已经是家庭成员，" +
                                "或者已经存在一条待处理邀请。"

                    422 ->
                        "家庭信息未通过服务器校验。"

                    500 ->
                        "服务器处理家庭操作失败，请稍后重试。"

                    else ->
                        "$defaultErrorMessage" +
                                "服务器返回状态码：$statusCode。"
                }

        return FamilyOperationResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin =
                statusCode == 401,
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