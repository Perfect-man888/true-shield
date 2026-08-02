package com.trueshield.app.ui.risk.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.risk.history.RiskEventSummaryDto
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.RiskLevelBadge
import com.trueshield.app.ui.components.SecondaryActionButton
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.data.EmptyStateCard
import com.trueshield.app.ui.data.ExperienceTopBar
import com.trueshield.app.ui.data.GradientSummaryCard
import com.trueshield.app.ui.data.InlineMessageCard
import com.trueshield.app.ui.data.LoadingStateCard
import com.trueshield.app.ui.data.SummaryMetric
import com.trueshield.app.ui.data.formatLocalDateTime
import com.trueshield.app.ui.data.formatRelativeTime
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskMedium

private enum class HistoryRiskFilter(
    val label: String,
    val value: String?,
) {
    ALL("全部", null),
    HIGH("高风险", "high"),
    MEDIUM("中风险", "medium"),
    LOW("低风险", "low"),
}

@Composable
fun RiskHistoryScreen(
    state: RiskHistoryUiState,
    onRefreshClick: () -> Unit,
    onLoadMoreClick: () -> Unit,
    onRetryClick: () -> Unit,
    onEventClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(HistoryRiskFilter.ALL) }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    val visibleItems = remember(state.items, selectedFilter) {
        val filterValue = selectedFilter.value
        if (filterValue == null) {
            state.items
        } else {
            state.items.filter { it.riskLevel.equals(filterValue, true) }
        }
    }
    val loadedHigh = state.items.count { it.riskLevel.equals("high", true) }

    TrueShieldPageBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(Modifier.height(18.dp)) }
            item {
                ExperienceTopBar(
                    title = "风险历史",
                    subtitle = "查看全部检测记录、风险结论和处置详情",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshEnabled = !state.isBusy,
                )
            }
            item {
                GradientSummaryCard(
                    title = "我的检测记录",
                    subtitle = "所有检测结果都会安全保存在当前账户中",
                    icon = Icons.Default.History,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryMetric(
                            value = state.total.toString(),
                            label = "累计记录",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryMetric(
                            value = loadedHigh.toString(),
                            label = "已加载高风险",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryMetric(
                            value = state.items.size.toString(),
                            label = "当前已加载",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "按风险等级筛选",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HistoryRiskFilter.entries.forEach { filter ->
                            val selected = selectedFilter == filter
                            AssistChip(
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
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
                                    enabled = true,
                                    borderColor = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            if (state.isInitialLoading) {
                item { LoadingStateCard("正在读取历史检测记录……") }
            } else if (state.items.isEmpty() && state.errorMessage == null) {
                item {
                    EmptyStateCard(
                        title = "暂无历史检测记录",
                        description = "完成文本、图片、链接、语音或通话检测后，记录会显示在这里。",
                        actionText = "刷新记录",
                        onActionClick = onRefreshClick,
                    )
                }
            } else if (visibleItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "当前筛选下暂无记录",
                        description = "切换到其他风险等级，或完成新的风险检测。",
                    )
                }
            }

            items(
                items = visibleItems,
                key = { it.eventId },
            ) { event ->
                HistoryEventCard(
                    event = event,
                    onClick = { onEventClick(event.eventId) },
                )
            }

            state.errorMessage?.let { message ->
                item { InlineMessageCard(message, isError = true) }
                item {
                    PrimaryActionButton(
                        text = "重新加载",
                        onClick = onRetryClick,
                        enabled = !state.isBusy,
                    )
                }
            }

            if (state.canLoadMore || state.isLoadingMore) {
                item {
                    SecondaryActionButton(
                        text = if (state.isLoadingMore) "正在加载更多……" else "加载更多记录",
                        onClick = onLoadMoreClick,
                        enabled = state.canLoadMore,
                    )
                }
            }

            if (state.items.isNotEmpty() && !state.canLoadMore && !state.isLoadingMore) {
                item {
                    Text(
                        text = "已经加载全部历史记录",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun HistoryEventCard(
    event: RiskEventSummaryDto,
    onClick: () -> Unit,
) {
    val riskColor = when (event.riskLevel.lowercase()) {
        "high" -> RiskHigh
        "medium" -> RiskMedium
        else -> RiskLow
    }
    val (sourceLabel, sourceIcon) = sourcePresentation(event.sourceType)

    TrueShieldCard(
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(190.dp)
                    .background(riskColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = sourceIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = sourceLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = formatRelativeTime(event.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    RiskLevelBadge(event.riskLevel)
                }

                Text(
                    text = event.summary.ifBlank { "该事件没有检测摘要。" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "风险分 ${event.score}/100",
                        style = MaterialTheme.typography.labelMedium,
                        color = riskColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${event.evidenceCount} 条证据",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatLocalDateTime(event.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun sourcePresentation(sourceType: String): Pair<String, ImageVector> =
    when (sourceType.lowercase()) {
        "text" -> "文本检测" to Icons.Default.Article
        "image" -> "图片检测" to Icons.Default.Image
        "url" -> "链接检测" to Icons.Default.Link
        "voice" -> "语音检测" to Icons.Default.Mic
        "call" -> "通话护航" to Icons.Default.Phone
        else -> "风险检测" to Icons.Default.Article
    }
