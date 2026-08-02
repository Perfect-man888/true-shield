package com.trueshield.app.ui.risk.common

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.local.UiPreferencesStore
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.RiskLevelBadge
import com.trueshield.app.ui.components.SecondaryActionButton
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.RiskMedium
import com.trueshield.app.ui.theme.RiskMediumContainer
import java.time.Instant
import java.util.Locale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * 四类风险检测页面共用的内容模型。
 */
data class UnifiedRiskResultUi(
    val eventId: String,
    val riskLevel: String,
    val score: Int,
    val conclusion: String,
    val immediateAction: String,
    val actions: List<String>,
    val evidence: List<UnifiedRiskEvidenceUi>,
    val aiExplanation: String? = null,
    val originalContentTitle: String,
    val originalContent: String,
    val disclaimer: String,
    val createdAt: String,
    val basicTechnicalFields: List<Pair<String, String>>,
    val advancedTechnicalFields: List<Pair<String, String>>,
)

data class UnifiedRiskEvidenceUi(
    val title: String,
    val explanation: String,
    val score: Int? = null,
    val ruleCode: String? = null,
)

/**
 * 统一的检测页面框架。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskDetectionPage(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    TrueShieldPageBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 12.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
fun DetectionIntro(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = iconBackground,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PrivacyNoticeCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }

    TrueShieldCard(
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "隐私保护",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "内容仅用于本次风险分析，敏感信息会按功能需要脱敏处理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = "检测结果会保存在你的真信盾账户中，便于风险历史、家庭沟通和反馈闭环。风险报告导出时不会包含原始录音、原始图片、完整号码或系统密钥。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AnalysisProgressCard(
    title: String = "正在分析",
    steps: List<String>,
) {
    var activeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(steps) {
        activeIndex = 0
        while (true) {
            delay(900)
            if (activeIndex < steps.lastIndex) {
                activeIndex += 1
            }
        }
    }

    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            steps.forEachIndexed { index, step ->
                val completed = index < activeIndex
                val active = index == activeIndex

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = when {
                            completed -> Icons.Outlined.CheckCircle
                            active -> Icons.Outlined.AutoAwesome
                            else -> Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = when {
                            completed -> RiskLow
                            active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active || completed) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DetectionErrorCard(
    message: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.ReportProblem,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Column {
                Text(
                    text = "检测未完成",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/**
 * 文本、图片、链接、语音共用的结果展示。
 */
@Composable
fun UnifiedRiskResultContent(
    result: UnifiedRiskResultUi,
    onImmediateHelpClick: (String) -> Unit,
    onViewDetailClick: (String) -> Unit,
    onClearResultClick: () -> Unit,
) {
    HighRiskVoiceAnnouncement(result)

    var showHelpDialog by rememberSaveable { mutableStateOf(false) }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.FamilyRestroom,
                    contentDescription = null,
                )
            },
            title = { Text("进入家庭求助确认") },
            text = {
                Text(
                    "下一页会读取你的家庭列表，并允许选择默认家庭后创建告警。此步骤不会在未经确认的情况下自动发送。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHelpDialog = false
                        onImmediateHelpClick(result.eventId)
                    },
                ) {
                    Text("继续求助")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RiskConclusionCard(result)

        ActionAdviceCard(
            actions = result.actions.ifEmpty {
                listOf(result.immediateAction)
            },
        )

        if (result.riskLevel.equals("high", ignoreCase = true)) {
            FamilyHelpCard(
                onClick = { showHelpDialog = true },
            )
        } else {
            InformationCard(
                title = "家庭核实建议",
                icon = Icons.Outlined.FamilyRestroom,
                text = "可将检测结论发送给可信家人共同核实；只有高风险事件会提供一键创建家庭告警。",
            )
        }

        EvidenceSection(result.evidence)

        result.aiExplanation
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { explanation ->
                InformationCard(
                    title = "AI 语义说明",
                    icon = Icons.Outlined.AutoAwesome,
                    text = explanation,
                )
            }

        ExpandableContentCard(
            title = result.originalContentTitle,
            text = result.originalContent.ifBlank { "没有可展示的原始内容。" },
        )

        InformationCard(
            title = "风险与隐私说明",
            icon = Icons.Outlined.Info,
            text = result.disclaimer,
        )

        TechnicalDetailsCard(result)

        PrimaryActionButton(
            text = "查看完整事件详情",
            onClick = { onViewDetailClick(result.eventId) },
        )

        SecondaryActionButton(
            text = "关闭本次检测结果",
            onClick = onClearResultClick,
        )
    }
}

