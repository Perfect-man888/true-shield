package com.trueshield.app.data.network

import com.google.gson.JsonObject
import com.trueshield.app.data.model.account.ChangePasswordRequest
import com.trueshield.app.data.model.account.UserAccountResponse
import com.trueshield.app.data.model.push.PushDeviceRegisterRequest
import com.trueshield.app.data.model.push.PushDeviceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 当前登录用户资料与账户安全接口。
 */
interface UserApi {

    @GET("api/v1/users/me")
    suspend fun getCurrentUser():
            Response<UserAccountResponse>

    @PATCH("api/v1/users/me")
    suspend fun updateCurrentUser(
        @Body request: JsonObject,
    ): Response<UserAccountResponse>

    @PATCH("api/v1/users/me/password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest,
    ): Response<Unit>

    @POST("api/v1/users/me/push-devices")
    suspend fun registerPushDevice(
        @Body request: PushDeviceRegisterRequest,
    ): Response<PushDeviceResponse>

    @DELETE(
        "api/v1/users/me/push-devices/{installationId}",
    )
    suspend fun unregisterPushDevice(
        @Path("installationId")
        installationId: String,
    ): Response<Unit>
}
