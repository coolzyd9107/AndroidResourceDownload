package link.mczihan.androidResourceDownload.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
