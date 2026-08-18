package com.trueshield.app.ui.contact

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.contact.TrustedContactChannelValue
import com.trueshield.app.data.model.contact.TrustedContactDto
import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.ui.components.PrimaryActionButton
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldPageBackground
import com.trueshield.app.ui.family.common.FamilyAvatar
import com.trueshield.app.ui.family.common.FamilyEmptyState
import com.trueshield.app.ui.family.common.FamilyInfoLine
import com.trueshield.app.ui.family.common.FamilyInlineMessage
import com.trueshield.app.ui.family.common.FamilyLoadingCard
import com.trueshield.app.ui.family.common.FamilyPageHeader
import com.trueshield.app.ui.family.common.FamilySectionHeader
import com.trueshield.app.ui.family.common.FamilyStatusPill
import com.trueshield.app.ui.family.common.familyRoleLabel
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight
import com.trueshield.app.ui.theme.TrueShieldCyan
import com.trueshield.app.ui.theme.TrueShieldCyanLight

/**
 * 可信联系人：按家庭管理紧急联系顺序和告警接收状态。
 */
@Composable
fun TrustedContactScreen(
    state: TrustedContactUiState,
    onFamilyClick: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onStartCreateClick: () -> Unit,
    onStartEditClick: (TrustedContactDto) -> Unit,
    onCancelEditClick: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPreferredChannelChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onCanReceiveAlertsChange: (Boolean) -> Unit,
    onSaveContactClick: () -> Unit,
    onToggleAlertsClick: (TrustedContactDto) -> Unit,
    onDisableContactClick: (TrustedContactDto) -> Unit,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var contactToDisable by remember {
        mutableStateOf<TrustedContactDto?>(null)
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    contactToDisable?.let { contact ->
        AlertDialog(
            onDismissRequest = {
                contactToDisable = null
            },
            title = {
                Text(text = "停用可信联系人？")
            },
            text = {
                Text(
                    text = "停用后，“${contact.displayName}”将无法继续接收家庭告警。后端当前没有恢复停用联系人的接口。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contactToDisable = null
                        onDisableContactClick(contact)
                    },
                ) {
                    Text(
                        text = "确认停用",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        contactToDisable = null
                    },
                ) {
                    Text(text = "取消")
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
                    title = "可信联系人",
                    subtitle = "设置紧急情况下优先联系的人",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshing = state.isRefreshing,
                )
            }

            item {
                ContactOverviewCard(
                    familyName = state.selectedFamily?.name,
                    contactCount = state.totalContacts,
                    activeContactCount = state.contacts.count {
                        it.status.equals("active", ignoreCase = true) &&
                            it.canReceiveAlerts
                    },
                )
            }

            state.successMessage
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
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FamilyInlineMessage(
                                title = "操作失败",
                                message = message,
                                isError = true,
                            )
                            OutlinedButton(
                                onClick = onRetryClick,
                                enabled = !state.isBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = "重新加载")
                            }
                        }
                    }
                }

            item {
                FamilySectionHeader(
                    title = "选择家庭",
                    subtitle = "每个家庭可设置不同的可信联系人",
                )
            }

            when {
                state.isInitialLoading -> {
                    item {
                        FamilyLoadingCard(
                            message = "正在读取家庭和联系人……",
                        )
                    }
                }

                state.families.isEmpty() -> {
                    item {
                        FamilyEmptyState(
                            title = "暂无家庭",
                            message = "请先在家庭中心创建或加入家庭。",
                            icon = Icons.Default.Group,
                        )
                    }
                }

                else -> {
                    item {
                        FamilySelector(
                            families = state.families,
                            selectedFamilyId = state.selectedFamilyId,
                            enabled = !state.isBusy,
                            onFamilyClick = onFamilyClick,
                        )
                    }
                }
            }

            if (
                state.selectedFamilyId != null &&
                state.subjectUserId != null
            ) {
                item {
                    ContactEditorCard(
                        state = state,
                        onStartCreateClick = onStartCreateClick,
                        onCancelEditClick = onCancelEditClick,
                        onDisplayNameChange = onDisplayNameChange,
                        onRelationshipChange = onRelationshipChange,
                        onPhoneChange = onPhoneChange,
                        onEmailChange = onEmailChange,
                        onPreferredChannelChange = onPreferredChannelChange,
                        onPriorityChange = onPriorityChange,
                        onCanReceiveAlertsChange = onCanReceiveAlertsChange,
                        onSaveClick = onSaveContactClick,
                    )
                }

                item {
                    FamilySectionHeader(
                        title = "联系人列表",
                        subtitle = "数字越小，告警联系优先级越高",
                        trailing = {
                            FamilyStatusPill(
                                text = "${state.totalContacts} 人",
                                foreground = TrueShieldBlue,
                                background = TrueShieldBlueLight,
                            )
                        },
                    )
                }

                when {
                    state.isLoadingContacts -> {
                        item {
                            FamilyLoadingCard(
                                message = "正在读取可信联系人……",
                            )
                        }
                    }

                    state.contacts.isEmpty() && state.errorMessage == null -> {
                        item {
                            FamilyEmptyState(
                                title = "还没有可信联系人",
                                message = "填写上方信息，添加第一位紧急联系人。",
                                icon = Icons.Default.ContactPhone,
                                actionText = "开始填写",
                                onActionClick = onStartCreateClick,
                            )
                        }
                    }

                    else -> {
                        items(
                            items = state.contacts.sortedBy { it.priority },
                            key = { contact -> contact.id },
                        ) { contact ->
                            TrustedContactCard(
                                contact = contact,
                                isProcessing =
                                    state.processingContactId == contact.id,
                                interactionEnabled =
                                    !state.isBusy ||
                                        state.processingContactId == contact.id,
                                onEditClick = {
                                    onStartEditClick(contact)
                                },
                                onToggleAlertsClick = {
                                    onToggleAlertsClick(contact)
                                },
                                onDisableClick = {
                                    contactToDisable = contact
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactOverviewCard(
    familyName: String?,
    contactCount: Int,
    activeContactCount: Int,
) {
    TrueShieldCard {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(17.dp),
                color = TrueShieldCyanLight,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = null,
                        tint = TrueShieldCyan,
                        modifier = Modifier.size(29.dp),
                    )
                }
            }
            Spacer(Modifier.size(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = familyName?.takeIf { it.isNotBlank() }
                        ?: "请先选择家庭",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$contactCount 位联系人 · $activeContactCount 位可接收告警",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FamilySelector(
    families: List<FamilyDto>,
    selectedFamilyId: String?,
    enabled: Boolean,
    onFamilyClick: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = families,
            key = { family -> family.id },
        ) { family ->
            val selected = family.id == selectedFamilyId
            val shape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier
                    .clip(shape)
                    .clickable(enabled = enabled) {
                        onFamilyClick(family.id)
                    }
                    .border(
                        width = 1.dp,
                        color =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    ),
                shape = shape,
                color =
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 11.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    FamilyAvatar(
                        name = family.name,
                        modifier = Modifier.size(36.dp),
                    )
                    Column {
                        Text(
                            text = family.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                        Text(
                            text = familyRoleLabel(family.myRole),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactEditorCard(
    state: TrustedContactUiState,
    onStartCreateClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPreferredChannelChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onCanReceiveAlertsChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector =
                                if (state.isEditing) Icons.Default.Edit
                                else Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.size(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (state.isEditing) "修改可信联系人"
                            else "新增可信联系人",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.selectedFamily?.name.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!state.isEditing) {
                    TextButton(
                        onClick = onStartCreateClick,
                        enabled = !state.isBusy,
                    ) {
                        Text(text = "清空")
                    }
                }
            }

            OutlinedTextField(
                value = state.displayNameInput,
                onValueChange = onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "联系人姓名") },
                singleLine = true,
                enabled = !state.isBusy,
                shape = RoundedCornerShape(16.dp),
            )

            OutlinedTextField(
                value = state.relationshipInput,
                onValueChange = onRelationshipChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "与我的关系") },
                placeholder = { Text(text = "例如：父亲、女儿、朋友") },
                singleLine = true,
                enabled = !state.isBusy,
                shape = RoundedCornerShape(16.dp),
            )

            OutlinedTextField(
                value = state.phoneInput,
                onValueChange = onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "手机号") },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                ),
                singleLine = true,
                enabled = !state.isBusy,
                shape = RoundedCornerShape(16.dp),
            )

            OutlinedTextField(
                value = state.emailInput,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "邮箱") },
                leadingIcon = {
                    Icon(Icons.Default.AlternateEmail, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                ),
                singleLine = true,
                enabled = !state.isBusy,
                shape = RoundedCornerShape(16.dp),
            )

            Text(
                text = "首选联系渠道",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChannelButton(
                    text = "电话",
                    icon = Icons.Default.Phone,
                    selected =
                        state.preferredChannel == TrustedContactChannelValue.PHONE,
                    enabled = !state.isBusy,
                    onClick = {
                        onPreferredChannelChange(TrustedContactChannelValue.PHONE)
                    },
                    modifier = Modifier.weight(1f),
                )
                ChannelButton(
                    text = "邮箱",
                    icon = Icons.Default.Email,
                    selected =
                        state.preferredChannel == TrustedContactChannelValue.EMAIL,
                    enabled = !state.isBusy,
                    onClick = {
                        onPreferredChannelChange(TrustedContactChannelValue.EMAIL)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.priorityInput,
                onValueChange = onPriorityChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "联系优先级") },
                supportingText = {
                    Text(text = "数字越小，联系优先级越高。")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                singleLine = true,
                enabled = !state.isBusy,
                shape = RoundedCornerShape(16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "允许接收家庭告警",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "关闭后保留资料，但不发送告警。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.canReceiveAlertsInput,
                    onCheckedChange = onCanReceiveAlertsChange,
                    enabled = !state.isBusy,
                )
            }

            PrimaryActionButton(
                text = when {
                    state.isSavingContact -> "正在保存……"
                    state.isEditing -> "保存联系人修改"
                    else -> "创建可信联系人"
                },
                onClick = onSaveClick,
                enabled = state.canSubmit,
            )

            if (state.isEditing) {
                OutlinedButton(
                    onClick = onCancelEditClick,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "取消修改")
                }
            }
        }
    }
}

