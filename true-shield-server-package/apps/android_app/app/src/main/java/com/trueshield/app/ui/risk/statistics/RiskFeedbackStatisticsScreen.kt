package com.trueshield.app.ui.risk.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.risk.feedback.RiskFeedbackStatisticsResponse
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.data.EmptyStateCard
import com.trueshield.app.ui.data.ExperienceTopBar
import com.trueshield.app.ui.data.GradientSummaryCard
import com.trueshield.app.ui.data.InlineMessageCard
import com.trueshield.app.ui.data.LoadingStateCard
import com.trueshield.app.ui.data.SummaryMetric
import kotlin.math.roundToInt

@Composable
fun RiskFeedbackStatisticsScreen(
    state: RiskFeedbackStatisticsUiState,
    onRefreshClick: () -> Unit,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    TrueShieldPageBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(18.dp)) }
            item {
                ExperienceTopBar(
                    title = "反馈统计",
                    subtitle = "查看检测纠错、准确反馈和模型改进数据",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshEnabled = !state.isBusy,
                )
            }

            if (state.isLoading && state.statistics == null) {
                item { LoadingStateCard("正在读取反馈统计……") }
            }

            state.errorMessage?.let { message ->
                item { InlineMessageCard(message, isError = true) }
                if (state.statistics == null) {
                    item {
                        PrimaryActionButton(
                            text = "重新加载",
                            onClick = onRetryClick,
                            enabled = !state.isBusy,
                        )
                    }
                }
            }

            state.statistics?.let { statistics ->
                if (statistics.totalFeedbacks <= 0) {
                    item {
                        EmptyStateCard(
                            title = "暂无反馈数据",
                            description = "在风险结果页提交“判断准确”或纠错反馈后，统计会显示在这里。",
                            actionText = "刷新统计",
                            onActionClick = onRefreshClick,
                        )
                    }
                } else {
                    item { FeedbackSummaryCard(statistics) }
                    item { AccuracyCard(statistics) }
                    item { FeedbackTypeCard(statistics) }
                    item { ExpectedRiskLevelCard(statistics) }
                    item {
                        TrueShieldCard {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "数据说明",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "准确率表示用户确认系统判断准确的反馈占比；纠错率表示误报、漏报或风险等级偏差反馈的占比。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun FeedbackSummaryCard(
    statistics: RiskFeedbackStatisticsResponse,
) {
    GradientSummaryCard(
        title = "反馈质量概览",
        subtitle = "每次反馈都会帮助真信盾持续优化风险识别",
        icon = Icons.Default.FactCheck,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryMetric(
                value = statistics.totalFeedbacks.toString(),
                label = "反馈总数",
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                value = percentText(statistics.accurateRate),
                label = "判断准确率",
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                value = percentText(statistics.correctionRate),
                label = "纠错反馈率",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AccuracyCard(
    statistics: RiskFeedbackStatisticsResponse,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "反馈质量趋势",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            PercentageRow(
                label = "判断准确",
                value = statistics.accurateRate,
            )
            PercentageRow(
                label = "需要纠错",
                value = statistics.correctionRate,
            )
        }
    }
}

@Composable
private fun FeedbackTypeCard(
    statistics: RiskFeedbackStatisticsResponse,
) {
    val counts = statistics.typeCounts
    val total = statistics.totalFeedbacks.coerceAtLeast(1)
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text(
                text = "反馈类型分布",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            CountProgressRow("判断准确", counts.accurate, total)
            CountProgressRow("系统误报", counts.falsePositive, total)
            CountProgressRow("系统漏报", counts.falseNegative, total)
            CountProgressRow("风险等级偏高", counts.riskTooHigh, total)
            CountProgressRow("风险等级偏低", counts.riskTooLow, total)
        }
    }
}

@Composable
private fun ExpectedRiskLevelCard(
    statistics: RiskFeedbackStatisticsResponse,
) {
    val levels = statistics.expectedRiskLevelCounts
    val total = (levels.low + levels.medium + levels.high).coerceAtLeast(1)
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text(
                text = "用户期望风险等级",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "仅统计用户在纠错时明确选择的风险等级。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CountProgressRow("高风险", levels.high, total)
            CountProgressRow("中风险", levels.medium, total)
            CountProgressRow("低风险", levels.low, total)
        }
    }
}

@Composable
private fun PercentageRow(
    label: String,
    value: Double,
) {
    val normalized = normalizeRate(value)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label)
            Text(
                text = "${(normalized * 100).roundToInt()}%",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            progress = { normalized.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp),
        )
    }
}

@Composable
private fun CountProgressRow(
    label: String,
    count: Int,
    total: Int,
) {
    val fraction = count.toFloat() / total.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "$count 条",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun normalizeRate(value: Double): Double =
    if (value > 1.0) {
        (value / 100.0).coerceIn(0.0, 1.0)
    } else {
        value.coerceIn(0.0, 1.0)
    }

private fun percentText(value: Double): String =
    "${(normalizeRate(value) * 100).roundToInt()}%"
