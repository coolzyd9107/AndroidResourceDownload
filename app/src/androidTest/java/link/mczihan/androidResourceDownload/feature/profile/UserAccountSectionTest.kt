package link.mczihan.androidResourceDownload.feature.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import org.junit.Rule
import org.junit.Test

class UserAccountSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingAvatarShowsDefaultAvatar() {
        setAccount(user(loginType = LoginType.GITHUB))

        composeRule.onNodeWithContentDescription("默认用户头像").assertExists()
    }

    @Test
    fun accountIdentityUsesExpressiveSettingsContent() {
        setAccount(user(loginType = LoginType.EMAIL).copy(email = "123456@qq.com"))

        composeRule.onNodeWithText("123456").assertExists()
        composeRule.onNodeWithText("123456@qq.com").assertExists()
        composeRule.onNodeWithText("邮箱验证码").assertExists()
    }

    private fun setAccount(user: User) {
        composeRule.setContent {
            MaterialTheme {
                UserAccountSection(
                    user = user,
                    allowQqLookup = false,
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
