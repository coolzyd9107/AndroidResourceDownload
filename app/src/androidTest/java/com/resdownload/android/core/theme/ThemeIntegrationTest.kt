package com.resdownload.android.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeIntegrationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledDynamicColorUsesSelectedSeedInLightMode() {
        val seed = ThemeSeedPreset.CORAL.seedColorArgb
        val variant = ThemeSchemeVariant.EXPRESSIVE
        var actualPrimary = Color.Unspecified
        composeRule.setContent {
            AndroidResourceDownloadTheme(
                themeMode = ThemeMode.LIGHT,
                dynamicColorEnabled = false,
                seedColorArgb = seed,
                schemeVariant = variant,
            ) {
                val primary = MaterialTheme.colorScheme.primary
                SideEffect { actualPrimary = primary }
            }
        }

        composeRule.runOnIdle {
            assertEquals(
                seedColorScheme(seed, darkTheme = false, variant = variant).primary,
                actualPrimary,
            )
        }
    }

    @Test
    fun disabledDynamicColorUsesSelectedSeedInDarkMode() {
        val seed = ThemeSeedPreset.INDIGO.seedColorArgb
        var actualSurface = Color.Unspecified
        composeRule.setContent {
            AndroidResourceDownloadTheme(
                themeMode = ThemeMode.DARK,
                dynamicColorEnabled = false,
                seedColorArgb = seed,
            ) {
                val surface = MaterialTheme.colorScheme.surface
                SideEffect { actualSurface = surface }
            }
        }

        composeRule.runOnIdle {
            assertEquals(seedColorScheme(seed, darkTheme = true).surface, actualSurface)
        }
    }
}
