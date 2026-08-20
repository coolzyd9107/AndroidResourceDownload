package link.mczihan.androidResourceDownload.feature.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import link.mczihan.androidResourceDownload.domain.model.Role
import org.junit.Rule
import org.junit.Test

class FilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun systemBackReturnsToParentDirectory() {
        composeRule.setContent {
            MaterialTheme {
                FilesScreen(
                    role = Role.USER,
                    onProfile = {},
                    onDownload = {},
                    onMessage = {},
                )
            }
        }
        composeRule.onNodeWithText("应用发布").performClick()
        composeRule.onNodeWithText("/应用发布").assertIsDisplayed()

        Espresso.pressBack()

        composeRule.onNodeWithText("/").assertIsDisplayed()
        composeRule.onNodeWithText("应用发布").assertIsDisplayed()
    }
}
