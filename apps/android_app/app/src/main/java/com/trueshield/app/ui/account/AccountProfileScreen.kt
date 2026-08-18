package com.trueshield.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trueshield.app.ui.components.ActionListItem
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.SecondaryActionButton
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.data.ExperienceTopBar
import com.trueshield.app.ui.data.GradientSummaryCard
import com.trueshield.app.ui.data.InlineMessageCard
import com.trueshield.app.ui.data.LabeledValueRow
import com.trueshield.app.ui.data.LoadingStateCard
import com.trueshield.app.ui.data.SummaryMetric
import com.trueshield.app.ui.data.formatLocalDateTime

@Composable
fun AccountProfileScreen(
    state: AccountProfileUiState,
    onDisplayNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onResetClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    TrueShieldPageBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(18.dp)) }
            item {
                ExperienceTopBar(
                    title = "个人资料与账户安全",
                    subtitle = "管理昵称、联系方式和登录密码",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshEnabled = !state.isLoading && !state.isSaving,
                )
            }

            if (state.isLoading && state.user == null) {
                item { LoadingStateCard("正在读取个人资料……") }
            } else if (state.user == null) {
                item {
                    InlineMessageCard(
                        message = state.errorMessage ?: "暂时无法读取个人资料。",
                        isError = true,
                    )
                }
                item {
                    PrimaryActionButton(
                        text = "重新加载",
                        onClick = onRefreshClick,
                    )
                }
            } else {
                val user = state.user
                val displayName = user.displayName
                    .trim()
                    .takeUnless { it.isBlank() || it.equals("string", true) }
                    ?: "未设置昵称"

                item {
                    GradientSummaryCard(
                        title = displayName,
                        subtitle = user.email,
                        icon = Icons.Default.AccountCircle,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SummaryMetric(
                                value = statusText(user.status),
                                label = "账户状态",
                                modifier = Modifier.weight(1f),
                            )
                            SummaryMetric(
                                value = user.phone?.takeIf { it.isNotBlank() }
                                    ?.let(::maskPhone) ?: "未绑定",
                                label = "联系电话",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                state.successMessage?.let { message ->
                    item { InlineMessageCard(message, isError = false) }
                }
                state.errorMessage?.let { message ->
                    item { InlineMessageCard(message, isError = true) }
                }

                item {
                    TrueShieldCard {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "编辑个人资料",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "昵称会用于首页问候和风险报告统计对象。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = state.displayName,
                                onValueChange = onDisplayNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("昵称") },
                                leadingIcon = {
                                    Icon(Icons.Default.Badge, contentDescription = null)
                                },
                                supportingText = { Text("2–64 个字符") },
                                singleLine = true,
                                enabled = !state.isSaving,
                                shape = MaterialTheme.shapes.large,
                            )

                            OutlinedTextField(
                                value = state.phone,
                                onValueChange = onPhoneChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("手机号（选填）") },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null)
                                },
                                supportingText = { Text("留空并保存可删除手机号") },
                                singleLine = true,
                                enabled = !state.isSaving,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                ),
                                shape = MaterialTheme.shapes.large,
                            )

                            PrimaryActionButton(
                                text = if (state.isSaving) "正在保存……" else "保存个人资料",
                                onClick = onSaveClick,
                                enabled = !state.isSaving && !state.isLoading,
                            )
                            SecondaryActionButton(
                                text = "撤销未保存修改",
                                onClick = onResetClick,
                                enabled = !state.isSaving,
                            )
                        }
                    }
                }

                item {
                    TrueShieldCard {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "账户信息",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            LabeledValueRow("邮箱", user.email)
                            LabeledValueRow("注册时间", formatLocalDateTime(user.createdAt))
                            LabeledValueRow("最近更新", formatLocalDateTime(user.updatedAt))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "用户编号 ${user.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                TrueShieldCard {
                    Column(modifier = Modifier.padding(4.dp)) {
                        ActionListItem(
                            title = "修改登录密码",
                            description = "验证当前密码后更新登录密码",
                            icon = Icons.Default.Lock,
                            onClick = onSecurityClick,
                        )
                        ActionListItem(
                            title = "退出当前账户",
                            description = "退出后需要重新输入账号和密码",
                            icon = Icons.Default.Logout,
                            iconColor = MaterialTheme.colorScheme.error,
                            iconBackground = MaterialTheme.colorScheme.errorContainer,
                            onClick = { showLogoutDialog = true },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("确认退出账户？") },
            text = { Text("退出后，本机保存的登录状态将被清除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    },
                ) {
                    Text("确认退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

private fun statusText(status: String): String = when (status.lowercase()) {
    "active" -> "正常"
    "disabled" -> "已停用"
    "locked" -> "已锁定"
    else -> status
}

private fun maskPhone(phone: String): String =
    if (phone.length >= 7) {
        phone.take(3) + "****" + phone.takeLast(4)
    } else {
        phone
    }
