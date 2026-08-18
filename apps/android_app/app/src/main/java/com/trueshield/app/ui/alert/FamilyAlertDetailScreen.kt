package com.trueshield.app.ui.alert

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.alert.AlertDeliveryAttemptResponse
import com.trueshield.app.data.model.alert.FamilyAlertRecipientResponse
import com.trueshield.app.data.model.alert.FamilyAlertResponse
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.RiskLevelBadge
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.family.common.FamilyEmptyState
import com.trueshield.app.ui.family.common.FamilyInfoLine
import com.trueshield.app.ui.family.common.FamilyInlineMessage
import com.trueshield.app.ui.family.common.FamilyLoadingCard
import com.trueshield.app.ui.family.common.FamilyPageHeader
import com.trueshield.app.ui.family.common.FamilySectionHeader
import com.trueshield.app.ui.family.common.FamilyStatusPill
import com.trueshield.app.ui.family.common.familyAlertStatusColors
import com.trueshield.app.ui.family.common.familyAlertStatusLabel
import com.trueshield.app.ui.family.common.fullFamilyDateTime
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.RiskMedium
import com.trueshield.app.ui.theme.RiskMediumContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight

/**
 * 家庭告警详情与处理流程。
 */
@Composable
fun FamilyAlertDetailScreen(
    state: FamilyAlertDetailUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onRetry: () -> Unit,
    onAcknowledgeClick: () -> Unit,
    onDispatchClick: () -> Unit,
    onRetryFailedClick: () -> Unit,
    onResolutionNoteChange: (String) -> Unit,
    onResolveClick: () -> Unit,
) {
    var technicalExpanded by remember { mutableStateOf(false) }
    var showResolveConfirmation by remember { mutableStateOf(false) }

    if (showResolveConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showResolveConfirmation = false
            },
            title = {
                Text(text = "确认完成告警处理？")
            },
            text = {
                Text(
                    text = "完成后该告警将进入“已完成”状态，并保留当前处理说明。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResolveConfirmation = false
                        onResolveClick()
                    },
                ) {
                    Text(text = "确认完成")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResolveConfirmation = false
                    },
                ) {
                    Text(text = "继续处理")
                }
            },
        )
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
                    title = "告警详情",
                    subtitle = "核实风险、通知家人并记录处理结果",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshing = state.isLoading,
                )
            }

            when {
                state.isLoading && state.alert == null -> {
                    item {
                        FamilyLoadingCard(
                            message = "正在读取告警详情……",
                        )
                    }
                }

                state.alert == null -> {
                    item {
                        FamilyInlineMessage(
                            title = "无法读取告警",
                            message = state.errorMessage ?: "告警不存在或无权查看。",
                            isError = true,
                        )
                    }
                    item {
                        PrimaryActionButton(
                            text = "重新加载",
                            onClick = onRetry,
                        )
                    }
                }

                else -> {
                    val alert = requireNotNull(state.alert)

                    item {
                        AlertHeroCard(alert = alert)
                    }

                    state.operationMessage
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
                                FamilyInlineMessage(
                                    title = "操作失败",
                                    message = message,
                                    isError = true,
                                )
                            }
                        }

                    item {
                        FamilySectionHeader(
                            title = "处理进度",
                            subtitle = "每一步都会保留时间记录",
                        )
                    }
                    item {
                        AlertTimeline(alert = alert)
                    }

                    item {
                        FamilySectionHeader(
                            title = "当前操作",
                            subtitle = operationSubtitle(alert.status),
                        )
                    }
                    item {
                        AlertOperationCard(
                            alert = alert,
                            state = state,
                            onAcknowledgeClick = onAcknowledgeClick,
                            onDispatchClick = onDispatchClick,
                            onRetryFailedClick = onRetryFailedClick,
                            onResolutionNoteChange = onResolutionNoteChange,
                            onResolveClick = {
                                showResolveConfirmation = true
                            },
                        )
                    }

                    item {
                        FamilySectionHeader(
                            title = "告警接收人",
                            subtitle = "共 ${alert.recipients.size} 位联系人",
                        )
                    }

                    if (alert.recipients.isEmpty()) {
                        item {
                            FamilyEmptyState(
                                title = "暂无接收人",
                                message = "请先在可信联系人中设置可接收告警的人。",
                                icon = Icons.Default.NotificationsActive,
                            )
                        }
                    } else {
                        items(
                            items = alert.recipients,
                            key = { recipient -> recipient.id },
                        ) { recipient ->
                            RecipientCard(recipient = recipient)
                        }
                    }

                    item {
                        FamilySectionHeader(
                            title = "发送记录",
                            subtitle = "查看每次通知的渠道和结果",
                        )
                    }

                    when {
                        state.isLoadingDeliveryAttempts -> {
                            item {
                                FamilyLoadingCard(
                                    message = "正在读取发送记录……",
                                )
                            }
                        }

                        !state.deliveryAttemptsErrorMessage.isNullOrBlank() -> {
                            item {
                                FamilyInlineMessage(
                                    title = "发送记录加载失败",
                                    message = state.deliveryAttemptsErrorMessage.orEmpty(),
                                    isError = true,
                                )
                            }
                        }

                        state.deliveryAttempts.isEmpty() -> {
                            item {
                                FamilyEmptyState(
                                    title = "暂无发送记录",
                                    message = "发送家庭告警后，投递结果会显示在这里。",
                                    icon = Icons.Default.History,
                                )
                            }
                        }

                        else -> {
                            items(
                                items = state.deliveryAttempts,
                                key = { attempt -> attempt.id },
                            ) { attempt ->
                                DeliveryAttemptCard(attempt = attempt)
                            }
                        }
                    }

                    item {
                        TechnicalDetailCard(
                            alert = alert,
                            expanded = technicalExpanded,
                            onToggle = {
                                technicalExpanded = !technicalExpanded
                            },
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = onRefreshClick,
                            enabled = !state.isLoading && !state.isOperating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(text = "刷新告警详情")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertHeroCard(
    alert: FamilyAlertResponse,
) {
    val (statusColor, statusBackground) =
        familyAlertStatusColors(alert.status)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color =
            when (alert.riskLevel.trim().lowercase()) {
                "high" -> RiskHighContainer
                "medium" -> RiskMediumContainer
                else -> RiskLowContainer
            },
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RiskLevelBadge(riskLevel = alert.riskLevel)
                Spacer(Modifier.size(8.dp))
                FamilyStatusPill(
                    text = familyAlertStatusLabel(alert.status),
                    foreground = statusColor,
                    background = statusBackground,
                )
            }

            Text(
                text = alert.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = alert.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "创建于 ${fullFamilyDateTime(alert.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlertTimeline(
    alert: FamilyAlertResponse,
) {
    val acknowledged =
        !alert.acknowledgedAt.isNullOrBlank() ||
            alert.status.equals("acknowledged", ignoreCase = true) ||
            alert.status.equals("resolved", ignoreCase = true)
    val resolved =
        !alert.resolvedAt.isNullOrBlank() ||
            alert.status.equals("resolved", ignoreCase = true)

    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            TimelineStep(
                title = "告警已创建",
                description = fullFamilyDateTime(alert.createdAt),
                completed = true,
                active = !acknowledged,
            )
            TimelineStep(
                title = "家庭成员已确认",
                description =
                    if (acknowledged) fullFamilyDateTime(alert.acknowledgedAt)
                    else "等待家庭成员查看",
                completed = acknowledged,
                active = acknowledged && !resolved,
            )
            TimelineStep(
                title = "告警处理完成",
                description =
                    if (resolved) fullFamilyDateTime(alert.resolvedAt)
                    else "完成核实后填写处理说明",
                completed = resolved,
                active = resolved,
            )
        }
    }
}

