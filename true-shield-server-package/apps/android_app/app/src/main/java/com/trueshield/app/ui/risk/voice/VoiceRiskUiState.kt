package com.trueshield.app.ui.risk.voice

import com.trueshield.app.data.model.risk.VoiceRiskResponse

enum class VoiceAudioSource {
    RECORDED,
    IMPORTED,
}

data class VoiceRiskUiState(
    val transcript: String = "",
    val serverTranscript: String = "",
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val recordedAudioPath: String? = null,
    val recordedAudioName: String? = null,
    val recordedAudioSizeBytes: Long? = null,
    val audioSource: VoiceAudioSource? = null,
    val isLoading: Boolean = false,
    val result: VoiceRiskResponse? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false,
) {
    val hasRecording: Boolean
        get() = !recordedAudioPath.isNullOrBlank()

    val canUploadAndAnalyze: Boolean
        get() = hasRecording && !isRecording && !isLoading

    val hasTranscriptCorrection: Boolean
        get() = transcript.isNotBlank() &&
            serverTranscript.isNotBlank() &&
            transcript.trim() != serverTranscript.trim()

    val canReanalyzeCorrection: Boolean
        get() = hasTranscriptCorrection && !isRecording && !isLoading
}
