package link.mczihan.androidResourceDownload.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
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
}
