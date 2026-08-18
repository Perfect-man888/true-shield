package com.trueshield.app.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trueshield.app.data.model.family.FamilyDto
import com.trueshield.app.data.model.family.FamilyMemberDto
import com.trueshield.app.data.model.family.ReceivedFamilyInvitationDto
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
import com.trueshield.app.ui.family.common.familyMemberStatusLabel
import com.trueshield.app.ui.family.common.familyRoleLabel
import com.trueshield.app.ui.family.common.fullFamilyDateTime
import com.trueshield.app.ui.onboarding.FirstUseGuideDialog
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.RiskMedium
import com.trueshield.app.ui.theme.RiskMediumContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldCyan

/**
 * 家庭中心：创建家庭、处理邀请、查看成员和发送邀请。
 */
@Composable
fun FamilyCenterScreen(
    state: FamilyCenterUiState,
    onFamilyNameChange: (String) -> Unit,
    onCreateFamilyClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onFamilyClick: (String) -> Unit,
    onInviteeEmailChange: (String) -> Unit,
    onSendInvitationClick: () -> Unit,
    onAcceptInvitationClick: (String) -> Unit,
    onDeclineInvitationClick: (String) -> Unit,
    onCloseMembersClick: () -> Unit,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    FirstUseGuideDialog(
        guideKey = "family_center_guide_v1",
        title = "家庭中心怎么用？",
        description = "家庭用于共享告警处理状态，不会自动共享原始检测内容。",
        steps = listOf(
            "创建家庭或接受可信家人的邀请",
            "邀请成员后设置可信联系人和告警策略",
            "发生高风险事件时共同确认和处理告警",
        ),
    )

    var invitationToDecline by remember {
        mutableStateOf<ReceivedFamilyInvitationDto?>(null)
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    invitationToDecline?.let { invitation ->
        AlertDialog(
            onDismissRequest = {
                invitationToDecline = null
            },
            title = {
                Text(text = "拒绝家庭邀请？")
            },
            text = {
                Text(
                    text = "拒绝后，你不会加入“${invitation.familyName}”。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        invitationToDecline = null
                        onDeclineInvitationClick(invitation.id)
                    },
                ) {
                    Text(
                        text = "确认拒绝",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        invitationToDecline = null
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
                    title = "家庭中心",
                    subtitle = "管理家庭、成员和收到的邀请",
                    onBackClick = onBackClick,
                    onRefreshClick = onRefreshClick,
                    refreshing = state.isRefreshing,
                )
            }

            item {
                FamilyOverviewCard(
                    familyCount = state.totalFamilies,
                    invitationCount = state.receivedInvitationCount,
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

            if (state.receivedInvitations.isNotEmpty()) {
                item {
                    FamilySectionHeader(
                        title = "待处理邀请",
                        subtitle = "确认邀请人和家庭名称后再加入",
                        trailing = {
                            FamilyStatusPill(
                                text = "${state.receivedInvitationCount} 条",
                                foreground = RiskMedium,
                                background = RiskMediumContainer,
                            )
                        },
                    )
                }

                items(
                    items = state.receivedInvitations,
                    key = { invitation -> invitation.id },
                ) { invitation ->
                    InvitationCard(
                        invitation = invitation,
                        isProcessing =
                            state.processingInvitationId == invitation.id,
                        interactionEnabled =
                            !state.isBusy ||
                                state.processingInvitationId == invitation.id,
                        onAcceptClick = {
                            onAcceptInvitationClick(invitation.id)
                        },
                        onDeclineClick = {
                            invitationToDecline = invitation
                        },
                    )
                }
            }

            item {
                CreateFamilyCard(
                    familyName = state.familyNameInput,
                    isCreating = state.isCreatingFamily,
                    canCreate = state.canCreateFamily,
                    onFamilyNameChange = onFamilyNameChange,
                    onCreateClick = onCreateFamilyClick,
                )
            }

            item {
                FamilySectionHeader(
                    title = "我的家庭",
                    subtitle = "选择家庭后可查看成员并发送邀请",
                )
            }

            when {
                state.isInitialLoading -> {
                    item {
                        FamilyLoadingCard(
                            message = "正在读取家庭信息……",
                        )
                    }
                }

                state.families.isEmpty() -> {
                    item {
                        FamilyEmptyState(
                            title = "还没有家庭",
                            message = "在上方输入家庭名称，创建第一个家庭守护空间。",
                            icon = Icons.Default.Home,
                        )
                    }
                }

                else -> {
                    items(
                        items = state.families,
                        key = { family -> family.id },
                    ) { family ->
                        FamilyCard(
                            family = family,
                            selected = family.id == state.selectedFamilyId,
                            enabled = !state.isBusy,
                            onClick = {
                                onFamilyClick(family.id)
                            },
                        )
                    }
                }
            }

            if (state.selectedFamilyId != null) {
                item {
                    SelectedFamilyHeader(
                        familyName = state.selectedFamily?.name.orEmpty(),
                        memberCount = state.selectedFamilyMemberTotal,
                        canInvite = state.selectedFamilyCanInvite,
                        onCloseClick = onCloseMembersClick,
                    )
                }

                if (state.selectedFamilyCanInvite) {
                    item {
                        InviteMemberCard(
                            familyName = state.selectedFamily?.name.orEmpty(),
                            email = state.inviteeEmailInput,
                            isSending = state.isSendingInvitation,
                            canSend = state.canSendInvitation,
                            onEmailChange = onInviteeEmailChange,
                            onSendClick = onSendInvitationClick,
                        )
                    }
                }

                item {
                    FamilySectionHeader(
                        title = "家庭成员",
                        subtitle = "当前家庭共有 ${state.selectedFamilyMemberTotal} 名成员",
                    )
                }

                when {
                    state.isLoadingMembers -> {
                        item {
                            FamilyLoadingCard(
                                message = "正在读取家庭成员……",
                            )
                        }
                    }

                    state.selectedFamilyMembers.isEmpty() -> {
                        item {
                            FamilyEmptyState(
                                title = "暂无成员信息",
                                message = "当前家庭暂时没有可以显示的成员。",
                                icon = Icons.Default.Group,
                            )
                        }
                    }

                    else -> {
                        items(
                            items = state.selectedFamilyMembers,
                            key = { member -> member.id },
                        ) { member ->
                            FamilyMemberCard(member = member)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyOverviewCard(
    familyCount: Int,
    invitationCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(TrueShieldBlue, TrueShieldCyan),
                    ),
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(29.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = "家庭安全共同守护",
                        style = MaterialTheme.typography.titleLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                    Text(
                        text = "成员、邀请和安全联系统一管理",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.86f),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverviewMetric(
                    value = familyCount.toString(),
                    label = "已加入家庭",
                    modifier = Modifier.weight(1f),
                )
                OverviewMetric(
                    value = invitationCount.toString(),
                    label = "待处理邀请",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.84f),
            )
        }
    }
}

@Composable
private fun CreateFamilyCard(
    familyName: String,
    isCreating: Boolean,
    canCreate: Boolean,
    onFamilyNameChange: (String) -> Unit,
    onCreateClick: () -> Unit,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column {
                    Text(
                        text = "创建家庭",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "为家人建立独立的安全守护空间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = familyName,
                onValueChange = onFamilyNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "家庭名称") },
                placeholder = { Text(text = "例如：幸福一家") },
                singleLine = true,
                enabled = !isCreating,
                shape = RoundedCornerShape(16.dp),
            )

            PrimaryActionButton(
                text = if (isCreating) "正在创建……" else "创建家庭",
                onClick = onCreateClick,
                enabled = canCreate,
            )
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: ReceivedFamilyInvitationDto,
    isProcessing: Boolean,
    interactionEnabled: Boolean,
    onAcceptClick: () -> Unit,
    onDeclineClick: () -> Unit,
) {
    val inviterName = invitation.inviterDisplayName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "未设置昵称"

    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = RiskMediumContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = null,
                            tint = RiskMedium,
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invitation.familyName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$inviterName 邀请你加入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FamilyStatusPill(
                    text = "待处理",
                    foreground = RiskMedium,
                    background = RiskMediumContainer,
                )
            }

            FamilyInfoLine(
                label = "加入身份",
                value = familyRoleLabel(invitation.role),
            )
            FamilyInfoLine(
                label = "接收邮箱",
                value = invitation.inviteeEmail,
                icon = Icons.Default.Email,
            )
            FamilyInfoLine(
                label = "过期时间",
                value = fullFamilyDateTime(invitation.expiresAt),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDeclineClick,
                    enabled = interactionEnabled && !isProcessing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "拒绝",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onAcceptClick,
                    enabled = interactionEnabled && !isProcessing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(text = "接受邀请")
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyCard(
    family: FamilyDto,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .border(1.dp, borderColor, shape),
        shape = shape,
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FamilyAvatar(name = family.name)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = family.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = familyRoleLabel(family.myRole),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FamilyStatusPill(
                text = if (selected) "正在查看" else "正常",
                foreground = if (selected) TrueShieldBlue else RiskLow,
                background =
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else RiskLowContainer,
            )
        }
    }
}

@Composable
private fun SelectedFamilyHeader(
    familyName: String,
    memberCount: Int,
    canInvite: Boolean,
    onCloseClick: () -> Unit,
) {
    TrueShieldCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = familyName.ifBlank { "当前家庭" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$memberCount 名成员 · ${if (canInvite) "可邀请成员" else "普通成员权限"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "收起家庭成员",
                )
            }
        }
    }
}

@Composable
private fun InviteMemberCard(
    familyName: String,
    email: String,
    isSending: Boolean,
    canSend: Boolean,
    onEmailChange: (String) -> Unit,
    onSendClick: () -> Unit,
) {
    TrueShieldCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MailOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = "邀请家庭成员",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "加入：$familyName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "成员注册邮箱") },
                placeholder = { Text(text = "member@example.com") },
                supportingText = {
                    Text(text = "${email.length}/$FAMILY_INVITEE_EMAIL_MAX_LENGTH")
                },
                singleLine = true,
                enabled = !isSending,
                shape = RoundedCornerShape(16.dp),
            )

            PrimaryActionButton(
                text = if (isSending) "正在发送邀请……" else "发送家庭邀请",
                onClick = onSendClick,
                enabled = canSend,
            )
        }
    }
}

