package com.trueshield.app.ui.risk.report

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.data.ExperienceTopBar
import com.trueshield.app.ui.data.GradientSummaryCard
import com.trueshield.app.ui.data.InlineMessageCard
import com.trueshield.app.ui.data.SummaryMetric
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.trueshield.app.data.model.risk.report.RiskReportSummaryResponse
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun RiskReportScreen(
    state: RiskReportUiState,
    onSelectPersonal: () -> Unit,
    onSelectFamily: (String) -> Unit,
    onSelectPeriodDays: (Int) -> Unit,
    onRefreshClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    var localMessage by remember {
        mutableStateOf<String?>(null)
    }

    val saveLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .CreateDocument("application/pdf"),
        ) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            val file =
                state.pdfFilePath
                    ?.let(::File)

            localMessage =
                runCatching {
                    require(
                        file != null &&
                            file.exists(),
                    )

                    context.contentResolver
                        .openOutputStream(uri)
                        ?.use { output ->
                            file.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        ?: error("无法打开目标文件。")

                    "PDF 已保存到你选择的位置。"
                }.getOrElse {
                    "保存 PDF 失败，请重试。"
                }
        }

    TrueShieldPageBackground {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
            }
            item {
                ExperienceTopBar(
                    title = "风险报告与导出",
                    subtitle = "生成个人或家庭风险摘要，并导出脱敏 PDF",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshEnabled = !state.isBusy,
                )
            }

        item {
            ReportCard(title = "报告范围") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                ) {
                    ScopeButton(
                        text = "个人",
                        selected =
                            state.scope ==
                                RiskReportScopeSelection.PERSONAL,
                        enabled = !state.isBusy,
                        onClick = onSelectPersonal,
                        modifier = Modifier.weight(1f),
                    )

                    ScopeButton(
                        text = "家庭",
                        selected =
                            state.scope ==
                                RiskReportScopeSelection.FAMILY,
                        enabled =
                            !state.isBusy &&
                                state.families.isNotEmpty(),
                        onClick = {
                            state.selectedFamilyId
                                ?.let(onSelectFamily)
                                ?: state.families
                                    .firstOrNull()
                                    ?.id
                                    ?.let(onSelectFamily)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (state.families.isNotEmpty()) {
                    Spacer(
                        modifier = Modifier.height(12.dp),
                    )
                    Text(
                        text = "选择家庭",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(
                        modifier = Modifier.height(8.dp),
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState(),
                                ),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),
                    ) {
                        state.families.forEach { family ->
                            ScopeButton(
                                text = family.name,
                                selected =
                                    state.scope ==
                                        RiskReportScopeSelection.FAMILY &&
                                        state.selectedFamilyId == family.id,
                                enabled = !state.isBusy,
                                onClick = {
                                    onSelectFamily(family.id)
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            ReportCard(title = "统计周期") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    listOf(7, 30, 90).forEach { days ->
                        ScopeButton(
                            text = "近 $days 天",
                            selected =
                                state.selectedPeriodDays == days,
                            enabled = !state.isBusy,
                            onClick = {
                                onSelectPeriodDays(days)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        state.familyMessage?.let { message ->
            item {
                MessageCard(
                    message = message,
                    isError = false,
                )
            }
        }

        state.errorMessage?.let { message ->
            item {
                MessageCard(
                    message = message,
                    isError = true,
                )
            }
        }

        localMessage?.let { message ->
            item {
                MessageCard(
                    message = message,
                    isError = false,
                )
            }
        }

        state.exportMessage?.let { message ->
            item {
                MessageCard(
                    message = message,
                    isError = false,
                )
            }
        }

        if (
            state.isLoading &&
            state.report == null
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(
                        modifier = Modifier.height(12.dp),
                    )
                    Text("正在生成报告预览……")
                }
            }
        }

        state.report?.let { report ->
            item {
                OverviewCard(report)
            }

            item {
                DistributionCard(report)
            }

            if (report.topSignals.isNotEmpty()) {
                item {
                    ReportCard(title = "主要风险证据") {
                        report.topSignals.forEachIndexed {
                                index,
                                signal,
                            ->
                            Text(
                                text =
                                    "${index + 1}. ${signal.title}（${signal.count} 次）",
                            )
                            if (
                                index !=
                                report.topSignals.lastIndex
                            ) {
                                Spacer(
                                    modifier = Modifier.height(7.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (report.recentEvents.isNotEmpty()) {
                item {
                    ReportCard(title = "重点风险事件") {
                        report.recentEvents
                            .take(6)
                            .forEachIndexed {
                                    index,
                                    event,
                                ->
                                Text(
                                    text =
                                        "${riskLabel(event.riskLevel)} · " +
                                            "${event.riskScore}/100 · " +
                                            sourceLabel(event.sourceType),
                                    fontWeight =
                                        FontWeight.SemiBold,
                                )
                                Text(
                                    text = event.summary,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium,
                                )
                                event.sourceExcerpt
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { excerpt ->
                                        Text(
                                            text = "脱敏内容：$excerpt",
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall,
                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .onSurfaceVariant,
                                            maxLines = 3,
                                        )
                                    }
                                Text(
                                    text =
                                        formatDateTime(event.createdAt),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall,
                                )
                                if (
                                    index !=
                                    report.recentEvents
                                        .take(6)
                                        .lastIndex
                                ) {
                                    Spacer(
                                        modifier = Modifier.height(12.dp),
                                    )
                                }
                            }
                    }
                }
            }

            if (report.recentAlerts.isNotEmpty()) {
                item {
                    ReportCard(title = "家庭告警处置") {
                        report.recentAlerts
                            .take(5)
                            .forEachIndexed {
                                    index,
                                    alert,
                                ->
                                Text(
                                    text =
                                        "${alertStatusLabel(alert.status)} · " +
                                            riskLabel(alert.riskLevel),
                                    fontWeight =
                                        FontWeight.SemiBold,
                                )
                                Text(alert.title)
                                Text(
                                    text = alert.summary,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium,
                                )
                                if (
                                    index !=
                                    report.recentAlerts
                                        .take(5)
                                        .lastIndex
                                ) {
                                    Spacer(
                                        modifier = Modifier.height(12.dp),
                                    )
                                }
                            }
                    }
                }
            }

            item {
                ReportCard(title = "隐私与风险说明") {
                    Text(report.privacyNotice)
                    Spacer(
                        modifier = Modifier.height(10.dp),
                    )
                    Text(report.disclaimer)
                }
            }

            item {
                ReportCard(title = "导出 PDF") {
                    Button(
                        onClick = onExportPdfClick,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier
                                        .width(20.dp)
                                        .height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("生成脱敏 PDF")
                        }
                    }

                    val file =
                        state.pdfFilePath
                            ?.let(::File)
                            ?.takeIf {
                                it.exists()
                            }

                    if (file != null) {
                        Spacer(
                            modifier = Modifier.height(10.dp),
                        )

                        OutlinedButton(
                            onClick = {
                                saveLauncher.launch(
                                    state.pdfDisplayName
                                        ?: "true-shield-risk-report.pdf",
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("保存 PDF 到设备")
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val uri =
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file,
                                        )

                                    val shareIntent =
                                        Intent(
                                            Intent.ACTION_SEND,
                                        ).apply {
                                            type = "application/pdf"
                                            putExtra(
                                                Intent.EXTRA_STREAM,
                                                uri,
                                            )
                                            addFlags(
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                            )
                                        }

                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            "分享真信盾风险报告",
                                        ),
                                    )
                                }.onFailure {
                                    localMessage =
                                        "分享 PDF 失败，请重试。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("分享 PDF")
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onRefreshClick,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("刷新报告预览")
            }
        }

            item {
                Spacer(
                    modifier = Modifier.height(20.dp),
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(
    report: RiskReportSummaryResponse,
) {
    val overview = report.overview
    val subjectName =
        report.subjectName
            .trim()
            .takeUnless {
                it.isBlank() ||
                    it.equals("string", ignoreCase = true)
            }
            ?: "未设置昵称"

    GradientSummaryCard(
        title = report.reportTitle,
        subtitle = "统计对象：$subjectName · ${report.periodStart} 至 ${report.periodEnd}",
        icon = Icons.Default.Assessment,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryMetric(
                value = overview.totalEvents.toString(),
                label = "风险事件",
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                value = String.format("%.1f", overview.averageScore),
                label = "平均风险分",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryMetric(
                value = overview.highRiskEvents.toString(),
                label = "高风险",
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                value = "${(overview.resolutionRate * 100).roundToInt()}%",
                label = "告警完成率",
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "报告编号：${report.reportCode}",
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.84f),
        )
    }
}

@Composable
private fun DistributionCard(
    report: RiskReportSummaryResponse,
) {
    ReportCard(title = "风险分布") {
        StatLine("高风险", report.riskLevels.high)
        StatLine("中风险", report.riskLevels.medium)
        StatLine("低风险", report.riskLevels.low)
        Spacer(
            modifier = Modifier.height(12.dp),
        )
        Text(
            text = "检测来源",
            fontWeight = FontWeight.SemiBold,
        )
        StatLine("文本", report.sourceTypes.text)
        StatLine("图片", report.sourceTypes.image)
        StatLine("链接", report.sourceTypes.url)
        StatLine("语音", report.sourceTypes.voice)
        StatLine("通话号码", report.sourceTypes.call)
    }
}

@Composable
private fun StatRow(
    leftTitle: String,
    leftValue: String,
    rightTitle: String,
    rightValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        StatBlock(
            title = leftTitle,
            value = leftValue,
            modifier = Modifier.weight(1f),
        )
        StatBlock(
            title = rightTitle,
            value = rightValue,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatBlock(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(title)
    }
}

@Composable
private fun StatLine(
    title: String,
    value: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Text(
            text = "$value 条",
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReportCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    isError: Boolean,
) {
    InlineMessageCard(
        message = message,
        isError = isError,
    )
}

@Composable
private fun ScopeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(text)
        }
    }
}

private fun riskLabel(
    value: String,
): String =
    when (value.lowercase()) {
        "high" -> "高风险"
        "medium" -> "中风险"
        "low" -> "低风险"
        else -> value
    }

private fun sourceLabel(
    value: String,
): String =
    when (value.lowercase()) {
        "text" -> "文本"
        "image" -> "图片"
        "url" -> "链接"
        "voice" -> "语音"
        "call" -> "通话号码"
        else -> value
    }

private fun alertStatusLabel(
    value: String,
): String =
    when (value.lowercase()) {
        "pending" -> "待确认"
        "acknowledged" -> "处理中"
        "resolved" -> "已完成"
        "cancelled" -> "已取消"
        else -> value
    }

private fun formatDateTime(
    value: String,
): String {
    return runCatching {
        OffsetDateTime
            .parse(value)
            .format(
                DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm"),
            )
    }.getOrDefault(value)
}
