package com.trueshield.app.ui.risk.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.trueshield.app.data.model.risk.VoiceRiskResponse
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.SecondaryActionButton
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.risk.common.AnalysisProgressCard
import com.trueshield.app.ui.risk.common.DetectionErrorCard
import com.trueshield.app.ui.risk.common.DetectionIntro
import com.trueshield.app.ui.risk.common.PrivacyNoticeCard
import com.trueshield.app.ui.risk.common.RiskDetectionPage
import com.trueshield.app.ui.risk.common.UnifiedRiskEvidenceUi
import com.trueshield.app.ui.risk.common.UnifiedRiskResultContent
import com.trueshield.app.ui.risk.common.UnifiedRiskResultUi
import com.trueshield.app.ui.risk.common.buildImmediateAction
import com.trueshield.app.ui.risk.common.buildRiskConclusion
import com.trueshield.app.ui.risk.common.formatAiConfidence
import com.trueshield.app.ui.risk.common.formatRiskScore
import com.trueshield.app.ui.risk.common.riskAnalysisEngineName
import com.trueshield.app.ui.risk.common.riskLevelDisplayName
import com.trueshield.app.ui.onboarding.FirstUseGuideDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_RECORDING_SECONDS = 180
private val VoicePurple = Color(0xFF6D4CC7)
private val VoicePurpleContainer = Color(0xFFEDE4FF)

/**
 * 新版语音风险检测页面。
 */
