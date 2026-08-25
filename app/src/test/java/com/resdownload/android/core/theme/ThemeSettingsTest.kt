package com.resdownload.android.core.theme

import androidx.compose.ui.graphics.toArgb
import com.materialkolor.contrast.Contrast
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsTest {
    @Test
    fun defaultsPreserveSystemModeAndDynamicColor() {
        assertEquals(
            ThemeSettings(
                themeMode = ThemeMode.SYSTEM,
                dynamicColorEnabled = true,
                seedColorArgb = DEFAULT_THEME_SEED_ARGB,
            ),
            ThemeSettings(),
        )
    }

    @Test
    fun seedNormalizationAlwaysProducesOpaqueColor() {
        assertEquals(0xFF123456.toInt(), normalizeThemeSeedArgb(0x00123456))
    }

    @Test
    fun generatedSchemesKeepMaterialForegroundContrast() {
        listOf(false, true).forEach { darkTheme ->
            val scheme = seedColorScheme(ThemeSeedPreset.VIOLET.seedColorArgb, darkTheme)

            assertTrue(contrast(scheme.primary.toArgb(), scheme.onPrimary.toArgb()) >= 4.5)
            assertTrue(contrast(scheme.surface.toArgb(), scheme.onSurface.toArgb()) >= 4.5)
            assertTrue(
                contrast(
                    scheme.primaryContainer.toArgb(),
                    scheme.onPrimaryContainer.toArgb(),
                ) >= 3.0,
            )
        }
    }

    @Test
    fun seedAndBrightnessChangeGeneratedRoles() {
        val forest = seedColorScheme(ThemeSeedPreset.FOREST.seedColorArgb, darkTheme = false)
        val coral = seedColorScheme(ThemeSeedPreset.CORAL.seedColorArgb, darkTheme = false)
        val dark = seedColorScheme(ThemeSeedPreset.FOREST.seedColorArgb, darkTheme = true)

        assertNotEquals(forest.primary, coral.primary)
        assertTrue(Hct.fromInt(dark.surface.toArgb()).tone < Hct.fromInt(forest.surface.toArgb()).tone)
    }

    @Test
    fun everyOfficialSchemeVariantGeneratesLightAndDarkRoles() {
        ThemeSchemeVariant.entries.forEach { variant ->
            listOf(false, true).forEach { darkTheme ->
                val scheme = seedColorScheme(
                    ThemeSeedPreset.CORAL.seedColorArgb,
                    darkTheme,
                    variant,
                )
                assertTrue(contrast(scheme.primary.toArgb(), scheme.onPrimary.toArgb()) >= 4.5)
            }
        }
    }

    @Test
    fun fidelityUsesSeedChromaAndTone() {
        val muted = themeSeedFromTone(ThemeTone(hue = 20f, chroma = 20f, tone = 35f))
        val vivid = themeSeedFromTone(ThemeTone(hue = 20f, chroma = 80f, tone = 65f))
        val mutedScheme = seedColorScheme(muted, false, ThemeSchemeVariant.FIDELITY)
        val vividScheme = seedColorScheme(vivid, false, ThemeSchemeVariant.FIDELITY)

        assertNotEquals(mutedScheme.primary, vividScheme.primary)
        assertNotEquals(mutedScheme.primaryContainer, vividScheme.primaryContainer)
    }

    private fun contrast(firstArgb: Int, secondArgb: Int): Double = Contrast.ratioOfTones(
        Hct.fromInt(firstArgb).tone,
        Hct.fromInt(secondArgb).tone,
    )
}
