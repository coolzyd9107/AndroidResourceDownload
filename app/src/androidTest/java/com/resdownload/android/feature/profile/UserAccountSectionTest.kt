package com.resdownload.android.feature.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.resdownload.android.BuildConfig
import com.resdownload.android.domain.model.LoginType
import com.resdownload.android.domain.model.Role
import com.resdownload.android.domain.model.User
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
        setAccount(
            user(loginType = LoginType.QQ).copy(
                name = "QQ 用户",
                email = "hidden@example.com",
            ),
        )

        composeRule.onNodeWithText("QQ 用户").assertExists()
        composeRule.onNodeWithText("QQ").assertExists()
        composeRule.onNodeWithText("hidden@example.com").assertDoesNotExist()
        composeRule.onNodeWithText("邮箱").assertDoesNotExist()
        composeRule.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertExists()
    }

    private fun setAccount(user: User) {
        composeRule.setContent {
            MaterialTheme {
                UserAccountSection(
                    user = user,
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
