package com.trueshield.app.ui.family.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trueshield.app.ui.components.TrueShieldCard
import com.trueshield.app.ui.components.TrueShieldEmptyCard
import com.trueshield.app.ui.components.TrueShieldInlineMessage
import com.trueshield.app.ui.components.TrueShieldLoadingCard
import com.trueshield.app.ui.theme.RiskHigh
import com.trueshield.app.ui.theme.RiskHighContainer
import com.trueshield.app.ui.theme.RiskLow
import com.trueshield.app.ui.theme.RiskLowContainer
import com.trueshield.app.ui.theme.TrueShieldBlue
import com.trueshield.app.ui.theme.TrueShieldBlueLight
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun FamilyPageHeader(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    onRefreshClick: (() -> Unit)? = null,
    refreshing: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onRefreshClick != null) {
            IconButton(
                onClick = onRefreshClick,
                enabled = !refreshing,
                modifier = Modifier.size(44.dp),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun FamilySectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.size(10.dp))
            trailing()
        }
    }
}

@Composable
fun FamilyAvatar(
    name: String,
    modifier: Modifier = Modifier,
    foreground: Color = TrueShieldBlue,
    background: Color = TrueShieldBlueLight,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.trim().take(1).ifBlank { "家" }.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = foreground,
            )
        }
    }
}

@Composable
fun FamilyStatusPill(
    text: String,
    foreground: Color,
    background: Color,
) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = background,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 5.dp,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun FamilyInlineMessage(
    title: String,
    message: String,
    isError: Boolean,
) {
    TrueShieldInlineMessage(
        title = title,
        message = message,
        isError = isError,
    )
}

@Composable
fun FamilyLoadingCard(
    message: String,
) {
    TrueShieldLoadingCard(message = message)
}

@Composable
fun FamilyEmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.Inbox,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    TrueShieldEmptyCard(
        title = title,
        description = message,
        icon = icon,
        actionText = actionText,
        onActionClick = onActionClick,
    )
}

@Composable
fun FamilyInfoLine(
    label: String,
    value: String,
    icon: ImageVector? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun familyRoleLabel(role: String): String =
    when (role.trim().lowercase()) {
        "owner" -> "家庭创建者"
        "admin" -> "家庭管理员"
        "member" -> "家庭成员"
        else -> role.ifBlank { "未知角色" }
    }

fun familyMemberStatusLabel(status: String): String =
    when (status.trim().lowercase()) {
        "active" -> "正常"
        "disabled" -> "已停用"
        "pending" -> "待确认"
        else -> status.ifBlank { "未知" }
    }

fun familyAlertStatusLabel(status: String): String =
    when (status.trim().lowercase()) {
        "pending" -> "待确认"
        "acknowledged" -> "处理中"
        "resolved" -> "已完成"
        else -> status.ifBlank { "未知状态" }
    }

@Composable
fun familyAlertStatusColors(status: String): Pair<Color, Color> =
    when (status.trim().lowercase()) {
        "pending" -> RiskHigh to RiskHighContainer
        "acknowledged" -> TrueShieldBlue to TrueShieldBlueLight
        "resolved" -> RiskLow to RiskLowContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant to
            MaterialTheme.colorScheme.surfaceVariant
    }

fun friendlyFamilyDateTime(rawValue: String): String {
    val raw = rawValue.trim()
    if (raw.isBlank()) return "—"

    val instant = runCatching {
        OffsetDateTime.parse(raw).toInstant()
    }.getOrNull() ?: return raw
        .replace("T", " ")
        .removeSuffix("Z")
        .take(16)

    val duration = Duration.between(instant, java.time.Instant.now())
    if (!duration.isNegative) {
        val minutes = max(0L, duration.toMinutes())
        val hours = duration.toHours()
        val days = duration.toDays()
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days == 1L -> "昨天"
            days < 7 -> "${days}天前"
            else -> DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }
    }

    return DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

fun fullFamilyDateTime(rawValue: String?): String {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return "—"
    val instant = runCatching {
        OffsetDateTime.parse(raw).toInstant()
    }.getOrNull() ?: return raw
        .replace("T", " ")
        .removeSuffix("Z")
        .take(16)

    return DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
