package com.trueshield.app.data.repository

import com.google.gson.JsonParser
import com.trueshield.app.data.model.risk.VoiceRiskRequest
import com.trueshield.app.data.model.risk.VoiceRiskResponse
import com.trueshield.app.data.network.RiskApi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

private const val MAX_VOICE_TRANSCRIPT_LENGTH = 10_000

sealed interface VoiceRiskAnalysisResult {
    data class Success(
        val response: VoiceRiskResponse,
    ) : VoiceRiskAnalysisResult

    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val requiresLogin: Boolean = false,
    ) : VoiceRiskAnalysisResult
}

class VoiceRiskRepository(
    private val riskApi: RiskApi,
) {
    suspend fun analyzeAudio(
        audioFile: File,
        uploadFileName: String = audioFile.name,
        language: String = "zh-CN",
    ): VoiceRiskAnalysisResult {
        if (!audioFile.exists() || audioFile.length() < 256L) {
            return VoiceRiskAnalysisResult.Error(
                message = "音频文件为空或已失效，请重新录制或选择。",
            )
        }

        return execute {
            val audioBody = audioFile.asRequestBody(
                detectAudioMediaType(uploadFileName).toMediaType(),
            )
            val audioPart = MultipartBody.Part.createFormData(
                "audio",
                uploadFileName,
                audioBody,
            )
            val languageBody = language.toRequestBody(
                "text/plain".toMediaType(),
            )

            riskApi.analyzeVoiceAudio(
                audio = audioPart,
                language = languageBody,
            )
        }
    }


    private fun detectAudioMediaType(fileName: String): String {
        return when (
            fileName.substringAfterLast('.', "").lowercase()
        ) {
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }

    suspend fun analyzeCorrectedTranscript(
        transcript: String,
        transcriptionModel: String? = null,
        durationSeconds: Double? = null,
    ): VoiceRiskAnalysisResult {
        val cleaned = transcript.trim()

        if (cleaned.isBlank()) {
            return VoiceRiskAnalysisResult.Error(
                message = "请先输入需要重新检测的转写文字。",
            )
        }

        if (cleaned.length > MAX_VOICE_TRANSCRIPT_LENGTH) {
            return VoiceRiskAnalysisResult.Error(
                message = "语音转写文本不能超过 10000 个字符。",
            )
        }

        return execute {
            riskApi.analyzeVoice(
                request = VoiceRiskRequest(
                    transcript = cleaned,
                    recognitionProvider = "manual_correction",
                    transcriptionModel = transcriptionModel,
                    durationSeconds = durationSeconds,
                ),
            )
        }
    }

    private suspend fun execute(
        request: suspend () -> Response<VoiceRiskResponse>,
    ): VoiceRiskAnalysisResult {
        return try {
            val response = request()
            val body = response.body()

            if (response.isSuccessful && body != null) {
                VoiceRiskAnalysisResult.Success(body)
            } else {
                createError(response)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            VoiceRiskAnalysisResult.Error(
                message =
                    "无法连接真信盾服务器，请检查后端是否已经启动。",
            )
        } catch (exception: Exception) {
            VoiceRiskAnalysisResult.Error(
                message = exception.message
                    ?: "语音风险检测过程中发生未知错误。",
            )
        }
    }

    private fun createError(
        response: Response<*>,
    ): VoiceRiskAnalysisResult.Error {
        val statusCode = response.code()
        val backendDetail = try {
            response.errorBody()
                ?.string()
                ?.takeIf { it.isNotBlank() }
                ?.let { raw ->
                    JsonParser.parseString(raw)
                        .asJsonObject
                        .get("detail")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                }
        } catch (_: Exception) {
            null
        }

        val message = backendDetail ?: when (statusCode) {
            401 -> "登录状态已失效，请重新登录。"
            413 -> "录音文件过大或录音时间过长，请缩短后重试。"
            415 -> "音频格式不受支持，请重新录制或选择其他文件。"
            422 -> "音频中没有识别到有效讲话，请重新录制或选择其他文件。"
            503 -> "本地语音模型尚未准备好，请检查后端模型配置。"
            504 -> "本地语音转写超时，请缩短录音后重试。"
            500 -> "服务器内部错误，请稍后重试。"
            else -> "语音风险检测失败，状态码：$statusCode。"
        }

        return VoiceRiskAnalysisResult.Error(
            message = message,
            statusCode = statusCode,
            requiresLogin = statusCode == 401,
        )
    }
}