@Composable
private fun TimelineStep(
    title: String,
    description: String,
    completed: Boolean,
    active: Boolean,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = when {
                completed -> RiskLowContainer
                active -> TrueShieldBlueLight
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (completed) Icons.Default.Check else Icons.Default.PendingActions,
                    contentDescription = null,
                    tint = when {
                        completed -> RiskLow
                        active -> TrueShieldBlue
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlertOperationCard(
    alert: FamilyAlertResponse,
    state: FamilyAlertDetailUiState,
    onAcknowledgeClick: () -> Unit,
    onDispatchClick: () -> Unit,
    onRetryFailedClick: () -> Unit,
    onResolutionNoteChange: (String) -> Unit,
    onResolveClick: () -> Unit,
) {
    val status = alert.status.trim().lowercase()
    val hasPendingRecipient = alert.recipients.any {
        it.deliveryStatus.equals("pending", ignoreCase = true)
    }
    val hasFailedRecipient = alert.recipients.any {
        it.deliveryStatus.equals("failed", ignoreCase = true)
    }

    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (status) {
                "pending" -> {
                    OperationHeading(
                        icon = Icons.Default.WarningAmber,
                        title = "确认已经查看告警",
                        message = "确认后表示你已经开始核实风险并联系家人。",
                        iconColor = RiskHigh,
                        iconBackground = RiskHighContainer,
                    )
                    PrimaryActionButton(
                        text =
                            if (state.isOperating) "正在确认……"
                            else "确认并开始处理",
                        onClick = onAcknowledgeClick,
                        enabled = !state.isOperating,
                    )
                }

                "acknowledged" -> {
                    OperationHeading(
                        icon = Icons.Default.PendingActions,
                        title = "完成安全核实",
                        message = "通知可信联系人，并记录最终处理结果。",
                        iconColor = TrueShieldBlue,
                        iconBackground = TrueShieldBlueLight,
                    )

                    if (alert.recipients.isEmpty()) {
                        FamilyInlineMessage(
                            title = "暂无可信联系人",
                            message = "当前告警没有接收人，仍可完成线下核实并记录处理说明。",
                            isError = true,
                        )
                    } else if (hasPendingRecipient) {
                        Button(
                            onClick = onDispatchClick,
                            enabled = !state.isOperating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text =
                                    if (state.isOperating) "正在发送……"
                                    else "发送家庭告警",
                            )
                        }
                    }

                    if (hasFailedRecipient) {
                        OutlinedButton(
                            onClick = onRetryFailedClick,
                            enabled = !state.isOperating,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text =
                                    if (state.isOperating) "正在重试……"
                                    else "重试发送失败通知",
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.resolutionNote,
                        onValueChange = onResolutionNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "处理说明") },
                        placeholder = {
                            Text(text = "例如：已联系本人核实，未发生转账，并完成安全提醒。")
                        },
                        supportingText = {
                            Text(text = "${state.resolutionNote.length}/1000")
                        },
                        minLines = 4,
                        maxLines = 8,
                        enabled = !state.isOperating,
                        shape = RoundedCornerShape(16.dp),
                    )

                    PrimaryActionButton(
                        text =
                            if (state.isOperating) "正在完成……"
                            else "完成告警处理",
                        onClick = onResolveClick,
                        enabled =
                            !state.isOperating &&
                                state.resolutionNote.isNotBlank(),
                    )
                }

                "resolved" -> {
                    OperationHeading(
                        icon = Icons.Default.TaskAlt,
                        title = "告警已经处理完成",
                        message = alert.resolutionNote
                            ?.takeIf { it.isNotBlank() }
                            ?: "本次风险已经完成核实与处置。",
                        iconColor = RiskLow,
                        iconBackground = RiskLowContainer,
                    )
                }

                else -> {
                    FamilyInlineMessage(
                        title = "未知告警状态",
                        message = "服务器返回状态：${alert.status}",
                        isError = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationHeading(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    iconColor: Color,
    iconBackground: Color,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = iconBackground,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecipientCard(
    recipient: FamilyAlertRecipientResponse,
) {
    val (foreground, background) =
        deliveryStatusColors(recipient.deliveryStatus)

    TrueShieldCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = recipient.recipientName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FamilyStatusPill(
                    text = deliveryStatusLabel(recipient.deliveryStatus),
                    foreground = foreground,
                    background = background,
                )
            }

            FamilyInfoLine(
                label = "通知渠道",
                value = channelLabel(recipient.channel),
            )
            FamilyInfoLine(
                label = "接收地址",
                value = recipient.destination?.takeIf { it.isNotBlank() } ?: "—",
            )
            recipient.sentAt
                ?.takeIf { it.isNotBlank() }
                ?.let { sentAt ->
                    FamilyInfoLine(
                        label = "发送时间",
                        value = fullFamilyDateTime(sentAt),
                    )
                }
            recipient.failureReason
                ?.takeIf { it.isNotBlank() }
                ?.let { reason ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RiskHighContainer,
                    ) {
                        Text(
                            text = "失败原因：$reason",
                            modifier = Modifier.padding(11.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
        }
    }
}

@Composable
private fun DeliveryAttemptCard(
    attempt: AlertDeliveryAttemptResponse,
) {
    val (foreground, background) =
        deliveryStatusColors(attempt.status)

    TrueShieldCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 ${attempt.attemptNumber} 次发送",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                FamilyStatusPill(
                    text = deliveryStatusLabel(attempt.status),
                    foreground = foreground,
                    background = background,
                )
            }
            FamilyInfoLine(
                label = "渠道",
                value = channelLabel(attempt.channel),
            )
            FamilyInfoLine(
                label = "服务提供方",
                value = providerLabel(attempt.provider),
            )
            FamilyInfoLine(
                label = "接收地址",
                value = attempt.destination?.takeIf { it.isNotBlank() } ?: "—",
            )
            FamilyInfoLine(
                label = "尝试时间",
                value = fullFamilyDateTime(attempt.attemptedAt),
            )
            attempt.failureReason
                ?.takeIf { it.isNotBlank() }
                ?.let { reason ->
                    Text(
                        text = "失败原因：$reason",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiskHigh,
                    )
                }
        }
    }
}

@Composable
private fun TechnicalDetailCard(
    alert: FamilyAlertResponse,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    TrueShieldCard(
        onClick = onToggle,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "技术信息",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (expanded) "点击收起" else "事件编号等信息默认折叠",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector =
                        if (expanded) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }

            if (expanded) {
                FamilyInfoLine(label = "告警编号", value = alert.id)
                FamilyInfoLine(label = "风险事件编号", value = alert.riskEventId)
                FamilyInfoLine(label = "家庭编号", value = alert.familyId)
                FamilyInfoLine(label = "被保护用户编号", value = alert.subjectUserId)
                FamilyInfoLine(label = "创建时间", value = fullFamilyDateTime(alert.createdAt))
                FamilyInfoLine(label = "更新时间", value = fullFamilyDateTime(alert.updatedAt))
            }
        }
    }
}

