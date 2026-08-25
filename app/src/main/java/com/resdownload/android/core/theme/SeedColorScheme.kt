package com.resdownload.android.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

internal fun seedColorScheme(
    seedColorArgb: Int,
    darkTheme: Boolean,
    variant: ThemeSchemeVariant = ThemeSchemeVariant.TONAL_SPOT,
): ColorScheme {
    val source = Hct.fromInt(normalizeThemeSeedArgb(seedColorArgb))
    val scheme = when (variant) {
        ThemeSchemeVariant.TONAL_SPOT -> SchemeTonalSpot(source, darkTheme, 0.0)
        ThemeSchemeVariant.FIDELITY -> SchemeFidelity(source, darkTheme, 0.0)
        ThemeSchemeVariant.MONOCHROME -> SchemeMonochrome(source, darkTheme, 0.0)
        ThemeSchemeVariant.NEUTRAL -> SchemeNeutral(source, darkTheme, 0.0)
        ThemeSchemeVariant.VIBRANT -> SchemeVibrant(source, darkTheme, 0.0)
        ThemeSchemeVariant.EXPRESSIVE -> SchemeExpressive(source, darkTheme, 0.0)
        ThemeSchemeVariant.CONTENT -> SchemeContent(source, darkTheme, 0.0)
        ThemeSchemeVariant.RAINBOW -> SchemeRainbow(source, darkTheme, 0.0)
    }
    val colors = MaterialDynamicColors()
    return lightColorScheme(
        primary = colors.primary().resolve(scheme),
        onPrimary = colors.onPrimary().resolve(scheme),
        primaryContainer = colors.primaryContainer().resolve(scheme),
        onPrimaryContainer = colors.onPrimaryContainer().resolve(scheme),
        inversePrimary = colors.inversePrimary().resolve(scheme),
        secondary = colors.secondary().resolve(scheme),
        onSecondary = colors.onSecondary().resolve(scheme),
        secondaryContainer = colors.secondaryContainer().resolve(scheme),
        onSecondaryContainer = colors.onSecondaryContainer().resolve(scheme),
        tertiary = colors.tertiary().resolve(scheme),
        onTertiary = colors.onTertiary().resolve(scheme),
        tertiaryContainer = colors.tertiaryContainer().resolve(scheme),
        onTertiaryContainer = colors.onTertiaryContainer().resolve(scheme),
        background = colors.background().resolve(scheme),
        onBackground = colors.onBackground().resolve(scheme),
        surface = colors.surface().resolve(scheme),
        onSurface = colors.onSurface().resolve(scheme),
        surfaceVariant = colors.surfaceVariant().resolve(scheme),
        onSurfaceVariant = colors.onSurfaceVariant().resolve(scheme),
        surfaceTint = colors.surfaceTint().resolve(scheme),
        inverseSurface = colors.inverseSurface().resolve(scheme),
        inverseOnSurface = colors.inverseOnSurface().resolve(scheme),
        error = colors.error().resolve(scheme),
        onError = colors.onError().resolve(scheme),
        errorContainer = colors.errorContainer().resolve(scheme),
        onErrorContainer = colors.onErrorContainer().resolve(scheme),
        outline = colors.outline().resolve(scheme),
        outlineVariant = colors.outlineVariant().resolve(scheme),
        scrim = colors.scrim().resolve(scheme),
        surfaceBright = colors.surfaceBright().resolve(scheme),
        surfaceDim = colors.surfaceDim().resolve(scheme),
        surfaceContainer = colors.surfaceContainer().resolve(scheme),
        surfaceContainerHigh = colors.surfaceContainerHigh().resolve(scheme),
        surfaceContainerHighest = colors.surfaceContainerHighest().resolve(scheme),
        surfaceContainerLow = colors.surfaceContainerLow().resolve(scheme),
        surfaceContainerLowest = colors.surfaceContainerLowest().resolve(scheme),
        primaryFixed = colors.primaryFixed().resolve(scheme),
        primaryFixedDim = colors.primaryFixedDim().resolve(scheme),
        onPrimaryFixed = colors.onPrimaryFixed().resolve(scheme),
        onPrimaryFixedVariant = colors.onPrimaryFixedVariant().resolve(scheme),
        secondaryFixed = colors.secondaryFixed().resolve(scheme),
        secondaryFixedDim = colors.secondaryFixedDim().resolve(scheme),
        onSecondaryFixed = colors.onSecondaryFixed().resolve(scheme),
        onSecondaryFixedVariant = colors.onSecondaryFixedVariant().resolve(scheme),
        tertiaryFixed = colors.tertiaryFixed().resolve(scheme),
        tertiaryFixedDim = colors.tertiaryFixedDim().resolve(scheme),
        onTertiaryFixed = colors.onTertiaryFixed().resolve(scheme),
        onTertiaryFixedVariant = colors.onTertiaryFixedVariant().resolve(scheme),
    )
}

private fun DynamicColor.resolve(scheme: DynamicScheme): Color = Color(getArgb(scheme))
