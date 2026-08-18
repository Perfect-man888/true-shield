package com.trueshield.app.ui.alert.policy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 家庭告警自动触发策略页面。
 *
 * 当前文件只负责显示页面和转发用户操作，
 * 网络请求和状态管理全部由 FamilyAlertPolicyViewModel 完成。
 */
@Composable
fun FamilyAlertPolicyScreen(
    familyId: String,
    state: FamilyAlertPolicyUiState,
    onBackClick: () -> Unit,
    onLoadPolicy: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onAutoCreateEnabledChange: (Boolean) -> Unit,
    onAutoDispatchEnabledChange: (Boolean) -> Unit,
    onMinimumRiskLevelChange: (String) -> Unit,
    onSourceTypeToggle: (String) -> Unit,
    onMaxRecipientsChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onResetClick: () -> Unit,
) {
    /**
     * 页面首次打开，或者 familyId 改变时，
     * 自动读取对应家庭的告警策略。
     */
    LaunchedEffect(familyId) {
        if (familyId.isNotBlank()) {
            onLoadPolicy(familyId)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            item {
                PolicyHeader(
                    onBackClick = onBackClick,
                )
            }

            item {
                PolicyOverviewCard(
                    state = state,
                )
            }

            if (state.isLoading && state.policy == null) {
                item {
                    InitialLoadingContent()
                }
            } else {
                item {
                    AutoCreatePolicyCard(
                        enabled = state.autoCreateEnabled,
                        onEnabledChange = onAutoCreateEnabledChange,
                    )
                }

                item {
                    AutoDispatchPolicyCard(
                        enabled = state.autoDispatchEnabled,
                        autoCreateEnabled = state.autoCreateEnabled,
                        onEnabledChange = onAutoDispatchEnabledChange,
                    )
                }

                item {
                    MinimumRiskLevelCard(
                        selectedRiskLevel = state.minimumRiskLevel,
                        enabled = state.autoCreateEnabled,
                        onRiskLevelChange = onMinimumRiskLevelChange,
                    )
                }

                item {
                    SourceTypeCard(
                        enabledSourceTypes = state.enabledSourceTypes,
                        enabled = state.autoCreateEnabled,
                        onSourceTypeToggle = onSourceTypeToggle,
                    )
                }

                item {
                    MaxRecipientsCard(
                        value = state.maxRecipientsText,
                        enabled = state.autoCreateEnabled,
                        onValueChange = onMaxRecipientsChange,
                    )
                }

                state.errorMessage?.let { errorMessage ->
                    item {
                        PolicyMessageCard(
                            title = "操作失败",
                            message = errorMessage,
                            isError = true,
                        )
                    }
                }

                state.successMessage?.let { successMessage ->
                    item {
                        PolicyMessageCard(
                            title = "操作成功",
                            message = successMessage,
                            isError = false,
                        )
                    }
                }

                item {
                    PolicyActionCard(
                        state = state,
                        onSaveClick = onSaveClick,
                        onResetClick = onResetClick,
                        onRefreshClick = onRefreshClick,
                    )
                }

                state.policy?.let { policy ->
                    item {
                        PolicyInformationCard(
                            familyId = policy.familyId,
                            createdAt = policy.createdAt,
                            updatedAt = policy.updatedAt,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 页面顶部标题。
 */
@Composable
private fun PolicyHeader(
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBackClick,
        ) {
            Text(text = "返回")
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "自动告警设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 页面总览卡片。
 */
@Composable
private fun PolicyOverviewCard(
    state: FamilyAlertPolicyUiState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "家庭安全自动化",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(
                text = when {
                    state.autoCreateEnabled &&
                            state.autoDispatchEnabled -> {
                        "检测到符合条件的风险事件后，系统会自动创建家庭告警并通知可信联系人。"
                    }

                    state.autoCreateEnabled -> {
                        "检测到符合条件的风险事件后，系统会自动创建家庭告警，但不会自动发送。"
                    }

                    else -> {
                        "自动告警当前已关闭。风险检测结果仍会正常保存，但不会自动创建家庭告警。"
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            if (state.hasUnsavedChanges) {
                Text(
                    text = "当前存在尚未保存的修改。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * 自动创建家庭告警开关。
 */
@Composable
private fun AutoCreatePolicyCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    SettingSwitchCard(
        title = "自动创建家庭告警",
        description = if (enabled) {
            "检测到达到最低风险等级的事件后，自动生成家庭告警。"
        } else {
            "风险检测完成后不会自动生成家庭告警。"
        },
        checked = enabled,
        onCheckedChange = onEnabledChange,
    )
}

/**
 * 自动发送家庭告警开关。
 */
@Composable
private fun AutoDispatchPolicyCard(
    enabled: Boolean,
    autoCreateEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    SettingSwitchCard(
        title = "自动通知可信联系人",
        description = when {
            !autoCreateEnabled -> {
                "需要先开启自动创建家庭告警。"
            }

            enabled -> {
                "创建家庭告警后，系统会自动向符合条件的可信联系人发送通知。"
            }

            else -> {
                "家庭告警创建后，需要进入告警详情手动发送通知。"
            }
        },
        checked = enabled,
        switchEnabled = autoCreateEnabled,
        onCheckedChange = onEnabledChange,
    )
}

/**
 * 通用开关卡片。
 */
@Composable
private fun SettingSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    switchEnabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = checked,
                enabled = switchEnabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

/**
 * 最低风险等级设置。
 */
@Composable
private fun MinimumRiskLevelCard(
    selectedRiskLevel: String,
    enabled: Boolean,
    onRiskLevelChange: (String) -> Unit,
) {
    val riskLevels = listOf(
        RiskLevelOption(
            value = "low",
            title = "低风险及以上",
            description = "低风险、中风险和高风险事件均可触发告警。",
        ),
        RiskLevelOption(
            value = "medium",
            title = "中风险及以上",
            description = "中风险和高风险事件可以触发告警。",
        ),
        RiskLevelOption(
            value = "high",
            title = "仅高风险",
            description = "只有高风险事件才会触发家庭告警。",
        ),
    )

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "自动触发风险等级",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "只有达到所选风险等级的检测结果，才会自动创建家庭告警。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            riskLevels.forEach { option ->
                RiskLevelOptionRow(
                    option = option,
                    selected = selectedRiskLevel == option.value,
                    enabled = enabled,
                    onClick = {
                        onRiskLevelChange(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun RiskLevelOptionRow(
    option: RiskLevelOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 10.dp,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = option.title,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 风险来源选择。
 */
@Composable
private fun SourceTypeCard(
    enabledSourceTypes: Set<String>,
    enabled: Boolean,
    onSourceTypeToggle: (String) -> Unit,
) {
    val options = listOf(
        SourceTypeOption(
            value = "text",
            title = "文本风险检测",
            description = "聊天记录、短信和其他文本内容。",
        ),
        SourceTypeOption(
            value = "image",
            title = "图片风险检测",
            description = "聊天截图、转账截图和其他图片内容。",
        ),
        SourceTypeOption(
            value = "url",
            title = "链接风险检测",
            description = "网页链接、登录链接和付款链接。",
        ),
    )

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "允许触发告警的检测类型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "至少选择一种检测类型。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            options.forEach { option ->
                SourceTypeOptionCard(
                    option = option,
                    selected = option.value in enabledSourceTypes,
                    enabled = enabled,
                    onClick = {
                        onSourceTypeToggle(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceTypeOptionCard(
    option: SourceTypeOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected && enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = option.title,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 最大接收人数设置。
 */
@Composable
private fun MaxRecipientsCard(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "最大告警接收人数",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "系统会按照可信联系人的优先级，选择对应数量的接收人。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                enabled = enabled,
                onValueChange = onValueChange,
                label = {
                    Text(text = "最大接收人数")
                },
                supportingText = {
                    Text(text = "请输入大于 0 的整数。")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
            )
        }
    }
}

/**
 * 保存、撤销和刷新按钮。
 */
@Composable
private fun PolicyActionCard(
    state: FamilyAlertPolicyUiState,
    onSaveClick: () -> Unit,
    onResetClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "保存设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave,
                onClick = onSaveClick,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(20.dp)
                            .height(20.dp),
                        strokeWidth = 2.dp,
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(text = "正在保存")
                } else {
                    Text(text = "保存自动告警设置")
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = state.hasUnsavedChanges &&
                        !state.isSaving &&
                        !state.isLoading,
                onClick = onResetClick,
            ) {
                Text(text = "撤销未保存的修改")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving &&
                        !state.isLoading,
                onClick = onRefreshClick,
            ) {
                Text(
                    text = if (state.isLoading) {
                        "正在刷新"
                    } else {
                        "刷新后端策略"
                    },
                )
            }
        }
    }
}

/**
 * 策略编号和时间信息。
 */
@Composable
private fun PolicyInformationCard(
    familyId: String,
    createdAt: String?,
    updatedAt: String?,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "策略信息",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "家庭编号：$familyId",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "创建时间：${createdAt ?: "暂无"}",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "更新时间：${updatedAt ?: "暂无"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 成功或错误提示卡片。
 */
@Composable
private fun PolicyMessageCard(
    title: String,
    message: String,
    isError: Boolean,
) {
    val containerColor =
        if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }

    val contentColor =
        if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

/**
 * 首次加载状态。
 */
@Composable
private fun InitialLoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()

        Text(
            text = "正在读取家庭告警设置……",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private data class RiskLevelOption(
    val value: String,
    val title: String,
    val description: String,
)

private data class SourceTypeOption(
    val value: String,
    val title: String,
    val description: String,
)