package com.trueshield.app.ui.main

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trueshield.app.ui.components.ActionListItem
import com.trueshield.app.ui.components.SectionHeader
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight
import com.trueshield.app.ui.theme.TrueShieldThemeMode

@Composable
fun ProfileHubScreen(
    themeMode: TrueShieldThemeMode,
    elderModeEnabled: Boolean,
    onThemeModeChange: (TrueShieldThemeMode) -> Unit,
    onElderModeChange: (Boolean) -> Unit,
    highRiskVoiceAlertEnabled: Boolean,
    onHighRiskVoiceAlertChange: (Boolean) -> Unit,
    onOnboardingClick: () -> Unit,
    onAccountProfileClick: () -> Unit,
    onRiskDashboardClick: () -> Unit,
    onRiskReportClick: () -> Unit,
    onRiskFeedbackStatisticsClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
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
                Column {
                    Text(
                        text = "我的",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        text = "管理账户、显示方式和风险数据",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                TrueShieldCard {
                    ActionListItem(
                        title = "个人资料与账户安全",
                        description = "修改昵称、手机号和登录密码",
                        icon = Icons.Default.Person,
                        iconColor = TrueShieldBlue,
                        iconBackground = TrueShieldBlueLight,
                        onClick = onAccountProfileClick,
                    )
                }
            }

            item {
                SectionHeader(
                    title = "显示设置",
                    subtitle = "设置会保存在当前设备",
                )
            }

            item {
                TrueShieldCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "外观模式",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "浅色、深色或跟随系统",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeModeChip(
                                text = "跟随系统",
                                selected = themeMode == TrueShieldThemeMode.SYSTEM,
                                onClick = {
                                    onThemeModeChange(TrueShieldThemeMode.SYSTEM)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ThemeModeChip(
                                text = "浅色",
                                selected = themeMode == TrueShieldThemeMode.LIGHT,
                                onClick = {
                                    onThemeModeChange(TrueShieldThemeMode.LIGHT)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ThemeModeChip(
                                text = "深色",
                                selected = themeMode == TrueShieldThemeMode.DARK,
                                onClick = {
                                    onThemeModeChange(TrueShieldThemeMode.DARK)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "长辈模式",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "放大字体并提高界面对比度",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = elderModeEnabled,
                                onCheckedChange = onElderModeChange,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "高风险语音提醒",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = if (elderModeEnabled) {
                                        "检测到高风险时自动播报安全提醒"
                                    } else {
                                        "启用长辈模式后生效"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = highRiskVoiceAlertEnabled,
                                onCheckedChange = onHighRiskVoiceAlertChange,
                                enabled = elderModeEnabled,
                            )
                        }
                    }
                }
            }

            item {
                TrueShieldCard {
                    ActionListItem(
                        title = "使用引导与隐私说明",
                        description = "重新查看检测、家庭守护与隐私保护说明",
                        icon = Icons.Default.HelpOutline,
                        onClick = onOnboardingClick,
                    )
                }
            }

            item {
                SectionHeader(
                    title = "风险数据",
                    subtitle = "统计、报告与反馈质量",
                )
            }

            item {
                TrueShieldCard {
                    Column(modifier = Modifier.padding(4.dp)) {
                        ActionListItem(
                            title = "风险统计",
                            description = "查看等级、来源与近期趋势",
                            icon = Icons.Default.BarChart,
                            onClick = onRiskDashboardClick,
                        )
                        ActionListItem(
                            title = "风险报告",
                            description = "导出个人或家庭脱敏 PDF",
                            icon = Icons.Default.PictureAsPdf,
                            onClick = onRiskReportClick,
                        )
                        ActionListItem(
                            title = "反馈统计",
                            description = "查看纠错与反馈质量数据",
                            icon = Icons.Default.FactCheck,
                            onClick = onRiskFeedbackStatisticsClick,
                        )
                    }
                }
            }

            item {
                TrueShieldCard {
                    ActionListItem(
                        title = "退出登录",
                        description = "退出后需要重新输入账户密码",
                        icon = Icons.Default.Logout,
                        iconColor = RiskHigh,
                        iconBackground = RiskHighContainer,
                        onClick = onLogoutClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                maxLines = 1,
            )
        },
        modifier = modifier,
    )
}