@Composable
private fun deliveryStatusColors(status: String): Pair<Color, Color> =
    when (status.trim().lowercase()) {
        "sent", "delivered", "success" -> RiskLow to RiskLowContainer
        "failed" -> RiskHigh to RiskHighContainer
        "pending" -> RiskMedium to RiskMediumContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant to
            MaterialTheme.colorScheme.surfaceVariant
    }

private fun deliveryStatusLabel(status: String): String =
    when (status.trim().lowercase()) {
        "pending" -> "等待发送"
        "sent" -> "已发送"
        "delivered" -> "已送达"
        "failed" -> "发送失败"
        "skipped" -> "已跳过"
        else -> status.ifBlank { "未知" }
    }

private fun channelLabel(channel: String): String =
    when (channel.trim().lowercase()) {
        "phone", "sms" -> "短信 / 电话"
        "email" -> "邮箱"
        "push" -> "应用推送"
        else -> channel.ifBlank { "未知渠道" }
    }

private fun providerLabel(provider: String): String =
    when (provider.trim().lowercase()) {
        "simulated" -> "开发模拟服务"
        "firebase", "fcm" -> "Firebase 推送"
        else -> provider.ifBlank { "未知服务" }
    }

private fun operationSubtitle(status: String): String =
    when (status.trim().lowercase()) {
        "pending" -> "先确认告警，再开始核实风险"
        "acknowledged" -> "通知家人并填写处理说明"
        "resolved" -> "本次告警已经完成"
        else -> "请根据当前状态完成处置"
    }
