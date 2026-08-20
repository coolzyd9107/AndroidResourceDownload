package link.mczihan.androidResourceDownload.feature.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingAvatarShowsDefaultAvatar() {
        setProfile(user(loginType = LoginType.GITHUB))

        composeRule.onNodeWithContentDescription("默认用户头像").assertExists()
    }

    private fun setProfile(user: User) {
        composeRule.setContent {
            MaterialTheme {
                ProfileScreen(
                    user = user,
                    onBack = {},
                    onLogout = {},
                )
            }
        }
    }

    private fun user(
        loginType: LoginType,
    ) = User(
        id = "user",
        name = "User",
        email = null,
        role = Role.USER,
        loginType = loginType,
    )
}
