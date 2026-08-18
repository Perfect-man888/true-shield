package com.trueshield.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DefaultFont = FontFamily.Default

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = DefaultFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/** 长辈模式仅放大字体并提高可读性，不删减功能。 */
val ElderTypography = Typography(
    displaySmall = Typography.displaySmall.copy(
        fontSize = 38.sp,
        lineHeight = 46.sp,
    ),
    headlineLarge = Typography.headlineLarge.copy(
        fontSize = 34.sp,
        lineHeight = 42.sp,
    ),
    headlineMedium = Typography.headlineMedium.copy(
        fontSize = 29.sp,
        lineHeight = 37.sp,
    ),
    titleLarge = Typography.titleLarge.copy(
        fontSize = 27.sp,
        lineHeight = 35.sp,
    ),
    titleMedium = Typography.titleMedium.copy(
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleSmall = Typography.titleSmall.copy(
        fontSize = 19.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = Typography.bodyLarge.copy(
        fontSize = 19.sp,
        lineHeight = 29.sp,
    ),
    bodyMedium = Typography.bodyMedium.copy(
        fontSize = 17.sp,
        lineHeight = 26.sp,
    ),
    bodySmall = Typography.bodySmall.copy(
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = Typography.labelLarge.copy(
        fontSize = 19.sp,
        lineHeight = 27.sp,
    ),
    labelMedium = Typography.labelMedium.copy(
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    labelSmall = Typography.labelSmall.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