@Composable
private fun ChannelButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(text = text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(text = text)
        }
    }
}

@Composable
private fun TrustedContactCard(
    contact: TrustedContactDto,
    isProcessing: Boolean,
    interactionEnabled: Boolean,
    onEditClick: () -> Unit,
    onToggleAlertsClick: () -> Unit,
    onDisableClick: () -> Unit,
) {
    val isActive = contact.status.equals("active", ignoreCase = true)
    val statusColor = if (isActive) RiskLow else MaterialTheme.colorScheme.outline
    val statusBackground =
        if (isActive) RiskLowContainer else MaterialTheme.colorScheme.surfaceVariant

    TrueShieldCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(statusColor),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FamilyAvatar(
                        name = contact.displayName,
                        foreground = if (isActive) TrueShieldCyan else Color.Gray,
                        background =
                            if (isActive) TrueShieldCyanLight
                            else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${contact.relationshipLabel} · 优先级 ${contact.priority}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FamilyStatusPill(
                        text =
                            if (!isActive) "已停用"
                            else if (contact.canReceiveAlerts) "可接收告警"
                            else "告警已暂停",
                        foreground =
                            if (isActive && contact.canReceiveAlerts) RiskLow
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        background = statusBackground,
                    )
                }

                contact.phone
                    ?.takeIf { it.isNotBlank() }
                    ?.let { phone ->
                        FamilyInfoLine(
                            label = "手机号",
                            value = phone,
                            icon = Icons.Default.Phone,
                        )
                    }
                contact.email
                    ?.takeIf { it.isNotBlank() }
                    ?.let { email ->
                        FamilyInfoLine(
                            label = "邮箱",
                            value = email,
                            icon = Icons.Default.Email,
                        )
                    }
                FamilyInfoLine(
                    label = "首选渠道",
                    value =
                        if (contact.preferredChannel == TrustedContactChannelValue.EMAIL) {
                            "邮箱"
                        } else {
                            "电话"
                        },
                )

                if (isProcessing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "正在处理……",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isActive) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onEditClick,
                            enabled = interactionEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(5.dp))
                            Text(text = "修改")
                        }
                        OutlinedButton(
                            onClick = onToggleAlertsClick,
                            enabled = interactionEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector =
                                    if (contact.canReceiveAlerts) {
                                        Icons.Default.NotificationsOff
                                    } else {
                                        Icons.Default.NotificationsActive
                                    },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(5.dp))
                            Text(
                                text =
                                    if (contact.canReceiveAlerts) "暂停告警"
                                    else "恢复告警",
                            )
                        }
                    }
                    TextButton(
                        onClick = onDisableClick,
                        enabled = interactionEnabled,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = RiskHigh,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            text = "停用联系人",
                            color = RiskHigh,
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = RiskHighContainer.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = "该联系人已停用，目前后端不支持恢复。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}
