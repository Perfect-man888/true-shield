package com.trueshield.app.ui.main

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trueshield.app.ui.components.ActionListItem
import com.trueshield.app.ui.components.SectionHeader
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight

@Composable
fun FamilyHubScreen(
    onFamilyCenterClick: () -> Unit,
    onFamilyAlertClick: () -> Unit,
    onTrustedContactsClick: () -> Unit,
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
                        text = "家庭守护",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        text = "让可信家人共同确认风险、处理告警",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0D5F91),
                                        Color(0xFF0E9F8B),
                                    ),
                                ),
                            )
                            .padding(20.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(17.dp),
                                color = Color.White.copy(alpha = 0.16f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(29.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.size(14.dp))
                            Column {
                                Text(
                                    text = "家庭安全共同守护",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                )
                                Spacer(Modifier.size(3.dp))
                                Text(
                                    text = "成员、告警与可信联系人统一管理",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.84f),
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "家庭服务",
                    subtitle = "选择需要管理的内容",
                )
            }

            item {
                TrueShieldCard {
                    Column(
                        modifier = Modifier.padding(4.dp),
                    ) {
                        ActionListItem(
                            title = "家庭中心",
                            description = "创建家庭、邀请成员并查看成员信息",
                            icon = Icons.Default.Groups,
                            iconColor = TrueShieldBlue,
                            iconBackground = TrueShieldBlueLight,
                            onClick = onFamilyCenterClick,
                        )
                        ActionListItem(
                            title = "家庭告警",
                            description = "确认、处理和追踪风险告警",
                            icon = Icons.Default.NotificationsActive,
                            iconColor = RiskHigh,
                            iconBackground = RiskHighContainer,
                            onClick = onFamilyAlertClick,
                        )
                        ActionListItem(
                            title = "可信联系人",
                            description = "设置紧急情况下优先联系的人",
                            icon = Icons.Default.ContactPhone,
                            iconColor = RiskLow,
                            iconBackground = RiskLowContainer,
                            onClick = onTrustedContactsClick,
                        )
                    }
                }
            }
        }
    }
}
