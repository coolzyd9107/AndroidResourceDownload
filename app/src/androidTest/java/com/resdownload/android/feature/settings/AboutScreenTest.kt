package com.resdownload.android.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.resdownload.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appIdentityShowsIconNameAndVersion() {
        setAbout()

        composeRule.onNodeWithContentDescription("资源云盘 应用图标").assertExists()
        composeRule.onNodeWithText("资源云盘").assertExists()
        composeRule.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertExists()
        composeRule.onNodeWithText("公告").assertDoesNotExist()
    }

    @Test
    fun checkUpdateItemStartsVersionCheck() {
        var checked = false
        setAbout(onCheckUpdate = { checked = true })

        composeRule.onNodeWithText("检查更新").performClick()

        composeRule.runOnIdle { assertTrue(checked) }
    }

    @Test
    fun availableUpdateCanOpenDownloadUrl() {
        var openedUrl: String? = null
        var dismissed = false
        setAbout(
            updateState = UpdateUiState.Available(
                currentVersion = "2.0.0",
                latestVersion = "2.1.1",
                updateUrl = "https://example.com/download",
            ),
            onDismissUpdate = { dismissed = true },
            onOpenUrl = {
                openedUrl = it
                true
            },
        )

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
        setAbout(
            updateState = UpdateUiState.Available(
                currentVersion = "2.0.0",
                latestVersion = "2.0.1",
                updateUrl = "https://example.com/download",
            ),
            onDismissUpdate = { dismissed = true },
            onOpenUrl = {
                openedUrl = it
                true
            },
        )

        composeRule.onNodeWithText("取消").performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertNull(openedUrl)
        }
    }

    @Test
    fun failedBrowserLaunchKeepsAvailableUpdateDialog() {
        var dismissed = false
        setAbout(
            updateState = UpdateUiState.Available(
                currentVersion = "2.0.0",
                latestVersion = "2.1.0",
                updateUrl = "https://example.com/download",
            ),
            onDismissUpdate = { dismissed = true },
            onOpenUrl = { false },
        )

        composeRule.onNodeWithText("下载").performClick()

        composeRule.runOnIdle { assertEquals(false, dismissed) }
        composeRule.onNodeWithText("发现新版本").assertExists()
    }

    @Test
    fun upToDateStateOnlyShowsLatestMessage() {
        setAbout(updateState = UpdateUiState.UpToDate("2.0.0"))

        composeRule.onNodeWithText("已是最新版本").assertExists()
        composeRule.onNodeWithText("下载").assertDoesNotExist()
    }

    @Test
    fun developerCardPreservesOrderAndOpensProfiles() {
        val openedUrls = mutableListOf<String>()
        setAbout(onOpenUrl = { url -> openedUrls += url; true })

        val frontendTop = composeRule.onNodeWithText("前端开发者")
            .fetchSemanticsNode().boundsInRoot.top
        val backendTop = composeRule.onNodeWithText("后端开发者")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(frontendTop < backendTop)

        composeRule.onNodeWithContentDescription("打开 coolzyd9107 的 GitHub 主页")
            .performClick()
        composeRule.onNodeWithContentDescription("打开 zhuzhuzihan 的 GitHub 主页")
            .performClick()
        composeRule.onNodeWithText("GitHub").assertDoesNotExist()

        composeRule.runOnIdle {
            assertEquals(
                listOf(FRONTEND_DEVELOPER_URL, BACKEND_DEVELOPER_URL),
                openedUrls,
            )
        }
    }

    @Test
    fun sourceCodeItemOpensApplicationRepository() {
        var openedUrl: String? = null
        setAbout(onOpenUrl = { url -> openedUrl = url; true })

        composeRule.onNodeWithText("在GitHub查看源代码").performClick()

        composeRule.runOnIdle { assertEquals(SOURCE_REPOSITORY_URL, openedUrl) }
        composeRule.onNodeWithText("浏览项目代码、提交问题或参与开发").assertExists()
    }

    @Test
    fun backButtonReturnsToSettings() {
        var navigatedBack = false
        setAbout(onNavigateBack = { navigatedBack = true })

        composeRule.onNodeWithContentDescription("返回设置").performClick()

        composeRule.runOnIdle { assertTrue(navigatedBack) }
    }

    private fun setAbout(
        onNavigateBack: () -> Unit = {},
        updateState: UpdateUiState = UpdateUiState.Idle,
        onCheckUpdate: () -> Unit = {},
        onDismissUpdate: () -> Unit = {},
        onOpenUrl: (String) -> Boolean = { false },
    ) {
        composeRule.setContent {
            MaterialTheme {
                AboutScreen(
                    onNavigateBack = onNavigateBack,
                    updateState = updateState,
                    onCheckUpdate = onCheckUpdate,
                    onDismissUpdate = onDismissUpdate,
                    onOpenUrl = onOpenUrl,
                )
            }
        }
    }
}
