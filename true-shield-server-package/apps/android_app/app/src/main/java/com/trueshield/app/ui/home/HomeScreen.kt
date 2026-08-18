package com.trueshield.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import com.trueshield.app.ui.components.DotStatus
import com.trueshield.app.ui.components.FeatureTile
import com.trueshield.app.ui.components.MetricBlock
import com.trueshield.app.ui.components.SectionHeader
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.RiskMedium
import com.trueshield.app.ui.theme.RiskMediumContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight
import com.trueshield.app.ui.theme.TrueShieldCyan
import com.trueshield.app.ui.theme.TrueShieldCyanLight
import java.util.Calendar

private data class HomeFeature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBackground: Color,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefreshClick: () -> Unit,
    onCallGuardClick: () -> Unit,
    onTextRiskClick: () -> Unit,
    onImageRiskClick: () -> Unit,
    onVoiceRiskClick: () -> Unit,
    onUrlRiskClick: () -> Unit,
    onRiskHistoryClick: () -> Unit,
    onFamilyAlertClick: () -> Unit,
) {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "上午好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }

    val displayName =
        state.displayName.ifBlank { "真信盾用户" }

    val featureItems = listOf(
        HomeFeature(
            title = "文本检测",
            description = "粘贴可疑聊天或短信",
            icon = Icons.Default.TextFields,
            iconColor = TrueShieldBlue,
            iconBackground = TrueShieldBlueLight,
            onClick = onTextRiskClick,
        ),
        HomeFeature(
            title = "图片检测",
            description = "识别截图与二维码风险",
            icon = Icons.Default.Image,
            iconColor = TrueShieldCyan,
            iconBackground = TrueShieldCyanLight,
            onClick = onImageRiskClick,
        ),
        HomeFeature(
            title = "链接检测",
            description = "检查网页和跳转地址",
            icon = Icons.Default.Link,
            iconColor = RiskMedium,
            iconBackground = RiskMediumContainer,
            onClick = onUrlRiskClick,
        ),
        HomeFeature(
            title = "语音检测",
            description = "录音或导入音频分析",
            icon = Icons.Default.Mic,
            iconColor = Color(0xFF7952B3),
            iconBackground = Color(0xFFF0E8FF),
            onClick = onVoiceRiskClick,
        ),
    )

    TrueShieldPageBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 18.dp,
                bottom = 24.dp,
            ),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "$greeting，$displayName",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "真信盾正在守护您的家庭",
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
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新首页",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            item {
                ProtectionOverviewCard(
                    callGuardEnabled = state.callGuardEnabled,
                    todayDetectionCount = state.todayDetectionCount,
                    pendingAlertCount = state.pendingAlertCount,
                )
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = RiskMediumContainer,
                    ) {
                        Text(
                            text = "部分概览暂未更新：${state.errorMessage}",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = RiskMedium,
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "通话安全护航",
                    subtitle = "来电前筛查号码风险，必要时一键通知家人",
                )
            }

            item {
                CallGuardCard(
                    enabled = state.callGuardEnabled,
                    familyCount = state.familyCount,
                    onClick = onCallGuardClick,
                )
            }

            item {
                SectionHeader(
                    title = "快速检测",
                    subtitle = "选择内容类型，立即开始风险分析",
                )
            }

            items(featureItems.chunked(2).size) { rowIndex ->
                val rowItems = featureItems.chunked(2)[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { feature ->
                        FeatureTile(
                            title = feature.title,
                            description = feature.description,
                            icon = feature.icon,
                            iconColor = feature.iconColor,
                            iconBackground = feature.iconBackground,
                            onClick = feature.onClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionHeader(
                    title = "家庭守护",
                    subtitle = "关注家庭告警与历史检测情况",
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FeatureTile(
                        title = "家庭告警",
                        description = if (state.pendingAlertCount > 0) {
                            "${state.pendingAlertCount} 条告警待处理"
                        } else {
                            "当前没有待处理告警"
                        },
                        icon = Icons.Default.NotificationsActive,
                        iconColor = RiskHigh,
                        iconBackground = RiskHighContainer,
                        onClick = onFamilyAlertClick,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureTile(
                        title = "风险历史",
                        description = "查看全部检测记录与详情",
                        icon = Icons.Default.History,
                        iconColor = RiskLow,
                        iconBackground = RiskLowContainer,
                        onClick = onRiskHistoryClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionOverviewCard(
    callGuardEnabled: Boolean,
    todayDetectionCount: Int,
    pendingAlertCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF124A96),
                            Color(0xFF1C72C9),
                            Color(0xFF159C91),
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.16f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            text = "家庭安全状态",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            text = if (callGuardEnabled) {
                                "通话护航已开启"
                            } else {
                                "建议开启通话护航"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.84f),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricBlock(
                        label = "今日检测",
                        value = "$todayDetectionCount 次",
                        modifier = Modifier.weight(1f),
                    )
                    MetricBlock(
                        label = "待处理告警",
                        value = "$pendingAlertCount 条",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CallGuardCard(
    enabled: Boolean,
    familyCount: Int,
    onClick: () -> Unit,
) {
    TrueShieldCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(17.dp),
                color = TrueShieldBlueLight,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = TrueShieldBlue,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.size(13.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = if (enabled) "守护已开启" else "点击开启守护",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DotStatus(
                    label = if (enabled) {
                        "正在筛查陌生来电"
                    } else {
                        "尚未获得通话筛查权限"
                    },
                    active = enabled,
                )
                Text(
                    text = if (familyCount > 0) {
                        "已关联 $familyCount 个家庭"
                    } else {
                        "进入后设置一键求助家庭"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
