package com.trueshield.app.ui.risk.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.risk.dashboard.FamilyAlertStatusDistributionDto
import com.trueshield.app.data.model.risk.dashboard.RiskDailyTrendPointDto
import com.trueshield.app.data.model.risk.dashboard.RiskDashboardResponse
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.data.ExperienceTopBar
import com.trueshield.app.ui.data.GradientSummaryCard
import com.trueshield.app.ui.data.InlineMessageCard
import com.trueshield.app.ui.data.LoadingStateCard
import com.trueshield.app.ui.data.SummaryMetric
import com.trueshield.app.ui.data.formatLocalDateTime
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskMedium
import kotlin.math.roundToInt

@Composable
fun RiskDashboardScreen(
    state: RiskDashboardUiState,
    onSelectPersonal: () -> Unit,
    onSelectFamily: (String) -> Unit,
    onSelectPeriodDays: (Int) -> Unit,
    onRefreshClick: () -> Unit,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit,
) {
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
                    title = "风险统计",
                    subtitle = "查看个人或家庭的风险分布与近期趋势",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshEnabled = !state.isBusy,
                )
            }

            item {
                DashboardFilters(
                    state = state,
                    onSelectPersonal = onSelectPersonal,
                    onSelectFamily = onSelectFamily,
                    onSelectPeriodDays = onSelectPeriodDays,
                )
            }

            state.familyMessage?.let { message ->
                item { InlineMessageCard(message, isError = false) }
            }
            state.errorMessage?.let { message ->
                item { InlineMessageCard(message, isError = true) }
            }

            if (state.isLoading && state.dashboard == null) {
                item { LoadingStateCard("正在生成风险统计……") }
            }

            state.dashboard?.let { dashboard ->
                item { DashboardSummary(dashboard) }
                item { RiskLevelDistributionCard(dashboard) }
                item { SourceDistributionCard(dashboard) }
                item {
                    DailyTrendCard(
                        points = dashboard.dailyTrend,
                        periodDays = dashboard.periodDays,
                    )
                }
                item { AlertStatusCard(dashboard.alertStatus) }
                item {
                    Text(
                        text = "统计生成时间：${formatLocalDateTime(dashboard.generatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.dashboard == null && !state.isLoading) {
                item {
                    PrimaryActionButton(
                        text = "重新加载风险统计",
                        onClick = onRetryClick,
                        enabled = !state.isBusy,
                    )
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun DashboardFilters(
    state: RiskDashboardUiState,
    onSelectPersonal: () -> Unit,
    onSelectFamily: (String) -> Unit,
    onSelectPeriodDays: (Int) -> Unit,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "统计范围",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectionChip(
                    text = "个人",
                    selected = state.scope == RiskDashboardScopeSelection.PERSONAL,
                    enabled = !state.isBusy,
                    onClick = onSelectPersonal,
                    modifier = Modifier.weight(1f),
                )
                SelectionChip(
                    text = "家庭",
                    selected = state.scope == RiskDashboardScopeSelection.FAMILY,
                    enabled = !state.isBusy && state.families.isNotEmpty(),
                    onClick = {
                        state.selectedFamilyId
                            ?.let(onSelectFamily)
                            ?: state.families.firstOrNull()?.id?.let(onSelectFamily)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.families.isNotEmpty()) {
                Text(
                    text = "选择家庭",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.families.forEach { family ->
                        SelectionChip(
                            text = family.name,
                            selected = state.scope == RiskDashboardScopeSelection.FAMILY &&
                                state.selectedFamilyId == family.id,
                            enabled = !state.isBusy,
                            onClick = { onSelectFamily(family.id) },
                        )
                    }
                }
            }

            Text(
                text = "统计周期",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(7, 30, 90).forEach { days ->
                    SelectionChip(
                        text = "近 $days 天",
                        selected = state.selectedPeriodDays == days,
                        enabled = !state.isBusy,
                        onClick = { onSelectPeriodDays(days) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            labelColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = enabled,
            borderColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    )
}

@Composable
private fun DashboardSummary(
    dashboard: RiskDashboardResponse,
) {
    val title = if (dashboard.scope == "family") {
        dashboard.familyName ?: "家庭风险概览"
    } else {
        "个人风险概览"
    }
    val subject = if (dashboard.scope == "family") {
        "${dashboard.memberCount} 位家庭成员"
    } else {
        "当前登录账户"
    }

    GradientSummaryCard(
        title = title,
        subtitle = "$subject · 近 ${dashboard.periodDays} 天",
        icon = Icons.Default.Assessment,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryMetric(
                value = dashboard.overview.totalEvents.toString(),
                label = "累计检测",
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                value = dashboard.overview.periodEvents.toString(),
                label = "周期检测",
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                value = String.format("%.1f", dashboard.overview.averageScore),
                label = "平均风险分",
                modifier = Modifier.weight(1f),
            )
        }
        dashboard.overview.latestEventAt?.let { time ->
            Text(
                text = "最近检测：${formatLocalDateTime(time)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.86f),
            )
        }
    }
}

@Composable
private fun RiskLevelDistributionCard(
    dashboard: RiskDashboardResponse,
) {
    val levels = dashboard.riskLevels
    val total = (levels.high + levels.medium + levels.low).coerceAtLeast(1)
    DataCard(title = "风险等级分布", subtitle = "按检测结果的风险等级统计") {
        DistributionRow("高风险", levels.high, total, RiskHigh)
        DistributionRow("中风险", levels.medium, total, RiskMedium)
        DistributionRow("低风险", levels.low, total, RiskLow)
    }
}

@Composable
private fun SourceDistributionCard(
    dashboard: RiskDashboardResponse,
) {
    val sources = dashboard.sourceTypes
    val total = (
        sources.text + sources.image + sources.url +
            sources.voice + sources.call + sources.other
        ).coerceAtLeast(1)

    DataCard(title = "检测来源", subtitle = "不同检测通道的使用情况") {
        DistributionRow("文本检测", sources.text, total, MaterialTheme.colorScheme.primary)
        DistributionRow("图片检测", sources.image, total, MaterialTheme.colorScheme.secondary)
        DistributionRow("链接检测", sources.url, total, RiskMedium)
        DistributionRow("语音检测", sources.voice, total, MaterialTheme.colorScheme.tertiary)
        DistributionRow("通话护航", sources.call, total, RiskLow)
        if (sources.other > 0) {
            DistributionRow("其他检测", sources.other, total, MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DistributionRow(
    label: String,
    count: Int,
    total: Int,
    color: Color,
) {
    val fraction = (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$count 条",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun DailyTrendCard(
    points: List<RiskDailyTrendPointDto>,
    periodDays: Int,
) {
    DataCard(
        title = "近 $periodDays 天风险趋势",
        subtitle = "柱高表示每日检测事件数量",
    ) {
        if (points.isEmpty() || points.all { it.total == 0 }) {
            Text(
                text = "该周期内暂无风险检测记录。",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DataCard
        }

        val maxTotal = points.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
        val visiblePoints = if (points.size > 30) points.takeLast(30) else points
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            visiblePoints.forEachIndexed { index, point ->
                val height = if (point.total == 0) {
                    5.dp
                } else {
                    (110f * point.total.toFloat() / maxTotal.toFloat())
                        .coerceAtLeast(10f)
                        .dp
                }
                val showLabel = periodDays <= 7 ||
                    index == 0 ||
                    index == visiblePoints.lastIndex ||
                    index % 5 == 0

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (point.total > 0 && periodDays <= 7) {
                        Text(
                            text = point.total.toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height)
                            .background(
                                color = if (point.high > 0) RiskHigh else MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (showLabel) point.date.takeLast(5) else "",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertStatusCard(
    alertStatus: FamilyAlertStatusDistributionDto,
) {
    DataCard(
        title = "家庭告警处理",
        subtitle = "家庭告警确认与完成情况",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallMetric(
                value = alertStatus.total.toString(),
                label = "告警总数",
                modifier = Modifier.weight(1f),
            )
            SmallMetric(
                value = "${(alertStatus.resolutionRate * 100).roundToInt()}%",
                label = "完成率",
                modifier = Modifier.weight(1f),
            )
        }
        DistributionRow("待确认", alertStatus.pending, alertStatus.total.coerceAtLeast(1), RiskHigh)
        DistributionRow("处理中", alertStatus.acknowledged, alertStatus.total.coerceAtLeast(1), MaterialTheme.colorScheme.primary)
        DistributionRow("已完成", alertStatus.resolved, alertStatus.total.coerceAtLeast(1), RiskLow)
        if (alertStatus.cancelled > 0) {
            DistributionRow("已取消", alertStatus.cancelled, alertStatus.total.coerceAtLeast(1), MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DataCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun SmallMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
