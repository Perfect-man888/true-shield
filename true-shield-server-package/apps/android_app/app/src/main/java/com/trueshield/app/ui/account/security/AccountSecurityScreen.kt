package com.trueshield.app.ui.account.security

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.SecondaryActionButton
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.data.ExperienceTopBar
import com.trueshield.app.ui.data.GradientSummaryCard
import com.trueshield.app.ui.data.InlineMessageCard

@Composable
fun AccountSecurityScreen(
    state: AccountSecurityUiState,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onToggleCurrentPasswordVisibility: () -> Unit,
    onToggleNewPasswordVisibility: () -> Unit,
    onToggleConfirmPasswordVisibility: () -> Unit,
    onSubmitClick: () -> Unit,
    onReloginClick: () -> Unit,
    onBackClick: () -> Unit,
) {
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
                    title = "账户安全",
                    subtitle = "定期更新密码，保护个人与家庭风险数据",
                    onBackClick = onBackClick,
                )
            }
            item {
                GradientSummaryCard(
                    title = "修改登录密码",
                    subtitle = "修改成功后需要使用新密码重新登录",
                    icon = Icons.Default.Security,
                ) {
                    Text(
                        text = "建议使用 8–128 位、与其他网站不同的密码。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            state.errorMessage?.let { message ->
                item { InlineMessageCard(message, isError = true) }
            }
            state.successMessage?.let { message ->
                item { InlineMessageCard(message, isError = false) }
            }

            item {
                TrueShieldCard {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "验证并设置新密码",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )

                        PasswordField(
                            value = state.currentPassword,
                            onValueChange = onCurrentPasswordChange,
                            label = "当前密码",
                            visible = state.currentPasswordVisible,
                            onToggleVisibility = onToggleCurrentPasswordVisibility,
                            enabled = !state.isSubmitting && !state.passwordChanged,
                        )
                        PasswordField(
                            value = state.newPassword,
                            onValueChange = onNewPasswordChange,
                            label = "新密码",
                            visible = state.newPasswordVisible,
                            onToggleVisibility = onToggleNewPasswordVisibility,
                            enabled = !state.isSubmitting && !state.passwordChanged,
                        )
                        PasswordField(
                            value = state.confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            label = "确认新密码",
                            visible = state.confirmPasswordVisible,
                            onToggleVisibility = onToggleConfirmPasswordVisibility,
                            enabled = !state.isSubmitting && !state.passwordChanged,
                        )

                        if (state.passwordChanged) {
                            PrimaryActionButton(
                                text = "使用新密码重新登录",
                                onClick = onReloginClick,
                            )
                        } else {
                            PrimaryActionButton(
                                text = if (state.isSubmitting) "正在修改……" else "确认修改密码",
                                onClick = onSubmitClick,
                                enabled = !state.isSubmitting,
                            )
                            SecondaryActionButton(
                                text = "取消",
                                onClick = onBackClick,
                                enabled = !state.isSubmitting,
                            )
                        }
                    }
                }
            }

            item {
                TrueShieldCard {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text(
                            text = "安全建议",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        SecurityTip("不要使用邮箱、手机号或连续数字作为密码。")
                        SecurityTip("不要与其他网站共用同一个密码。")
                        SecurityTip("不要向任何人透露登录密码或验证码。")
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun SecurityTip(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "•",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null)
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
        shape = MaterialTheme.shapes.large,
    )
}
