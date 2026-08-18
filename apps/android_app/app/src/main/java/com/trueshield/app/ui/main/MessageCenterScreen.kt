package com.trueshield.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.family.common.FamilyEmptyState
import com.trueshield.app.ui.family.common.FamilyInlineMessage
import com.trueshield.app.ui.family.common.FamilySectionHeader
import com.trueshield.app.ui.family.common.FamilyStatusPill
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.RiskMedium
import com.trueshield.app.ui.theme.RiskMediumContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight
import com.trueshield.app.ui.theme.TrueShieldCyan

/**
 * 消息中心：聚合待处理告警、家庭邀请和近七天风险提醒。
 */
@Composable
fun MessageCenterScreen(
    state: MessageCenterUiState,
    onRefreshClick: () -> Unit,
    onFamilyAlertClick: () -> Unit,
    onFamilyCenterClick: () -> Unit,
    onRiskHistoryClick: () -> Unit,
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
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(
                top = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "消息",
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            text = "集中处理家庭告警、邀请和风险提醒",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !state.isLoading,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新消息",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            item {
                MessageSummaryCard(state = state)
            }

            state.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { message ->
                    item {
                        FamilyInlineMessage(
                            title = "部分消息暂时无法更新",
                            message = message,
                            isError = true,
                        )
                    }
                }

            if (state.initialized && !state.hasActionableMessages) {
                item {
                    FamilyEmptyState(
                        title = "目前没有待处理消息",
                        message = "家庭告警和邀请都已处理。新的安全事项会及时显示在这里。",
                        icon = Icons.Default.Inbox,
                    )
                }
            }

            item {
                FamilySectionHeader(
                    title = "需要处理",
                    subtitle = "数字表示当前待处理数量，不代表跨设备已读状态",
                    trailing = {
                        if (state.actionableCount > 0) {
                            FamilyStatusPill(
                                text = "${state.actionableCount} 项",
                                foreground = RiskHigh,
                                background = RiskHighContainer,
                            )
                        }
                    },
                )
            }

            item {
                MessageCategoryCard(
                    title = "家庭告警",
                    description = alertDescription(state),
                    icon = Icons.Default.NotificationsActive,
                    iconColor = RiskHigh,
                    iconBackground = RiskHighContainer,
                    badgeText =
                        if (state.activeAlertCount > 0) {
                            "${state.activeAlertCount} 条待处理"
                        } else {
                            "暂无待处理"
                        },
                    badgeForeground =
                        if (state.activeAlertCount > 0) RiskHigh else RiskLow,
                    badgeBackground =
                        if (state.activeAlertCount > 0) RiskHighContainer else RiskLowContainer,
                    onClick = onFamilyAlertClick,
                )
            }

            item {
                MessageCategoryCard(
                    title = "家庭邀请",
                    description =
                        if (state.invitationCount > 0) {
                            "有新的家庭邀请等待确认"
                        } else {
                            "当前没有待处理家庭邀请"
                        },
                    icon = Icons.Default.GroupAdd,
                    iconColor = RiskMedium,
                    iconBackground = RiskMediumContainer,
                    badgeText =
                        if (state.invitationCount > 0) {
                            "${state.invitationCount} 条待处理"
                        } else {
                            "暂无待处理"
                        },
                    badgeForeground =
                        if (state.invitationCount > 0) RiskMedium else RiskLow,
                    badgeBackground =
                        if (state.invitationCount > 0) RiskMediumContainer else RiskLowContainer,
                    onClick = onFamilyCenterClick,
                )
            }

            item {
                FamilySectionHeader(
                    title = "风险提醒",
                    subtitle = "回顾近 7 天检测结果和处置建议",
                )
            }

            item {
                MessageCategoryCard(
                    title = "近 7 天风险记录",
                    description = "共 ${state.recentRiskCount} 次检测，其中 ${state.recentHighRiskCount} 次高风险",
                    icon =
                        if (state.recentHighRiskCount > 0) {
                            Icons.Default.WarningAmber
                        } else {
                            Icons.Default.History
                        },
                    iconColor =
                        if (state.recentHighRiskCount > 0) RiskHigh else TrueShieldBlue,
                    iconBackground =
                        if (state.recentHighRiskCount > 0) RiskHighContainer else TrueShieldBlueLight,
                    badgeText =
                        if (state.recentHighRiskCount > 0) {
                            "${state.recentHighRiskCount} 次高风险"
                        } else {
                            "风险记录"
                        },
                    badgeForeground =
                        if (state.recentHighRiskCount > 0) RiskHigh else TrueShieldBlue,
                    badgeBackground =
                        if (state.recentHighRiskCount > 0) RiskHighContainer else TrueShieldBlueLight,
                    onClick = onRiskHistoryClick,
                )
            }
        }
    }
}

@Composable
private fun MessageSummaryCard(
    state: MessageCenterUiState,
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
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (state.hasActionableMessages) {
                                "有 ${state.actionableCount} 项需要关注"
                            } else {
                                "家庭消息已处理完成"
                            },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "已关联 ${state.familyCount} 个家庭",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.84f),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryMetric(
                    value = state.activeAlertCount,
                    label = "活动告警",
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    value = state.invitationCount,
                    label = "家庭邀请",
                    modifier = Modifier.weight(1f),
                )
                SummaryMetric(
                    value = state.recentHighRiskCount,
                    label = "高风险记录",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
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
                horizontal = 9.dp,
                vertical = 10.dp,
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
private fun MessageCategoryCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Color,
    badgeText: String,
    badgeForeground: Color,
    badgeBackground: Color,
    onClick: () -> Unit,
) {
    TrueShieldCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = iconBackground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(7.dp))
                FamilyStatusPill(
                    text = badgeText,
                    foreground = badgeForeground,
                    background = badgeBackground,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun alertDescription(state: MessageCenterUiState): String =
    when {
        state.pendingAlertCount > 0 && state.acknowledgedAlertCount > 0 ->
            "${state.pendingAlertCount} 条待确认，${state.acknowledgedAlertCount} 条处理中"

        state.pendingAlertCount > 0 ->
            "${state.pendingAlertCount} 条告警等待家庭成员确认"

        state.acknowledgedAlertCount > 0 ->
            "${state.acknowledgedAlertCount} 条告警正在处理中"

        else -> "当前没有待处理家庭告警"
    }
