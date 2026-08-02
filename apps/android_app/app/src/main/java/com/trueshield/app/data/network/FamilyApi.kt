package com.trueshield.app.data.network

import com.trueshield.app.data.model.family.FamilyCreateRequest
import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.data.model.family.FamilyListResponse
import com.trueshield.app.data.model.family.FamilyMemberListResponse
import com.trueshield.app.data.model.family.ReceivedFamilyInvitationListResponse
import com.trueshield.app.data.model.family.FamilyInvitationAcceptResponse
import com.trueshield.app.data.model.family.FamilyInvitationCreateRequest
import com.trueshield.app.data.model.family.FamilyInvitationDto
import com.trueshield.app.data.model.alert.AlertDeliveryAttemptListResponse
import com.trueshield.app.data.model.alert.DispatchFamilyAlertRequest
import com.trueshield.app.data.model.alert.FamilyAlertDispatchResponse
import com.trueshield.app.data.model.alert.FamilyAlertListResponse
import com.trueshield.app.data.model.alert.FamilyAlertResponse
import com.trueshield.app.data.model.alert.ResolveFamilyAlertRequest
import com.trueshield.app.data.model.alert.FamilyAlertPolicyResponse
import com.trueshield.app.data.model.alert.UpdateFamilyAlertPolicyRequest
import retrofit2.http.PATCH
import retrofit2.http.Query
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 真信盾家庭中心接口。
 *
 * AuthInterceptor 会自动添加：
 * Authorization: Bearer <access_token>
 */
interface FamilyApi {

    /**
     * 创建一个新家庭。
     *
     * 创建者会自动成为 owner。
     */
    @POST("api/v1/families")
    suspend fun createFamily(
        @Body
        request: FamilyCreateRequest,
    ): Response<FamilyDto>

    /**
     * 获取当前用户加入或创建的家庭。
     */
    @GET("api/v1/families")
    suspend fun getFamilies():
            Response<FamilyListResponse>

    /**
     * 获取指定家庭的成员。
     */
    @GET("api/v1/families/{familyId}/members")
    suspend fun getFamilyMembers(
        @Path("familyId")
        familyId: String,
    ): Response<FamilyMemberListResponse>

    /**
     * 通过邮箱邀请用户加入指定家庭。
     *
     * 当前只有家庭 owner 或有权限的管理员
     * 可以执行该操作。
     */
    @POST("api/v1/families/{familyId}/invitations")
    suspend fun createInvitation(
        @Path("familyId")
        familyId: String,

        @Body
        request: FamilyInvitationCreateRequest,
    ): Response<FamilyInvitationDto>

    /**
     * 获取当前用户收到的家庭邀请。
     */
    @GET("api/v1/families/invitations/received")
    suspend fun getReceivedInvitations():
            Response<ReceivedFamilyInvitationListResponse>

    /**
     * 接受收到的家庭邀请。
     */
    @POST(
        "api/v1/families/invitations/{invitationId}/accept",
    )
    suspend fun acceptInvitation(
        @Path("invitationId")
        invitationId: String,
    ): Response<FamilyInvitationAcceptResponse>

    /**
     * 拒绝收到的家庭邀请。
     */
    @POST(
        "api/v1/families/invitations/{invitationId}/decline",
    )
    suspend fun declineInvitation(
        @Path("invitationId")
        invitationId: String,
    ): Response<FamilyInvitationDto>

    /**
     * 根据一条高风险事件手动创建家庭告警。
     *
     * 当后端已经启用自动创建策略时，
     * 同一风险事件可能已经存在告警，
     * 此时后端可能返回 409。
     */
    @POST(
        "api/v1/families/{family_id}" +
                "/alerts/from-events/{event_id}"
    )
    suspend fun createFamilyAlertFromEvent(
        @Path("family_id")
        familyId: String,

        @Path("event_id")
        eventId: String,
    ): Response<FamilyAlertResponse>

    /**
     * 查询某个家庭的告警列表。
     */
    @GET(
        "api/v1/families/{family_id}/alerts"
    )
    suspend fun getFamilyAlerts(
        @Path("family_id")
        familyId: String,
    ): Response<FamilyAlertListResponse>

    /**
     * 查询单条家庭告警详情。
     */
    @GET(
        "api/v1/families/{family_id}" +
                "/alerts/{alert_id}"
    )
    suspend fun getFamilyAlertDetail(
        @Path("family_id")
        familyId: String,

        @Path("alert_id")
        alertId: String,
    ): Response<FamilyAlertResponse>

    /**
     * 确认已经查看并开始处理家庭告警。
     */
    @POST(
        "api/v1/families/{family_id}" +
                "/alerts/{alert_id}/acknowledge"
    )
    suspend fun acknowledgeFamilyAlert(
        @Path("family_id")
        familyId: String,

        @Path("alert_id")
        alertId: String,
    ): Response<FamilyAlertResponse>

    /**
     * 完成家庭告警处理。
     */
    @POST(
        "api/v1/families/{family_id}" +
                "/alerts/{alert_id}/resolve"
    )
    suspend fun resolveFamilyAlert(
        @Path("family_id")
        familyId: String,

        @Path("alert_id")
        alertId: String,

        @Body
        request: ResolveFamilyAlertRequest,
    ): Response<FamilyAlertResponse>

    /**
     * 手动发送家庭告警。
     *
     * 正常发送时，模拟失败列表传空列表。
     */
    @POST(
        "api/v1/families/{family_id}" +
                "/alerts/{alert_id}/dispatch"
    )
    suspend fun dispatchFamilyAlert(
        @Path("family_id")
        familyId: String,

        @Path("alert_id")
        alertId: String,

        @Body
        request: DispatchFamilyAlertRequest,
    ): Response<FamilyAlertDispatchResponse>

    /**
     * 查询家庭告警的所有发送尝试记录。
     *
     * 例如：
     * 第一次发送失败；
     * 第二次重试发送成功。
     */
    @GET(
        "api/v1/families/{family_id}" +
                "/alerts/{alert_id}/delivery-attempts"
    )
    suspend fun getFamilyAlertDeliveryAttempts(
        @Path("family_id")
        familyId: String,

        @Path("alert_id")
        alertId: String,
    ): Response<AlertDeliveryAttemptListResponse>

    /**
     * 重试当前告警中发送失败的通知。
     */
    @POST(
        "api/v1/families/{family_id}" +
                "/alerts/{alert_id}/retry-failed"
    )
    suspend fun retryFailedFamilyAlertDeliveries(
        @Path("family_id")
        familyId: String,

        @Path("alert_id")
        alertId: String,
    ): Response<FamilyAlertDispatchResponse>

    /**
     * 查询家庭告警自动触发策略。
     */
    @GET("api/v1/families/{family_id}/alert-policy")
    suspend fun getFamilyAlertPolicy(
        @Path("family_id")
        familyId: String,
    ): Response<FamilyAlertPolicyResponse>

    /**
     * 更新家庭告警自动触发策略。
     */
    @PATCH("api/v1/families/{family_id}/alert-policy")
    suspend fun updateFamilyAlertPolicy(
        @Path("family_id")
        familyId: String,

        @Body
        request: UpdateFamilyAlertPolicyRequest,
    ): Response<FamilyAlertPolicyResponse>
}