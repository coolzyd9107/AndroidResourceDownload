package com.resdownload.android.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.resdownload.android.core.theme.DEFAULT_THEME_SEED_ARGB
import com.resdownload.android.core.theme.ThemeMode
import com.resdownload.android.core.theme.ThemeSchemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferencesTest {
    @Test
    fun missingPreferencesUseBackwardCompatibleDefaults() {
        val settings = emptyPreferences().toThemeSettings()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertTrue(settings.dynamicColorEnabled)
        assertEquals(DEFAULT_THEME_SEED_ARGB, settings.seedColorArgb)
        assertEquals(ThemeSchemeVariant.TONAL_SPOT, settings.schemeVariant)
    }

    @Test
    fun storedAppearanceSettingsRoundTripAndNormalizeAlpha() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("theme_mode") to "DARK",
            booleanPreferencesKey("dynamic_color_enabled") to false,
            intPreferencesKey("seed_color_argb") to 0x00123456,
            stringPreferencesKey("theme_scheme_variant") to "EXPRESSIVE",
        )

        val settings = preferences.toThemeSettings()

        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertFalse(settings.dynamicColorEnabled)
        assertEquals(0xFF123456.toInt(), settings.seedColorArgb)
        assertEquals(ThemeSchemeVariant.EXPRESSIVE, settings.schemeVariant)
    }

    @Test
    fun unknownThemeModeFallsBackToSystem() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("theme_mode") to "UNKNOWN",
        )

        assertEquals(ThemeMode.SYSTEM, preferences.toThemeSettings().themeMode)
    }
}
