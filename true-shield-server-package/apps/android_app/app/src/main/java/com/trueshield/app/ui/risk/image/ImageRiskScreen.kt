package com.trueshield.app.ui.risk.image

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.risk.ImageRiskResponse
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
import com.trueshield.app.ui.theme.TrueShieldCyan
import com.trueshield.app.ui.theme.TrueShieldCyanLight
import kotlin.math.roundToInt

/**
 * 新版图片 OCR 与风险检测页面。
 */
@Composable
fun ImageRiskScreen(
    state: ImageRiskUiState,
    onImageSelected: (Uri?) -> Unit,
    onAnalyzeClick: () -> Unit,
    onClearResultClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onImmediateHelpClick: (String) -> Unit,
    onViewDetailClick: (String) -> Unit,
    onFeedbackClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    FirstUseGuideDialog(
        guideKey = "image_risk_guide_v1",
        title = "图片检测怎么用？",
        description = "适合聊天截图、通知、海报和二维码页面截图。",
        steps = listOf(
            "选择清晰且包含完整文字的图片",
            "等待本地 OCR 提取文字并进行风险分析",
            "结果不准确时可提交 OCR 或检测反馈",
        ),
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        onImageSelected(uri)
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    RiskDetectionPage(
        title = "图片检测",
        subtitle = "识别聊天截图、通知和二维码中的风险内容",
        onBackClick = onBackClick,
    ) {
        item {
            DetectionIntro(
                title = "上传可疑截图",
                description = "系统先进行 OCR 文字识别，再结合规则与本地 AI 分析诈骗风险。",
                icon = Icons.Outlined.ImageSearch,
                iconColor = TrueShieldCyan,
                iconBackground = TrueShieldCyanLight,
            )
        }

        item {
            TrueShieldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.hasSelectedImage) {
                        state.previewBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "已选择图片预览",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 320.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.fileName ?: "已选择图片",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${formatFileSize(state.fileSizeBytes)} · ${state.contentType ?: "图片"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "选择包含聊天记录或转账提示的清晰截图",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "支持 JPEG、PNG、WEBP、BMP，最大 10 MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    PrimaryActionButton(
                        text = when {
                            state.isReadingImage -> "正在读取图片"
                            state.hasSelectedImage -> "重新选择图片"
                            else -> "选择聊天截图"
                        },
                        onClick = {
                            imagePickerLauncher.launch(
                                arrayOf(
                                    "image/jpeg",
                                    "image/png",
                                    "image/webp",
                                    "image/bmp",
                                ),
                            )
                        },
                        enabled = !state.isReadingImage && !state.isLoading,
                    )

                    if (state.hasSelectedImage) {
                        PrimaryActionButton(
                            text = if (state.isLoading) "正在识别与检测" else "开始图片检测",
                            onClick = onAnalyzeClick,
                            enabled = state.canAnalyze,
                        )
                        SecondaryActionButton(
                            text = "清除所选图片",
                            onClick = onClearAllClick,
                            enabled = !state.isReadingImage && !state.isLoading,
                        )
                    }
                }
            }
        }

        item { PrivacyNoticeCard() }

        if (state.isReadingImage) {
            item {
                AnalysisProgressCard(
                    title = "正在读取图片",
                    steps = listOf(
                        "正在校验图片格式和大小",
                        "正在生成安全预览",
                    ),
                )
            }
        }

        if (state.isLoading) {
            item {
                AnalysisProgressCard(
                    steps = listOf(
                        "正在进行 OCR 文字识别",
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UnifiedRiskResultContent(
                        result = result.toUnifiedUi(),
                        onImmediateHelpClick = onImmediateHelpClick,
                        onViewDetailClick = onViewDetailClick,
                        onClearResultClick = onClearResultClick,
                    )
                    SecondaryActionButton(
                        text = "反馈 OCR 或检测结果",
                        onClick = { onFeedbackClick(result.eventId) },
                    )
                }
            }
        }
    }
}

private fun ImageRiskResponse.toUnifiedUi(): UnifiedRiskResultUi {
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
        originalContentTitle = "OCR 识别内容",
        originalContent = extractedText,
        disclaimer = disclaimer,
        createdAt = createdAt,
        basicTechnicalFields = listOf(
            "分析引擎" to riskAnalysisEngineName(analysisMode),
            "OCR 行数" to ocrQuality.lineCount.toString(),
            "规则版本" to ruleVersion,
        ),
        advancedTechnicalFields = buildList {
            add("图片尺寸" to "${imageWidth} × ${imageHeight}")
            add(
                "OCR 平均置信度" to
                    "${(ocrQuality.averageConfidence * 100).roundToInt()}%",
            )
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

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "大小未知"
    val kilobytes = bytes / 1024.0
    return if (kilobytes < 1024.0) {
        "%.1f KB".format(kilobytes)
    } else {
        "%.2f MB".format(kilobytes / 1024.0)
    }
}
