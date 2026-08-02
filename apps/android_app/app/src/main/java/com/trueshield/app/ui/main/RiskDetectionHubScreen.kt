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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trueshield.app.ui.components.ActionListItem
import com.trueshield.app.ui.components.FeatureTile
import com.trueshield.app.ui.components.SectionHeader
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.theme.RiskMedium
import com.trueshield.app.ui.theme.RiskMediumContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight
import com.trueshield.app.ui.theme.TrueShieldCyan
import com.trueshield.app.ui.theme.TrueShieldCyanLight

@Composable
fun RiskDetectionHubScreen(
    onTextRiskClick: () -> Unit,
    onImageRiskClick: () -> Unit,
    onUrlRiskClick: () -> Unit,
    onVoiceRiskClick: () -> Unit,
    onCallGuardClick: () -> Unit,
    onRiskHistoryClick: () -> Unit,
    onRiskDashboardClick: () -> Unit,
    onRiskReportClick: () -> Unit,
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
                        text = "风险检测",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        text = "选择内容类型，真信盾将结合规则与本地 AI 给出风险建议",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionHeader(
                    title = "快速检测",
                    subtitle = "一步只做一件事，结果更清晰",
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FeatureTile(
                        title = "文本检测",
                        description = "聊天记录、短信与通知",
                        icon = Icons.Default.TextFields,
                        iconColor = TrueShieldBlue,
                        iconBackground = TrueShieldBlueLight,
                        onClick = onTextRiskClick,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureTile(
                        title = "图片检测",
                        description = "截图、海报与二维码",
                        icon = Icons.Default.Image,
                        iconColor = TrueShieldCyan,
                        iconBackground = TrueShieldCyanLight,
                        onClick = onImageRiskClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FeatureTile(
                        title = "链接检测",
                        description = "网址、短链与跳转页面",
                        icon = Icons.Default.Link,
                        iconColor = RiskMedium,
                        iconBackground = RiskMediumContainer,
                        onClick = onUrlRiskClick,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureTile(
                        title = "语音检测",
                        description = "录音、音频与转写文本",
                        icon = Icons.Default.Mic,
                        iconColor = Color(0xFF7952B3),
                        iconBackground = Color(0xFFF0E8FF),
                        onClick = onVoiceRiskClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SectionHeader(
                    title = "通话与记录",
                    subtitle = "筛查陌生来电，回顾历史分析",
                )
            }

            item {
                TrueShieldCard {
                    Column(
                        modifier = Modifier.padding(4.dp),
                    ) {
                        ActionListItem(
                            title = "通话安全护航",
                            description = "号码风险筛查与一键家庭求助",
                            icon = Icons.Default.Call,
                            onClick = onCallGuardClick,
                        )
                        ActionListItem(
                            title = "风险历史",
                            description = "查看全部检测事件与处理详情",
                            icon = Icons.Default.History,
                            onClick = onRiskHistoryClick,
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "数据与报告",
                    subtitle = "了解趋势并导出脱敏风险报告",
                )
            }

            item {
                TrueShieldCard {
                    Column(
                        modifier = Modifier.padding(4.dp),
                    ) {
                        ActionListItem(
                            title = "风险统计",
                            description = "查看风险等级、来源和趋势",
                            icon = Icons.Default.BarChart,
                            onClick = onRiskDashboardClick,
                        )
                        ActionListItem(
                            title = "风险报告",
                            description = "生成个人或家庭脱敏 PDF",
                            icon = Icons.Default.PictureAsPdf,
                            onClick = onRiskReportClick,
                        )
                    }
                }
            }
        }
    }
}
