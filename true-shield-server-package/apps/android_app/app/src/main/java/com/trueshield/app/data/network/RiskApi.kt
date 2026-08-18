package com.trueshield.app.data.network

import com.trueshield.app.data.model.risk.ImageRiskResponse
import com.trueshield.app.data.model.risk.RiskEventDetailResponse
import com.trueshield.app.data.model.risk.TextRiskRequest
import com.trueshield.app.data.model.risk.TextRiskResponse
import com.trueshield.app.data.model.risk.VoiceRiskRequest
import com.trueshield.app.data.model.risk.VoiceRiskResponse
import com.trueshield.app.data.model.risk.dashboard.RiskDashboardResponse
import com.trueshield.app.data.model.risk.feedback.CorrectedRiskReanalysisResponse
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackRequest
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackResponse
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackStatisticsResponse
import com.trueshield.app.data.model.risk.url.UrlRiskRequest
import com.trueshield.app.data.model.risk.url.UrlRiskResponse
import com.trueshield.app.data.model.risk.history.RiskEventListResponse
import com.trueshield.app.data.model.risk.report.RiskReportSummaryResponse
import retrofit2.http.Query
import retrofit2.http.PUT
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * 真信盾风险检测接口。
 */
interface RiskApi {

    /**
     * 分析并保存可疑文本。
     */
    @POST("api/v1/risk/text/analyze")
    suspend fun analyzeText(
        @Body
        request: TextRiskRequest,
    ): Response<TextRiskResponse>

    /**
     * 上传录音，由后端本地 Whisper 转写并执行风险检测。
     */
    @Multipart
    @POST("api/v1/risk/voice/audio/analyze")
    suspend fun analyzeVoiceAudio(
        @Part
        audio: MultipartBody.Part,

        @Part("language")
        language: RequestBody,
    ): Response<VoiceRiskResponse>

    /**
     * 使用用户修正后的转写文本重新执行风险检测。
     */
    @POST("api/v1/risk/voice/analyze")
    suspend fun analyzeVoice(
        @Body
        request: VoiceRiskRequest,
    ): Response<VoiceRiskResponse>

    /**
     * 上传聊天截图并执行 OCR 与风险检测。
     *
     * Multipart 字段名称必须为 image。
     */
    @Multipart
    @POST("api/v1/risk/image/analyze")
    suspend fun analyzeImage(
        @Part
        image: MultipartBody.Part,
    ): Response<ImageRiskResponse>

    /**
     * 分析并保存可疑 URL。
     *
     * 当 resolveRedirects 为 true 时，
     * 后端会在安全检查后尝试解析重定向链。
     */
    @POST("api/v1/risk/url/analyze")
    suspend fun analyzeUrl(
        @Body
        request: UrlRiskRequest,
    ): Response<UrlRiskResponse>

    /**
     * 分页获取当前用户的风险检测历史。
     *
     * 包括文本、图片和 URL 风险事件。
     */
    @GET("api/v1/risk/events")
    suspend fun getRiskEvents(
        @Query("limit")
        limit: Int,

        @Query("offset")
        offset: Int,
    ): Response<RiskEventListResponse>


    /**
     * 获取个人或家庭风险统计仪表盘。
     *
     * familyId 为空时统计当前用户；
     * 非空时统计指定家庭。
     */
    @GET("api/v1/risk/dashboard")
    suspend fun getRiskDashboard(
        @Query("period_days")
        periodDays: Int,

        @Query("family_id")
        familyId: String? = null,
    ): Response<RiskDashboardResponse>

    /**
     * 生成个人或家庭风险报告预览。
     */
    @GET("api/v1/risk/reports/summary")
    suspend fun getRiskReportSummary(
        @Query("period_days")
        periodDays: Int,

        @Query("family_id")
        familyId: String? = null,
    ): Response<RiskReportSummaryResponse>

    /**
     * 下载脱敏风险报告 PDF。
     */
    @Streaming
    @GET("api/v1/risk/reports/pdf")
    suspend fun downloadRiskReportPdf(
        @Query("period_days")
        periodDays: Int,

        @Query("family_id")
        familyId: String? = null,
    ): Response<ResponseBody>

    /**
     * 获取当前登录用户的风险反馈统计。
     */
    @GET("api/v1/risk/feedback/statistics")
    suspend fun getRiskFeedbackStatistics(
    ): Response<RiskFeedbackStatisticsResponse>

    /**
     * 根据事件编号读取完整风险事件。
     */
    @GET("api/v1/risk/events/{eventId}")
    suspend fun getRiskEventDetail(
        @Path("eventId")
        eventId: String,
    ): Response<RiskEventDetailResponse>

    /**
     * 创建或更新风险反馈。
     */
    @PUT(
        "api/v1/risk/events/{eventId}/feedback",
    )
    suspend fun upsertRiskFeedback(
        @Path("eventId")
        eventId: String,

        @Body
        request: RiskFeedbackRequest,
    ): Response<RiskFeedbackResponse>

    /**
     * 获取当前用户对指定事件提交的反馈。
     */
    @GET(
        "api/v1/risk/events/{eventId}/feedback",
    )
    suspend fun getRiskFeedback(
        @Path("eventId")
        eventId: String,
    ): Response<RiskFeedbackResponse>

    /**
     * 使用用户人工修正的 OCR 文本重新分析。
     *
     * 该接口仅适用于图片风险事件。
     */
    @POST(
        "api/v1/risk/events/{eventId}/reanalyze-corrected",
    )
    suspend fun reanalyzeCorrectedText(
        @Path("eventId")
        eventId: String,
    ): Response<CorrectedRiskReanalysisResponse>
}