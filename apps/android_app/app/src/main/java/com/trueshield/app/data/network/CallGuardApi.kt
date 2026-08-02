package com.trueshield.app.data.network

import com.trueshield.app.data.model.callguard.CallGuardHelpRequest
import com.trueshield.app.data.model.callguard.CallGuardHelpResponse
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeRequest
import com.trueshield.app.data.model.callguard.CallNumberAnalyzeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 通话安全护航接口。
 */
interface CallGuardApi {

    @POST("api/v1/call-guard/analyze-number")
    suspend fun analyzeNumber(
        @Body
        request: CallNumberAnalyzeRequest,
    ): Response<CallNumberAnalyzeResponse>

    @POST("api/v1/call-guard/help")
    suspend fun requestFamilyHelp(
        @Body
        request: CallGuardHelpRequest,
    ): Response<CallGuardHelpResponse>
}
