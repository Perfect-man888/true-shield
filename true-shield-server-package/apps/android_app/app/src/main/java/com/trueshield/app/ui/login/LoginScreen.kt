package com.trueshield.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground

@Composable
fun LoginScreen(
    state: LoginUiState,
    onDisplayNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onResetCodeChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onResetConfirmPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onBackToLoginClick: () -> Unit,
    onResendResetCodeClick: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TrueShieldPageBackground(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AuthBrandHeader(
                showBack = !state.isLoginMode,
                onBackClick = onBackToLoginClick,
                backEnabled = !state.isLoading,
            )

            TrueShieldCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = screenTitle(state),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = screenSubtitle(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.isRegisterMode) {
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
                            enabled = !state.isLoading,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("邮箱") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        supportingText = when {
                            state.isPasswordResetConfirmMode -> {
                                { Text("验证码已发送到该邮箱") }
                            }
                            state.isLoginMode -> {
                                { Text("使用注册邮箱登录") }
                            }
                            else -> null
                        },
                        singleLine = true,
                        enabled = !state.isLoading &&
                            !state.isPasswordResetConfirmMode,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (state.isPasswordResetRequestMode) {
                                ImeAction.Done
                            } else {
                                ImeAction.Next
                            },
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (state.isPasswordResetRequestMode) {
                                    onSubmit()
                                }
                            },
                        ),
                        shape = RoundedCornerShape(16.dp),
                    )

                    if (state.isRegisterMode) {
                        OutlinedTextField(
                            value = state.phone,
                            onValueChange = onPhoneChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("手机号（选填）") },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null)
                            },
                            singleLine = true,
                            enabled = !state.isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }

                    if (state.isLoginMode || state.isRegisterMode) {
                        PasswordField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            label = "密码",
                            isVisible = state.isPasswordVisible,
                            enabled = !state.isLoading,
                            imeAction = if (state.isRegisterMode) {
                                ImeAction.Next
                            } else {
                                ImeAction.Done
                            },
                            onDone = {
                                if (state.isLoginMode) onSubmit()
                            },
                            onToggleVisibility = onTogglePasswordVisibility,
                        )
                    }

                    if (state.isRegisterMode) {
                        PasswordField(
                            value = state.confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            label = "确认密码",
                            isVisible = state.isPasswordVisible,
                            enabled = !state.isLoading,
                            imeAction = ImeAction.Done,
                            onDone = onSubmit,
                            onToggleVisibility = onTogglePasswordVisibility,
                        )
                        SecurityHint(
                            text = "密码需为 8–128 位，建议同时包含字母、数字和符号。",
                        )
                    }

                    if (state.isPasswordResetConfirmMode) {
                        OutlinedTextField(
                            value = state.resetCode,
                            onValueChange = onResetCodeChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("6 位邮箱验证码") },
                            leadingIcon = {
                                Icon(Icons.Default.AlternateEmail, contentDescription = null)
                            },
                            supportingText = {
                                val minutes = state.resetExpiresInSeconds / 60
                                Text(
                                    if (minutes > 0) {
                                        "验证码约 $minutes 分钟内有效，只能使用一次"
                                    } else {
                                        "验证码只能使用一次"
                                    },
                                )
                            },
                            singleLine = true,
                            enabled = !state.isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )

                        PasswordField(
                            value = state.newPassword,
                            onValueChange = onNewPasswordChange,
                            label = "新密码",
                            isVisible = state.isPasswordVisible,
                            enabled = !state.isLoading,
                            imeAction = ImeAction.Next,
                            onDone = {},
                            onToggleVisibility = onTogglePasswordVisibility,
                        )

                        PasswordField(
                            value = state.resetConfirmPassword,
                            onValueChange = onResetConfirmPasswordChange,
                            label = "确认新密码",
                            isVisible = state.isPasswordVisible,
                            enabled = !state.isLoading,
                            imeAction = ImeAction.Done,
                            onDone = onSubmit,
                            onToggleVisibility = onTogglePasswordVisibility,
                        )
                        SecurityHint(
                            text = "重置成功后，请使用新密码重新登录。",
                        )
                    }

                    state.infoMessage?.let { message ->
                        AuthMessageCard(
                            message = message,
                            isError = false,
                        )
                    }

                    state.errorMessage?.let { message ->
                        AuthMessageCard(
                            message = message,
                            isError = true,
                        )
                    }

                    Button(
                        onClick = onSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = !state.isLoading,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text("正在处理")
                        } else {
                            Text(
                                text = submitButtonText(state),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    when {
                        state.isLoginMode -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = onForgotPasswordClick,
                                    enabled = !state.isLoading,
                                ) {
                                    Text("忘记密码？")
                                }
                                TextButton(
                                    onClick = onToggleAuthMode,
                                    enabled = !state.isLoading,
                                ) {
                                    Text("立即注册")
                                }
                            }
                        }

                        state.isRegisterMode -> {
                            TextButton(
                                onClick = onToggleAuthMode,
                                enabled = !state.isLoading,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text("已有账户？返回登录")
                            }
                        }

                        state.isPasswordResetRequestMode -> Unit

                        state.isPasswordResetConfirmMode -> {
                            TextButton(
                                onClick = onResendResetCodeClick,
                                enabled = !state.isLoading,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text("没有收到？重新发送验证码")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "账户用于同步风险历史、家庭告警和脱敏报告。真信盾不会要求你提供银行卡密码或短信验证码。",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AuthBrandHeader(
    showBack: Boolean,
    onBackClick: () -> Unit,
    backEnabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showBack) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBackClick,
                    enabled = backEnabled,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回登录",
                    )
                }
                Text(
                    text = "返回登录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier.size(82.dp),
            shape = RoundedCornerShape(27.dp),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "真信盾",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "守护每一次联系与决定",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SecurityHint(
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuthMessageCard(
    message: String,
    isError: Boolean,
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val foregroundColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isError) {
                    Icons.Default.ErrorOutline
                } else {
                    Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = foregroundColor,
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = foregroundColor,
            )
        }
    }
}

private fun screenTitle(
    state: LoginUiState,
): String = when {
    state.isRegisterMode -> "创建家庭守护账户"
    state.isPasswordResetRequestMode -> "找回登录密码"
    state.isPasswordResetConfirmMode -> "验证邮箱并设置新密码"
    else -> "欢迎回来"
}

private fun screenSubtitle(
    state: LoginUiState,
): String = when {
    state.isRegisterMode -> "注册后会自动登录，并可继续创建或加入家庭。"
    state.isPasswordResetRequestMode -> "输入注册邮箱，接收 6 位重置验证码。"
    state.isPasswordResetConfirmMode -> "填写邮件验证码，并设置一组新的登录密码。"
    else -> "登录后继续查看风险记录与家庭守护状态。"
}

private fun submitButtonText(
    state: LoginUiState,
): String = when {
    state.isRegisterMode -> "注册并登录"
    state.isPasswordResetRequestMode -> "发送重置验证码"
    state.isPasswordResetConfirmMode -> "确认重置密码"
    else -> "安全登录"
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isVisible: Boolean,
    enabled: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit,
    onToggleVisibility: () -> Unit,
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
        visualTransformation = if (isVisible) {
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
                    imageVector = if (isVisible) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = if (isVisible) "隐藏密码" else "显示密码",
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() },
        ),
        shape = RoundedCornerShape(16.dp),
    )
}
