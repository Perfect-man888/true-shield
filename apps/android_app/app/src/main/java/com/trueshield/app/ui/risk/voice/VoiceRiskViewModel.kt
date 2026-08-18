package com.trueshield.app.ui.risk.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trueshield.app.TrueShieldApplication
import com.trueshield.app.data.repository.VoiceRiskAnalysisResult
import com.trueshield.app.data.repository.VoiceRiskRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceRiskViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app = application as TrueShieldApplication

    private val repository = VoiceRiskRepository(
        riskApi = app.apiClient.riskApi,
    )

    private val _uiState = MutableStateFlow(
        VoiceRiskUiState(),
    )

    val uiState: StateFlow<VoiceRiskUiState> =
        _uiState.asStateFlow()

    fun onTranscriptChange(value: String) {
        _uiState.update {
            it.copy(
                transcript = value.take(10_000),
                errorMessage = null,
            )
        }
    }

    fun onRecordingStarted() {
        deleteRecordedAudio()
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingDurationSeconds = 0,
                recordedAudioPath = null,
                recordedAudioName = null,
                recordedAudioSizeBytes = null,
                audioSource = null,
                result = null,
                transcript = "",
                serverTranscript = "",
                statusMessage = "正在录音，请清晰说出可疑通话内容……",
                errorMessage = null,
            )
        }
    }

    fun onRecordingDurationChanged(seconds: Int) {
        _uiState.update {
            if (!it.isRecording) it
            else it.copy(recordingDurationSeconds = seconds)
        }
    }

    fun onRecordingStopped(
        file: File,
        durationSeconds: Double,
    ) {
        deleteRecordedAudio()

        val source = if (
            VoiceAudioFileImporter.isImportedFile(file)
        ) {
            VoiceAudioSource.IMPORTED
        } else {
            VoiceAudioSource.RECORDED
        }

        _uiState.update {
            it.copy(
                isRecording = false,
                recordingDurationSeconds = if (
                    source == VoiceAudioSource.RECORDED
                ) {
                    durationSeconds.toInt().coerceAtLeast(1)
                } else {
                    0
                },
                recordedAudioPath = file.absolutePath,
                recordedAudioName = file.name,
                recordedAudioSizeBytes = file.length(),
                audioSource = source,
                result = null,
                transcript = "",
                serverTranscript = "",
                statusMessage = if (
                    source == VoiceAudioSource.IMPORTED
                ) {
                    "已选择本地录音。点击上传后，将由后端本地 FunASR 转写并检测。"
                } else {
                    "录音完成。点击上传后，将由后端本地 FunASR 转写并检测。"
                },
                errorMessage = null,
            )
        }
    }

    fun onRecordingError(message: String) {
        _uiState.update {
            it.copy(
                isRecording = false,
                statusMessage = null,
                errorMessage = message,
            )
        }
    }

    fun uploadAndAnalyze() {
        val path = _uiState.value.recordedAudioPath
        if (path.isNullOrBlank()) {
            onRecordingError("请先录制或选择一段需要检测的语音。")
            return
        }
        if (_uiState.value.isLoading) return

        val audioFile = File(path)
        _uiState.update {
            it.copy(
                isLoading = true,
                result = null,
                statusMessage =
                    "正在上传音频。本地 FunASR 正在转写并执行风险检测……",
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            handleResult(
                result = repository.analyzeAudio(
                    audioFile = audioFile,
                    uploadFileName = currentUploadFileName(audioFile),
                ),
                deleteAudioOnSuccess = true,
            )
        }
    }

    fun reanalyzeCorrectedTranscript() {
        val current = _uiState.value
        if (!current.canReanalyzeCorrection) return

        _uiState.update {
            it.copy(
                isLoading = true,
                result = null,
                statusMessage = "正在使用修正后的文字重新检测……",
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            handleResult(
                result = repository.analyzeCorrectedTranscript(
                    transcript = current.transcript,
                    transcriptionModel =
                        current.result?.transcriptionModel,
                    durationSeconds =
                        current.result?.durationSeconds,
                ),
                deleteAudioOnSuccess = false,
            )
        }
    }

    private fun handleResult(
        result: VoiceRiskAnalysisResult,
        deleteAudioOnSuccess: Boolean,
    ) {
        try {
            when (result) {
                is VoiceRiskAnalysisResult.Success -> {
                    if (deleteAudioOnSuccess) {
                        deleteRecordedAudio()
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRecording = false,
                            result = result.response,
                            transcript = result.response.transcript,
                            serverTranscript = result.response.transcript,
                            recordedAudioPath =
                                if (deleteAudioOnSuccess) null
                                else it.recordedAudioPath,
                            recordedAudioName =
                                if (deleteAudioOnSuccess) null
                                else it.recordedAudioName,
                            recordedAudioSizeBytes =
                                if (deleteAudioOnSuccess) null
                                else it.recordedAudioSizeBytes,
                            audioSource =
                                if (deleteAudioOnSuccess) null
                                else it.audioSource,
                            statusMessage =
                                "本地语音转写与风险检测已完成，可核对并修正文字。",
                            errorMessage = null,
                        )
                    }
                }

                is VoiceRiskAnalysisResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = result.message,
                            sessionExpired = result.requiresLogin,
                        )
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statusMessage = null,
                    errorMessage = exception.message
                        ?: "语音风险检测失败。",
                )
            }
        }
    }

    fun clearResult() {
        _uiState.update {
            it.copy(
                result = null,
                transcript = "",
                serverTranscript = "",
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    fun clearAll() {
        deleteRecordedAudio()
        _uiState.value = VoiceRiskUiState()
    }

    private fun currentUploadFileName(audioFile: File): String {
        return _uiState.value.recordedAudioName
            ?.takeIf { it.isNotBlank() }
            ?: audioFile.name
    }

    private fun deleteRecordedAudio() {
        val file = _uiState.value.recordedAudioPath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?: return

        VoiceAudioFileImporter.deleteImportedFile(file)
    }

    fun consumeSessionExpired() {
        _uiState.update {
            it.copy(sessionExpired = false)
        }
    }

    override fun onCleared() {
        deleteRecordedAudio()
        super.onCleared()
    }
}
