package com.trueshield.app.ui.risk.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.risk.TextRiskResponse
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
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight

/**
 * 新版文本风险检测页面。
 */
@Composable
fun TextRiskScreen(
    state: TextRiskUiState,
    onTextChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onClearResultClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onImmediateHelpClick: (String) -> Unit,
    onViewDetailClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    FirstUseGuideDialog(
        guideKey = "text_risk_guide_v1",
        title = "文本检测怎么用？",
        description = "粘贴完整聊天或短信，保留身份、转账和时间催促等上下文。",
        steps = listOf(
            "点击“粘贴”读取剪贴板内容",
            "点击“开始文本检测”并等待规则与 AI 复核",
            "高风险时先停止操作，再联系家人核验",
        ),
    )

    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    RiskDetectionPage(
        title = "文本检测",
        subtitle = "粘贴聊天、短信或通知，识别常见诈骗话术",
        onBackClick = onBackClick,
    ) {
        item {
            DetectionIntro(
                title = "检测可疑文字",
                description = "重点识别冒充身份、诱导转账、验证码索取和紧急催促。",
                icon = Icons.Outlined.TextSnippet,
                iconColor = TrueShieldBlue,
                iconBackground = TrueShieldBlueLight,
            )
        }

        item {
            TrueShieldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "聊天或短信内容",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                val pastedText = clipboardManager
                                    .getText()
                                    ?.text
                                    .orEmpty()
                                if (pastedText.isNotBlank()) {
                                    onTextChange(pastedText)
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
                        value = state.text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 210.dp),
                        placeholder = {
                            Text(
                                "例如：我是公安局工作人员，你涉嫌洗钱，请立即将钱转入安全账户。",
                            )
                        },
                        supportingText = {
                            Text("${state.text.length}/$TEXT_RISK_MAX_LENGTH")
                        },
                        minLines = 8,
                        maxLines = 14,
                        enabled = !state.isLoading,
                        shape = RoundedCornerShape(16.dp),
                    )

                    PrimaryActionButton(
                        text = if (state.isLoading) "正在检测" else "开始文本检测",
                        onClick = onAnalyzeClick,
                        enabled = state.canSubmit,
                    )

                    if (state.text.isNotBlank() || state.result != null) {
                        SecondaryActionButton(
                            text = "清空内容",
                            onClick = onClearAllClick,
                            enabled = !state.isLoading,
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
                        "正在匹配可解释风险规则",
                        "正在进行语义风险复核",
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
                    result = result.toUnifiedUi(state.text),
                    onImmediateHelpClick = onImmediateHelpClick,
                    onViewDetailClick = onViewDetailClick,
                    onClearResultClick = onClearResultClick,
                )
            }
        }
    }
}

private fun TextRiskResponse.toUnifiedUi(
    originalText: String,
): UnifiedRiskResultUi {
    val firstEvidenceTitle = evidence.firstOrNull()?.title

    return UnifiedRiskResultUi(
        eventId = eventId,
        riskLevel = riskLevel,
        score = score,
        conclusion = buildRiskConclusion(riskLevel, firstEvidenceTitle),
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
        originalContentTitle = "原始检测内容",
        originalContent = originalText,
        disclaimer = disclaimer,
        createdAt = createdAt,
        basicTechnicalFields = listOf(
            "分析引擎" to riskAnalysisEngineName(analysisMode),
            "规则版本" to ruleVersion,
        ),
        advancedTechnicalFields = buildList {
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