@Composable
fun VoiceRiskScreen(
    state: VoiceRiskUiState,
    onTranscriptChange: (String) -> Unit,
    onRecordingStarted: () -> Unit,
    onRecordingDurationChanged: (Int) -> Unit,
    onRecordingStopped: (File, Double) -> Unit,
    onRecordingError: (String) -> Unit,
    onUploadAndAnalyzeClick: () -> Unit,
    onReanalyzeCorrectionClick: () -> Unit,
    onClearResultClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onImmediateHelpClick: (String) -> Unit,
    onViewDetailClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    FirstUseGuideDialog(
        guideKey = "voice_risk_guide_v1",
        title = "语音检测怎么用？",
        description = "可以现场录音，也可以导入已有的通话或语音文件。",
        steps = listOf(
            "录音前确认已获得必要授权并注意当地法律",
            "上传后先检查转写文字是否准确",
            "需要时修正转写内容并重新分析",
        ),
    )

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val recorder = remember {
        VoiceAudioRecorder(context.applicationContext)
    }
    val coroutineScope = rememberCoroutineScope()
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var isImportingAudio by remember { mutableStateOf(false) }

    fun startRecordingInternal() {
        try {
            recorder.start()
            recordingStartedAt = SystemClock.elapsedRealtime()
            onRecordingStarted()
        } catch (exception: Exception) {
            recorder.cancel()
            onRecordingError(
                exception.message ?: "无法启动录音，请检查麦克风权限。",
            )
        }
    }

    fun stopRecordingInternal() {
        try {
            val recorded = recorder.stop()
            recordingStartedAt = 0L
            onRecordingStopped(recorded.file, recorded.durationSeconds)
        } catch (exception: Exception) {
            recorder.cancel()
            recordingStartedAt = 0L
            onRecordingError(
                exception.message ?: "结束录音失败，请重新录制。",
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecordingInternal()
        } else {
            onRecordingError("未授予麦克风权限，请在系统设置中允许后重试。")
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isImportingAudio = true
                try {
                    val imported = withContext(Dispatchers.IO) {
                        VoiceAudioFileImporter.import(
                            context = context.applicationContext,
                            uri = uri,
                        )
                    }
                    onRecordingStopped(imported.file, 0.0)
                } catch (exception: Exception) {
                    onRecordingError(
                        exception.message ?: "导入本地录音失败，请重新选择。",
                    )
                } finally {
                    isImportingAudio = false
                }
            }
        }
    }

    fun requestStartRecording() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startRecordingInternal()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.isRecording) {
        while (state.isRecording) {
            delay(1_000)
            val seconds = (
                (SystemClock.elapsedRealtime() - recordingStartedAt) / 1_000L
            ).toInt().coerceAtLeast(0)
            onRecordingDurationChanged(seconds)

            if (seconds >= MAX_RECORDING_SECONDS) {
                stopRecordingInternal()
                break
            }
        }
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    RiskDetectionPage(
        title = "语音检测",
        subtitle = "录制或导入通话音频，由本地模型转写并分析",
        onBackClick = {
            recorder.cancel()
            onBackClick()
        },
    ) {
        item {
            DetectionIntro(
                title = "分析可疑通话",
                description = "支持现场录音和本地音频文件，最长录制 3 分钟。",
                icon = Icons.Outlined.GraphicEq,
                iconColor = VoicePurple,
                iconBackground = VoicePurpleContainer,
            )
        }

        item {
            TrueShieldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "录音或选择音频",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Button(
                        onClick = {
                            if (state.isRecording) {
                                stopRecordingInternal()
                            } else {
                                requestStartRecording()
                            }
                        },
                        enabled = !state.isLoading && !isImportingAudio,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isRecording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                    ) {
                        Icon(
                            imageVector = if (state.isRecording) {
                                Icons.Outlined.StopCircle
                            } else {
                                Icons.Outlined.Mic
                            },
                            contentDescription = null,
                        )
                        Text(
                            text = if (state.isRecording) {
                                "停止录音（${formatDuration(state.recordingDurationSeconds)}）"
                            } else {
                                "开始录音"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            audioPickerLauncher.launch(
                                arrayOf("audio/*", "application/octet-stream"),
                            )
                        },
                        enabled = !state.isRecording && !state.isLoading && !isImportingAudio,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        if (isImportingAudio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("正在导入", modifier = Modifier.padding(start = 8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AudioFile,
                                contentDescription = null,
                            )
                            Text("选择本地录音", modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    Text(
                        text = "支持 m4a、mp3、wav、aac、mp4、ogg，单个文件不超过 20 MB。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.hasRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (state.audioSource) {
                                        VoiceAudioSource.IMPORTED ->
                                            state.recordedAudioName ?: "本地录音"
                                        else -> "现场录音 ${formatDuration(state.recordingDurationSeconds)}"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatBytes(state.recordedAudioSizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        PrimaryActionButton(
                            text = if (state.isLoading) "正在转写与检测" else "上传、转写并检测",
                            onClick = onUploadAndAnalyzeClick,
                            enabled = state.canUploadAndAnalyze,
                        )
                    }

                    state.statusMessage?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (state.serverTranscript.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "转写文本校对",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TextButton(
                                onClick = {
                                    val pastedText = clipboardManager
                                        .getText()
                                        ?.text
                                        .orEmpty()
                                    if (pastedText.isNotBlank()) {
                                        onTranscriptChange(pastedText)
                                    }
                                },
                                enabled = !state.isLoading,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = null,
                                )
                                Text("粘贴")
                            }
                        }

                        OutlinedTextField(
                            value = state.transcript,
                            onValueChange = onTranscriptChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("FunASR 转写内容") },
                            supportingText = {
                                Text("${state.transcript.length}/10000；发现错字可直接修改")
                            },
                            minLines = 5,
                            maxLines = 12,
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(16.dp),
                        )

                        if (state.hasTranscriptCorrection) {
                            PrimaryActionButton(
                                text = "使用修正文字重新检测",
                                onClick = onReanalyzeCorrectionClick,
                                enabled = state.canReanalyzeCorrection,
                            )
                        }
                    }

                    if (state.hasRecording || state.result != null || state.transcript.isNotBlank()) {
                        SecondaryActionButton(
                            text = "清空本次语音检测",
                            onClick = {
                                recorder.cancel()
                                onClearAllClick()
                            },
                            enabled = !state.isLoading && !isImportingAudio,
                        )
                    }
                }
            }
        }

        item { PrivacyNoticeCard() }

        if (state.isLoading) {
            item {
                AnalysisProgressCard(
                    steps = listOf(
                        "正在上传并校验音频",
                        "本地 FunASR 正在转写语音",
                        "正在匹配规则并进行语义复核",
                        "正在生成安全建议",
                    ),
                )
            }
        }

        state.errorMessage?.let { message ->
            item { DetectionErrorCard(message) }
        }

        state.result?.let { result ->
            item {
                UnifiedRiskResultContent(
                    result = result.toUnifiedUi(state.transcript),
                    onImmediateHelpClick = onImmediateHelpClick,
                    onViewDetailClick = onViewDetailClick,
                    onClearResultClick = onClearResultClick,
                )
            }
        }
    }
}

private fun VoiceRiskResponse.toUnifiedUi(
    displayedTranscript: String,
): UnifiedRiskResultUi {
    return UnifiedRiskResultUi(
        eventId = eventId,
        riskLevel = riskLevel,
        score = score,
        conclusion = buildRiskConclusion(riskLevel, evidence.firstOrNull()?.title),
        immediateAction = buildImmediateAction(riskLevel, actions.firstOrNull()),
        actions = actions,
        evidence = evidence.map {
            UnifiedRiskEvidenceUi(
                title = it.title,
                explanation = it.explanation,
                score = it.score,
                ruleCode = it.ruleId,
            )
        },
        aiExplanation = aiSummary,
        originalContentTitle = "语音转写内容",
        originalContent = displayedTranscript.ifBlank { transcript },
        disclaimer = disclaimer,
        createdAt = createdAt,
        basicTechnicalFields = listOf(
            "分析引擎" to riskAnalysisEngineName(analysisMode),
            "转写模型" to (transcriptionModel ?: "本地 FunASR"),
            "规则版本" to ruleVersion,
        ),
        advancedTechnicalFields = buildList {
            add("识别服务" to recognitionProvider)
            recognitionConfidence?.let {
                add("识别置信度" to formatAiConfidence(it))
            }
            languageProbability?.let {
                add("语言置信度" to formatAiConfidence(it))
            }
            durationSeconds?.let {
                add("音频时长" to "%.1f 秒".format(it))
            }
            add("引擎版本" to engineVersion)
            ruleScore?.let {
                add("规则评分" to formatRiskScore(it))
            }
            aiScore?.let {
                add("AI 语义评分" to formatRiskScore(it))
            }
            aiRiskLevel?.let {
                add("AI 语义等级" to riskLevelDisplayName(it))
            }
            if (fusionApplied) {
                add("最终融合评分" to formatRiskScore(score))
            }
            fusionReason?.let {
                add("评分融合说明" to it)
            }
            aiModel?.let { add("AI 模型" to it) }
            aiConfidence?.let {
                add("AI 置信度" to formatAiConfidence(it))
            }
            val ruleIds = evidence
                .map { it.ruleId }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("、")
            if (ruleIds.isNotBlank()) {
                add("命中规则" to ruleIds)
            }
        },
    )
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "大小未知"
    val kilobytes = bytes / 1024.0
    return if (kilobytes < 1024.0) {
        "%.1f KB".format(kilobytes)
    } else {
        "%.2f MB".format(kilobytes / 1024.0)
    }
}
