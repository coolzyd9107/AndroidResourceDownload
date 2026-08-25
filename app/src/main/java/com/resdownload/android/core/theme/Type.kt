package com.resdownload.android.core.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DefaultTypography = Typography()

private val BaseTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(letterSpacing = 0.sp),
    displayMedium = DefaultTypography.displayMedium.copy(letterSpacing = 0.sp),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = DefaultTypography.headlineLarge.copy(letterSpacing = 0.sp),
    headlineMedium = DefaultTypography.headlineMedium.copy(letterSpacing = 0.sp),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = DefaultTypography.titleSmall.copy(letterSpacing = 0.sp),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = DefaultTypography.bodySmall.copy(letterSpacing = 0.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = DefaultTypography.labelSmall.copy(letterSpacing = 0.sp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val AppTypography = BaseTypography.copy(
    displayLargeEmphasized = BaseTypography.displayLarge.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.sp,
    ),
    displayMediumEmphasized = BaseTypography.displayMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.sp,
    ),
    displaySmallEmphasized = BaseTypography.displaySmall.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.sp,
    ),
    headlineLargeEmphasized = BaseTypography.headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineMediumEmphasized = BaseTypography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineSmallEmphasized = BaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    titleLargeEmphasized = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    titleMediumEmphasized = BaseTypography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    titleSmallEmphasized = BaseTypography.titleSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    bodyLargeEmphasized = BaseTypography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    bodyMediumEmphasized = BaseTypography.bodyMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    bodySmallEmphasized = BaseTypography.bodySmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    labelLargeEmphasized = BaseTypography.labelLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    labelMediumEmphasized = BaseTypography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    labelSmallEmphasized = BaseTypography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
)
