package link.mczihan.androidResourceDownload.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import link.mczihan.androidResourceDownload.core.theme.ThemeSeedPreset
import link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noticeItemOpensNoticeContent() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    noticeState = NoticeUiState.Content("测试公告正文"),
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("公告").performClick()
        composeRule.onNodeWithText("测试公告正文").assertExists()
    }

    @Test
    fun checkUpdateItemStartsVersionCheck() {
        var checked = false
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    onCheckUpdate = { checked = true },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("检查更新").performClick()

        composeRule.runOnIdle { assertTrue(checked) }
    }

    @Test
    fun availableUpdateCanOpenDownloadUrl() {
        var openedUrl: String? = null
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    updateState = UpdateUiState.Available(
                        currentVersion = "2.0.0",
                        latestVersion = "2.1.1",
                        updateUrl = "https://example.com/download",
                    ),
                    onDismissUpdate = { dismissed = true },
                    onOpenUpdateUrl = {
                        openedUrl = it
                        true
                    },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("发现新版本").assertExists()
        composeRule.onNodeWithText("下载").performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertEquals("https://example.com/download", openedUrl)
        }
    }

    @Test
    fun cancellingAvailableUpdateOnlyDismissesDialog() {
        var openedUrl: String? = null
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    updateState = UpdateUiState.Available(
                        currentVersion = "2.0.0",
                        latestVersion = "2.0.1",
                        updateUrl = "https://example.com/download",
                    ),
                    onDismissUpdate = { dismissed = true },
                    onOpenUpdateUrl = {
                        openedUrl = it
                        true
                    },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("取消").performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertNull(openedUrl)
        }
    }

    @Test
    fun failedBrowserLaunchKeepsAvailableUpdateDialog() {
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    updateState = UpdateUiState.Available(
                        currentVersion = "2.0.0",
                        latestVersion = "2.1.0",
                        updateUrl = "https://example.com/download",
                    ),
                    onDismissUpdate = { dismissed = true },
                    onOpenUpdateUrl = { false },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("下载").performClick()

        composeRule.runOnIdle { assertEquals(false, dismissed) }
        composeRule.onNodeWithText("发现新版本").assertExists()
    }

    @Test
    fun upToDateStateOnlyShowsLatestMessage() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    updateState = UpdateUiState.UpToDate("2.0.0"),
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("已是最新版本").assertExists()
        composeRule.onNodeWithText("下载").assertDoesNotExist()
    }

    @Test
    fun dynamicColorHidesManualPaletteAndCanBeDisabled() {
        var enabled: Boolean? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    dynamicColorEnabled = true,
                    dynamicColorAvailable = true,
                    onDynamicColorEnabledChange = { enabled = it },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("主题色彩").assertDoesNotExist()
        composeRule.onNode(hasContentDescription("莫奈自动取色开关"))
            .assertIsOn()
            .performClick()

        composeRule.runOnIdle { assertEquals(false, enabled) }
    }

    @Test
    fun manualPaletteShowsSelectedPresetAndChangesSeed() {
        var selectedSeed: Int? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.DARK,
                    onThemeModeChange = {},
                    dynamicColorEnabled = false,
                    themeSeedColorArgb = ThemeSeedPreset.FOREST.seedColorArgb,
                    onThemeSeedColorChange = { selectedSeed = it },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("主题色彩").assertExists()
        composeRule.onNode(hasContentDescription("主题色 森林")).assertIsSelected()
        composeRule.onNode(hasContentDescription("主题色 靛蓝")).performClick()

        composeRule.runOnIdle {
            assertEquals(ThemeSeedPreset.INDIGO.seedColorArgb, selectedSeed)
        }
    }

    @Test
    fun manualPaletteExposesOfficialSchemesResetAndCustomControls() {
        var selectedVariant: ThemeSchemeVariant? = null
        var reset = false
        var customSeed: Int? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    dynamicColorEnabled = false,
                    onThemeSchemeVariantChange = { selectedVariant = it },
                    onResetThemeColor = { reset = true },
                    onThemeSeedColorChange = { customSeed = it },
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("Tonal Spot").performClick()
        ThemeSchemeVariant.entries.filterNot { it == ThemeSchemeVariant.TONAL_SPOT }.forEach { variant ->
            composeRule.onNodeWithText(variant.displayName).assertExists()
        }
        composeRule.onNodeWithText("Fidelity").performClick()
        composeRule.onNode(hasContentDescription("恢复默认主题色")).performClick()
        composeRule.onNode(hasContentDescription("自定义主题色")).performClick()
        composeRule.onNodeWithText("色相").assertExists()
        composeRule.onNodeWithText("应用").performClick()
        composeRule.onNodeWithText("自定义主题色").assertDoesNotExist()

        composeRule.runOnIdle {
            assertEquals(ThemeSchemeVariant.FIDELITY, selectedVariant)
            assertTrue(reset)
            assertTrue(customSeed != null)
        }
    }

    @Test
    fun fidelityCustomColorExposesSourceChromaAndToneControls() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    themeMode = ThemeMode.LIGHT,
                    onThemeModeChange = {},
                    dynamicColorEnabled = false,
                    themeSchemeVariant = ThemeSchemeVariant.FIDELITY,
                    onLogout = {},
                )
            }
        }

        composeRule.onNode(hasContentDescription("自定义主题色")).performClick()

        composeRule.onNode(hasContentDescription("色相")).assertExists()
        composeRule.onNode(hasContentDescription("色彩浓度")).assertExists()
        composeRule.onNode(hasContentDescription("明度")).assertExists()
    }
}
