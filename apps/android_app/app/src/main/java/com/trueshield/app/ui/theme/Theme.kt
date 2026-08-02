package com.trueshield.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** 用户可选的外观模式。 */
enum class TrueShieldThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

private val LightColorScheme = lightColorScheme(
    primary = TrueShieldBlue,
    onPrimary = LightSurface,
    primaryContainer = TrueShieldBlueLight,
    onPrimaryContainer = TrueShieldBlueDark,
    secondary = TrueShieldCyan,
    onSecondary = LightSurface,
    secondaryContainer = TrueShieldCyanLight,
    onSecondaryContainer = ColorTokens.DarkGreenText,
    error = RiskHigh,
    errorContainer = RiskHighContainer,
    onErrorContainer = ColorTokens.DarkRedText,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorTokens.DarkPrimary,
    onPrimary = ColorTokens.DarkPrimaryText,
    primaryContainer = ColorTokens.DarkPrimaryContainer,
    onPrimaryContainer = ColorTokens.DarkPrimaryContainerText,
    secondary = ColorTokens.DarkSecondary,
    onSecondary = ColorTokens.DarkSecondaryText,
    secondaryContainer = ColorTokens.DarkSecondaryContainer,
    onSecondaryContainer = ColorTokens.DarkSecondaryContainerText,
    error = ColorTokens.DarkError,
    errorContainer = ColorTokens.DarkErrorContainer,
    onErrorContainer = ColorTokens.DarkErrorContainerText,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
)

/**
 * 全局主题：固定使用真信盾品牌色，避免系统动态取色破坏风险色语义。
 */
@Composable
fun TrueShieldTheme(
    themeMode: TrueShieldThemeMode = TrueShieldThemeMode.SYSTEM,
    elderMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        TrueShieldThemeMode.SYSTEM -> isSystemInDarkTheme()
        TrueShieldThemeMode.LIGHT -> false
        TrueShieldThemeMode.DARK -> true
    }

    val baseColorScheme =
        if (darkTheme) DarkColorScheme else LightColorScheme

    val resolvedColorScheme =
        if (elderMode) {
            baseColorScheme.copy(
                outline =
                    if (darkTheme) {
                        androidx.compose.ui.graphics.Color(0xFFB8C7DA)
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF536276)
                    },
                outlineVariant =
                    if (darkTheme) {
                        androidx.compose.ui.graphics.Color(0xFF8797AB)
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF78879A)
                    },
                onSurfaceVariant =
                    if (darkTheme) {
                        androidx.compose.ui.graphics.Color(0xFFE0E8F2)
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF354255)
                    },
            )
        } else {
            baseColorScheme
        }

    MaterialTheme(
        colorScheme = resolvedColorScheme,
        typography = if (elderMode) ElderTypography else Typography,
        content = content,
    )
}

/** 仅用于集中放置深色模式的高对比度颜色。 */
private object ColorTokens {
    val DarkPrimary = androidx.compose.ui.graphics.Color(0xFF9AC4FF)
    val DarkPrimaryText = androidx.compose.ui.graphics.Color(0xFF002E62)
    val DarkPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF164B88)
    val DarkPrimaryContainerText = androidx.compose.ui.graphics.Color(0xFFD6E8FF)
    val DarkSecondary = androidx.compose.ui.graphics.Color(0xFF67D8C6)
    val DarkSecondaryText = androidx.compose.ui.graphics.Color(0xFF00382F)
    val DarkSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF005146)
    val DarkSecondaryContainerText = androidx.compose.ui.graphics.Color(0xFF8EF5E1)
    val DarkError = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
    val DarkErrorContainer = androidx.compose.ui.graphics.Color(0xFF8C1D20)
    val DarkErrorContainerText = androidx.compose.ui.graphics.Color(0xFFFFDAD6)
    val DarkGreenText = androidx.compose.ui.graphics.Color(0xFF00382F)
    val DarkRedText = androidx.compose.ui.graphics.Color(0xFF65000B)
}
