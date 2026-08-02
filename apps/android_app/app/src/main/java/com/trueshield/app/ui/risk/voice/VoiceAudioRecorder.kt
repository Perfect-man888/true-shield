package com.trueshield.app.ui.risk.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Android 本地录音器。
 *
 * 录音保存到应用缓存目录，上传成功或用户清空后立即删除。
 */
class VoiceAudioRecorder(
    private val context: Context,
) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtElapsedRealtime: Long = 0L

    @Suppress("DEPRECATION")
    fun start(): File {
        cancel()

        val file = File.createTempFile(
            "true-shield-voice-",
            ".m4a",
            context.cacheDir,
        )

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
        } catch (exception: Exception) {
            runCatching { recorder.release() }
            file.delete()
            throw exception
        }

        mediaRecorder = recorder
        outputFile = file
        startedAtElapsedRealtime = SystemClock.elapsedRealtime()
        return file
    }

    fun stop(): RecordedVoiceAudio {
        val recorder = mediaRecorder
            ?: error("当前没有正在进行的录音。")
        val file = outputFile
            ?: error("没有找到当前录音文件。")

        val durationMillis = (
            SystemClock.elapsedRealtime() - startedAtElapsedRealtime
        ).coerceAtLeast(0L)

        try {
            recorder.stop()
        } catch (exception: RuntimeException) {
            file.delete()
            throw IllegalStateException(
                "录音时间过短或录音数据无效，请重新录制。",
                exception,
            )
        } finally {
            recorder.reset()
            recorder.release()
            mediaRecorder = null
            outputFile = null
            startedAtElapsedRealtime = 0L
        }

        if (!file.exists() || file.length() < 256L) {
            file.delete()
            error("录音内容为空或过短，请重新录制。")
        }

        return RecordedVoiceAudio(
            file = file,
            durationSeconds = durationMillis / 1000.0,
        )
    }

    fun cancel() {
        val recorder = mediaRecorder
        if (recorder != null) {
            runCatching { recorder.stop() }
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
        mediaRecorder = null
        startedAtElapsedRealtime = 0L

        outputFile?.delete()
        outputFile = null
    }
}

data class RecordedVoiceAudio(
    val file: File,
    val durationSeconds: Double,
)