@Composable
private fun HighRiskVoiceAnnouncement(
    result: UnifiedRiskResultUi,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        UiPreferencesStore(context.applicationContext)
    }
    val shouldSpeak =
        result.riskLevel.equals("high", ignoreCase = true) &&
            preferences.isElderModeEnabled() &&
            preferences.isHighRiskVoiceAlertEnabled()

    DisposableEffect(result.eventId, shouldSpeak) {
        var engine: TextToSpeech? = null

        if (shouldSpeak) {
            engine = TextToSpeech(
                context.applicationContext,
            ) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    engine?.language = Locale.SIMPLIFIED_CHINESE
                    engine?.speak(
                        "真信盾提醒：检测到高风险，请立即停止转账或提供验证码，并联系可信家人核实。",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "true_shield_high_risk_${result.eventId}",
                    )
                }
            }
        }

        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }
}

@Composable
private fun RiskConclusionCard(
    result: UnifiedRiskResultUi,
) {
    val colors = riskColors(result.riskLevel)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.second,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RiskLevelBadge(result.riskLevel)
                Text(
                    text = "${result.score} 分",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.first,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = result.conclusion,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = colors.first,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = result.immediateAction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionAdviceCard(
    actions: List<String>,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "立即措施",
                style = MaterialTheme.typography.titleMedium,
            )

            actions.distinct().take(5).forEachIndexed { index, action ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = action,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilyHelpCard(
    onClick: () -> Unit,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FamilyRestroom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "家庭求助",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "让可信家人共同核实，并在需要时创建家庭告警。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text = "立即求助",
                )
                Spacer(Modifier.size(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun EvidenceSection(
    evidence: List<UnifiedRiskEvidenceUi>,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "风险证据",
                style = MaterialTheme.typography.titleMedium,
            )

            if (evidence.isEmpty()) {
                Text(
                    text = "暂未发现明确的高危特征，仍建议通过官方渠道核实对方身份和请求。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                evidence.take(8).forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = item.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            item.score?.let { score ->
                                Text(
                                    text = "+$score",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            text = item.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InformationCard(
    title: String,
    icon: ImageVector,
    text: String,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandableContentCard(
    title: String,
    text: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    TrueShieldCard(
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(9.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = null,
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TechnicalDetailsCard(
    result: UnifiedRiskResultUi,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    TrueShieldCard(
        onClick = { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Rule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "技术信息",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (expanded) "点击收起" else "事件编号、规则版本和分析引擎",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.basicTechnicalFields.forEach { (label, value) ->
                        TechnicalField(label, value)
                    }
                    result.advancedTechnicalFields.forEach { (label, value) ->
                        TechnicalField(label, value)
                    }
                    TechnicalField("事件编号", result.eventId)
                    TechnicalField("检测时间", formatRiskDateTime(result.createdAt))
                }
            }
        }
    }
}

@Composable
private fun TechnicalField(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "暂无" },
            modifier = Modifier.weight(0.65f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

fun buildRiskConclusion(
    riskLevel: String,
    preferredTitle: String?,
): String {
    val title = preferredTitle?.trim().orEmpty()
    if (title.isNotBlank()) return title

    return when (riskLevel.lowercase()) {
        "high" -> "检测到高风险诈骗特征"
        "medium" -> "检测到需要进一步核实的风险"
        else -> "暂未发现明确高危诈骗特征"
    }
}

fun buildImmediateAction(
    riskLevel: String,
    firstAction: String?,
): String {
    val action = firstAction?.trim().orEmpty()
    if (action.isNotBlank()) return action

    return when (riskLevel.lowercase()) {
        "high" -> "请立即停止转账、共享验证码或继续按对方指引操作。"
        "medium" -> "请暂停操作，并通过官方渠道独立核实对方身份。"
        else -> "仍需核实对方身份、网址和收款账户，避免仅凭单次检测作出决定。"
    }
}

fun formatRiskDateTime(value: String): String {
    return runCatching {
        val instant = Instant.parse(value)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        formatter.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrDefault(value)
}

private fun riskColors(
    riskLevel: String,
): Pair<Color, Color> {
    return when (riskLevel.lowercase()) {
        "high" -> RiskHigh to RiskHighContainer
        "medium" -> RiskMedium to RiskMediumContainer
        else -> RiskLow to RiskLowContainer
    }
}
