package com.trueshield.app.ui.risk.url

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.risk.url.UrlRiskResponse
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
import com.trueshield.app.ui.onboarding.FirstUseGuideDialog
import com.trueshield.app.ui.theme.RiskMedium
import com.trueshield.app.ui.theme.RiskMediumContainer

/**
 * 新版链接风险检测页面。
 */
@Composable
fun UrlRiskScreen(
    state: UrlRiskUiState,
    onUrlChange: (String) -> Unit,
    onResolveRedirectsChange: (Boolean) -> Unit,
    onAnalyzeClick: () -> Unit,
    onClearResultClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onImmediateHelpClick: (String) -> Unit,
    onViewDetailClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    FirstUseGuideDialog(
        guideKey = "url_risk_guide_v1",
        title = "链接检测怎么用？",
        description = "不要直接打开陌生链接，先复制网址交给真信盾检查。",
        steps = listOf(
            "点击“粘贴”读取完整网址",
            "建议保留安全解析重定向，检查最终落地地址",
            "发现高风险时不要登录、付款或下载文件",
        ),
    )

    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    RiskDetectionPage(
        title = "链接检测",
        subtitle = "检查网址、短链接和跳转页面是否存在风险",
        onBackClick = onBackClick,
    ) {
        item {
            DetectionIntro(
                title = "检查可疑链接",
                description = "识别非 HTTPS、IP 地址、凭据嵌入、异常端口和诱导性路径。",
                icon = Icons.Outlined.Link,
                iconColor = RiskMedium,
                iconBackground = RiskMediumContainer,
            )
        }

        item {
            TrueShieldCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "网址或链接",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                val pastedUrl = clipboardManager
                                    .getText()
                                    ?.text
                                    .orEmpty()
                                if (pastedUrl.isNotBlank()) {
                                    onUrlChange(pastedUrl)
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
                        value = state.url,
                        onValueChange = onUrlChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 118.dp),
                        placeholder = {
                            Text("例如：https://example.com/login")
                        },
                        supportingText = {
                            Text("${state.url.length}/$URL_RISK_MAX_LENGTH")
                        },
                        minLines = 3,
                        maxLines = 6,
                        enabled = !state.isLoading,
                        shape = RoundedCornerShape(16.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "安全解析重定向",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "由后端受控访问链接，检查最终落地地址。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.resolveRedirects,
                            onCheckedChange = onResolveRedirectsChange,
                            enabled = !state.isLoading,
                        )
                    }

                    if (state.resolveRedirects) {
                        Text(
                            text = "后端会限制跳转次数、阻止内网地址并进行循环保护，不会在手机浏览器中直接打开该链接。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    PrimaryActionButton(
                        text = if (state.isLoading) "正在检查链接" else "开始链接检测",
                        onClick = onAnalyzeClick,
                        enabled = state.canSubmit,
                    )

                    if (state.url.isNotBlank() || state.result != null) {
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
                    steps = if (state.resolveRedirects) {
                        listOf(
                            "正在规范化网址并匹配风险规则",
                            "正在安全解析跳转链",
                            "正在分析最终地址并生成建议",
                        )
                    } else {
                        listOf(
                            "正在规范化网址结构",
                            "正在匹配链接风险规则",
                            "正在生成安全建议",
                        )
                    },
                )
            }
        }

        state.errorMessage?.let { message ->
            item { DetectionErrorCard(message) }
        }

        state.result?.let { result ->
            item {
                UnifiedRiskResultContent(
                    result = result.toUnifiedUi(),
                    onImmediateHelpClick = onImmediateHelpClick,
                    onViewDetailClick = onViewDetailClick,
                    onClearResultClick = onClearResultClick,
                )
            }
        }
    }
}

private fun UrlRiskResponse.toUnifiedUi(): UnifiedRiskResultUi {
    val redirectResolution = redirectInspection.resolution
    val redirectDescription = when (redirectInspection.status.lowercase()) {
        "completed" -> {
            val finalUrl = redirectResolution?.finalUrl ?: normalizedUrl
            "重定向检查已完成，最终地址：$finalUrl"
        }
        "blocked" -> redirectInspection.message ?: "重定向检查已被安全策略阻止。"
        "failed" -> redirectInspection.message ?: "重定向检查未能完成。"
        else -> null
    }

    return UnifiedRiskResultUi(
        eventId = eventId,
        riskLevel = riskLevel,
        score = score,
        conclusion = buildRiskConclusion(riskLevel, summary),
        immediateAction = buildImmediateAction(riskLevel, actions.firstOrNull()),
        actions = actions,
        evidence = buildList {
            addAll(
                signals.map {
                    UnifiedRiskEvidenceUi(
                        title = it.title,
                        explanation = it.explanation,
                        score = it.score,
                        ruleCode = it.signalId,
                    )
                },
            )
            redirectDescription?.let { description ->
                add(
                    UnifiedRiskEvidenceUi(
                        title = "重定向检查",
                        explanation = description,
                    ),
                )
            }
        },
        aiExplanation = null,
        originalContentTitle = "原始检测链接",
        originalContent = originalUrl,
        disclaimer = disclaimer,
        createdAt = createdAt,
        basicTechnicalFields = listOf(
            "目标主机" to host,
            "规则版本" to ruleVersion,
            "跳转检查" to redirectStatusName(redirectInspection.status),
        ),
        advancedTechnicalFields = buildList {
            add("规范化链接" to normalizedUrl)
            redirectResolution?.let { resolution ->
                add("最终链接" to resolution.finalUrl)
                add("跳转次数" to resolution.redirectCount.toString())
                add("最终状态码" to resolution.finalStatusCode.toString())
            }
            val signalIds = signals
                .map { it.signalId }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("、")
            if (signalIds.isNotBlank()) {
                add("命中规则" to signalIds)
            }
        },
    )
}

private fun redirectStatusName(status: String): String {
    return when (status.lowercase()) {
        "completed" -> "已完成"
        "blocked" -> "已阻止"
        "failed" -> "检查失败"
        "not_requested" -> "未启用"
        else -> status
    }
}
