package com.trueshield.app.ui.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.alert.FamilyAlertResponse
import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.ui.components.RiskLevelBadge
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.family.common.FamilyAvatar
import com.trueshield.app.ui.family.common.FamilyEmptyState
import com.trueshield.app.ui.family.common.FamilyInlineMessage
import com.trueshield.app.ui.family.common.FamilyLoadingCard
import com.trueshield.app.ui.family.common.FamilyPageHeader
import com.trueshield.app.ui.family.common.FamilySectionHeader
import com.trueshield.app.ui.family.common.FamilyStatusPill
import com.trueshield.app.ui.family.common.familyAlertStatusColors
import com.trueshield.app.ui.family.common.familyAlertStatusLabel
import com.trueshield.app.ui.family.common.familyRoleLabel
import com.trueshield.app.ui.family.common.friendlyFamilyDateTime
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight
import com.trueshield.app.ui.theme.TrueShieldCyan

/**
 * 家庭告警列表：按家庭和处理状态筛选。
 */
@Composable
fun FamilyAlertScreen(
    state: FamilyAlertUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onRetryClick: () -> Unit,
    onFamilySelected: (String) -> Unit,
    onFilterSelected: (FamilyAlertFilter) -> Unit,
    onAlertClick: (
        familyId: String,
        alertId: String,
    ) -> Unit,
    onPolicyClick: () -> Unit,
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
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 14.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                FamilyPageHeader(
                    title = "家庭告警",
                    subtitle = "确认、处理和追踪家庭风险事件",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshing = state.isRefreshing,
                )
            }

            item {
                AlertOverviewCard(state = state)
            }

            state.successMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { message ->
                    item {
                        FamilyInlineMessage(
                            title = "操作成功",
                            message = message,
                            isError = false,
                        )
                    }
                }

            state.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { message ->
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FamilyInlineMessage(
                                title = "加载失败",
                                message = message,
                                isError = true,
                            )
                            OutlinedButton(
                                onClick = onRetryClick,
                                enabled = !state.isBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = "重新加载")
                            }
                        }
                    }
                }

            when {
                state.isInitialLoading && !state.hasLoadedOnce -> {
                    item {
                        FamilyLoadingCard(
                            message = "正在读取家庭告警……",
                        )
                    }
                }

                state.hasLoadedOnce && state.families.isEmpty() -> {
                    item {
                        FamilyEmptyState(
                            title = "还没有家庭",
                            message = "创建或加入家庭后，风险告警会显示在这里。",
                            icon = Icons.Default.Shield,
                        )
                    }
                }

                state.families.isNotEmpty() -> {
                    item {
                        FamilySectionHeader(
                            title = "选择家庭",
                            subtitle = "查看该家庭的全部风险告警",
                        )
                    }
                    item {
                        FamilySelector(
                            families = state.families,
                            selectedFamilyId = state.selectedFamilyId,
                            enabled = !state.isBusy,
                            onFamilySelected = onFamilySelected,
                        )
                    }
                }
            }

            if (!state.selectedFamilyId.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            FamilySectionHeader(
                                title = "告警记录",
                                subtitle = "按处理状态快速筛选",
                            )
                        }
                        OutlinedButton(
                            onClick = onPolicyClick,
                            enabled = !state.isBusy,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(5.dp))
                            Text(text = "自动策略")
                        }
                    }
                }

                item {
                    AlertFilterBar(
                        state = state,
                        onFilterSelected = onFilterSelected,
                    )
                }

                when {
                    state.isLoadingAlerts -> {
                        item {
                            FamilyLoadingCard(
                                message = "正在读取当前家庭告警……",
                            )
                        }
                    }

                    state.visibleAlerts.isEmpty() -> {
                        item {
                            FamilyEmptyState(
                                title = emptyTitle(state.selectedFilter),
                                message = emptyMessage(state.selectedFilter),
                                icon = Icons.Default.NotificationsActive,
                            )
                        }
                    }

                    else -> {
                        items(
                            items = state.visibleAlerts,
                            key = { alert -> alert.id },
                        ) { alert ->
                            FamilyAlertItemCard(
                                alert = alert,
                                onClick = {
                                    onAlertClick(alert.familyId, alert.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertOverviewCard(
    state: FamilyAlertUiState,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(TrueShieldBlue, TrueShieldCyan),
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = Color.White.copy(alpha = 0.18f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.selectedFamily?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: "家庭安全告警",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "优先处理待确认和处理中事项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AlertMetric(
                    value = state.pendingCount,
                    label = "待确认",
                    modifier = Modifier.weight(1f),
                )
                AlertMetric(
                    value = state.acknowledgedCount,
                    label = "处理中",
                    modifier = Modifier.weight(1f),
                )
                AlertMetric(
                    value = state.resolvedCount,
                    label = "已完成",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AlertMetric(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.16f),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 11.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.84f),
            )
        }
    }
}

@Composable
private fun FamilySelector(
    families: List<FamilyDto>,
    selectedFamilyId: String?,
    enabled: Boolean,
    onFamilySelected: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = families,
            key = { family -> family.id },
        ) { family ->
            val selected = family.id == selectedFamilyId
            val shape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier
                    .clip(shape)
                    .clickable(enabled = enabled) {
                        onFamilySelected(family.id)
                    }
                    .border(
                        width = 1.dp,
                        color =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    ),
                shape = shape,
                color =
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 10.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    FamilyAvatar(
                        name = family.name,
                        modifier = Modifier.size(36.dp),
                    )
                    Column {
                        Text(
                            text = family.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                        Text(
                            text = familyRoleLabel(family.myRole),
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
private fun AlertFilterBar(
    state: FamilyAlertUiState,
    onFilterSelected: (FamilyAlertFilter) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(horizontal = 1.dp),
    ) {
        item {
            FilterButton(
                text = "全部 ${state.totalAlerts}",
                selected = state.selectedFilter == FamilyAlertFilter.ALL,
                onClick = { onFilterSelected(FamilyAlertFilter.ALL) },
            )
        }
        item {
            FilterButton(
                text = "待确认 ${state.pendingCount}",
                selected = state.selectedFilter == FamilyAlertFilter.PENDING,
                onClick = { onFilterSelected(FamilyAlertFilter.PENDING) },
            )
        }
        item {
            FilterButton(
                text = "处理中 ${state.acknowledgedCount}",
                selected = state.selectedFilter == FamilyAlertFilter.ACKNOWLEDGED,
                onClick = { onFilterSelected(FamilyAlertFilter.ACKNOWLEDGED) },
            )
        }
        item {
            FilterButton(
                text = "已完成 ${state.resolvedCount}",
                selected = state.selectedFilter == FamilyAlertFilter.RESOLVED,
                onClick = { onFilterSelected(FamilyAlertFilter.RESOLVED) },
            )
        }
    }
}

@Composable
private fun FilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            ),
        shape = shape,
        color =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 15.dp,
                vertical = 9.dp,
            ),
            style = MaterialTheme.typography.labelMedium,
            color =
                if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FamilyAlertItemCard(
    alert: FamilyAlertResponse,
    onClick: () -> Unit,
) {
    val (statusColor, statusBackground) =
        familyAlertStatusColors(alert.status)

    TrueShieldCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(statusColor),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RiskLevelBadge(riskLevel = alert.riskLevel)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = friendlyFamilyDateTime(alert.createdAt),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FamilyStatusPill(
                        text = familyAlertStatusLabel(alert.status),
                        foreground = statusColor,
                        background = statusBackground,
                    )
                }

                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = alert.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${alert.recipients.size} 位接收人",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "查看详情  ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun emptyTitle(filter: FamilyAlertFilter): String =
    when (filter) {
        FamilyAlertFilter.ALL -> "暂无家庭告警"
        FamilyAlertFilter.PENDING -> "没有待确认告警"
        FamilyAlertFilter.ACKNOWLEDGED -> "没有处理中告警"
        FamilyAlertFilter.RESOLVED -> "没有已完成告警"
    }

private fun emptyMessage(filter: FamilyAlertFilter): String =
    when (filter) {
        FamilyAlertFilter.ALL -> "当前家庭暂时没有风险告警记录。"
        FamilyAlertFilter.PENDING -> "所有告警都已被查看或处理。"
        FamilyAlertFilter.ACKNOWLEDGED -> "目前没有正在处理的告警。"
        FamilyAlertFilter.RESOLVED -> "完成处理的告警会显示在这里。"
    }
