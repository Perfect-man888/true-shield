package com.trueshield.app.data.network

import com.trueshield.app.data.model.contact.TrustedContactCreateRequest
import com.trueshield.app.data.model.contact.TrustedContactDto
import com.trueshield.app.data.model.contact.TrustedContactListResponse
import com.trueshield.app.data.model.contact.TrustedContactUpdateRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 真信盾可信联系人接口。
 *
 * AuthInterceptor 会自动添加 JWT。
 */
interface TrustedContactApi {

    /**
     * 创建可信联系人。
     */
    @POST(
        "api/v1/families/{familyId}/trusted-contacts",
    )
    suspend fun createTrustedContact(
        @Path("familyId")
        familyId: String,

        @Body
        request: TrustedContactCreateRequest,
    ): Response<TrustedContactDto>

    /**
     * 查询当前成员在指定家庭中的可信联系人。
     */
    @GET(
        "api/v1/families/{familyId}/trusted-contacts",
    )
    suspend fun getTrustedContacts(
        @Path("familyId")
        familyId: String,
    ): Response<TrustedContactListResponse>

    /**
     * 修改可信联系人。
     */
    @PATCH(
        "api/v1/families/{familyId}/trusted-contacts/{contactId}",
    )
    suspend fun updateTrustedContact(
        @Path("familyId")
        familyId: String,

        @Path("contactId")
        contactId: String,

        @Body
        request: TrustedContactUpdateRequest,
    ): Response<TrustedContactDto>

    /**
     * 软停用可信联系人。
     *
     * 后端没有物理删除接口。
     */
    @POST(
        "api/v1/families/{familyId}/trusted-contacts/{contactId}/disable",
    )
    suspend fun disableTrustedContact(
        @Path("familyId")
        familyId: String,

        @Path("contactId")
        contactId: String,
    ): Response<TrustedContactDto>
}