@Composable
private fun FamilyMemberCard(
    member: FamilyMemberDto,
) {
    val displayName = member.displayName
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: member.email
            ?.substringBefore("@")
            ?.takeIf { it.isNotBlank() }
        ?: "未设置昵称"

    TrueShieldCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FamilyAvatar(
                name = displayName,
                foreground = TrueShieldCyan,
                background = MaterialTheme.colorScheme.secondaryContainer,
            )
            Spacer(Modifier.size(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FamilyStatusPill(
                        text = familyMemberStatusLabel(member.status),
                        foreground = RiskLow,
                        background = RiskLowContainer,
                    )
                }
                Text(
                    text = familyRoleLabel(member.role),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                member.phone
                    ?.takeIf { it.isNotBlank() }
                    ?.let { phone ->
                        FamilyInfoLine(
                            label = "手机号",
                            value = phone,
                            icon = Icons.Default.Phone,
                        )
                    }
                member.email
                    ?.takeIf { it.isNotBlank() }
                    ?.let { email ->
                        FamilyInfoLine(
                            label = "邮箱",
                            value = email,
                            icon = Icons.Default.Email,
                        )
                    }
                FamilyInfoLine(
                    label = "加入时间",
                    value = fullFamilyDateTime(member.joinedAt),
                    icon = Icons.Default.Person,
                )
            }
        }
    }
}
