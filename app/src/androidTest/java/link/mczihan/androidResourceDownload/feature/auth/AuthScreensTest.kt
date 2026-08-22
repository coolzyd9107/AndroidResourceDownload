package link.mczihan.androidResourceDownload.feature.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginActionsRemainPolicyGated() {
        var githubLogin = false
        var policyAccepted = false
        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    onGithubLogin = { githubLogin = true },
                    onEmailLogin = {},
                    onPolicyAccepted = { policyAccepted = true },
                )
            }
        }

        composeRule.onNodeWithText("使用 GitHub 登录").performClick()
        composeRule.onNodeWithText("请先同意用户协议与隐私政策").assertExists()
        composeRule.runOnIdle { assertFalse(githubLogin) }

        composeRule.onNodeWithText("用户协议与隐私政策").performClick()
        composeRule.onNodeWithText("同意").performClick()
        composeRule.onNodeWithText("使用 GitHub 登录").performClick()

        composeRule.runOnIdle {
            assertTrue(policyAccepted)
            assertTrue(githubLogin)
        }
    }

    @Test
    fun emailVerificationKeepsRequestAndSixDigitLoginContract() {
        var requestedEmail: String? = null
        var login: Pair<String, String>? = null
        composeRule.setContent {
            MaterialTheme {
                EmailVerificationScreen(
                    onBack = {},
                    onVerified = { _, _ -> },
                    onRequestCode = { requestedEmail = it },
                    onLogin = { email, code -> login = email to code },
                    codeSentEmail = "admin@mczihan.link",
                    codeExpiresInSeconds = 120,
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("admin@mczihan.link")
        composeRule.onNodeWithText("重新获取验证码").performClick()
        composeRule.onNodeWithText("验证码已发送，2 分钟内有效").assertExists()
        val codeField = composeRule.onNode(hasSetTextAction() and hasText("6 位验证码"))
        codeField.performTextInput("12a34567")
        codeField.performImeAction()

        composeRule.runOnIdle {
            assertEquals("admin@mczihan.link", requestedEmail)
            assertEquals("admin@mczihan.link" to "123456", login)
        }
    }

    @Test
    fun resendFailureKeepsPreviouslySentCodeFieldAvailable() {
        var codeSentEmail by mutableStateOf<String?>("user@qq.com")
        var message by mutableStateOf<String?>(null)
        composeRule.setContent {
            MaterialTheme {
                EmailVerificationScreen(
                    onBack = {},
                    onVerified = { _, _ -> },
                    onRequestCode = {},
                    onLogin = { _, _ -> },
                    codeSentEmail = codeSentEmail,
                    message = message,
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("user@qq.com")
        composeRule.onNode(hasSetTextAction() and hasText("6 位验证码")).assertExists()

        composeRule.runOnIdle {
            codeSentEmail = null
            message = "验证码发送失败"
        }

        composeRule.onNode(hasSetTextAction() and hasText("6 位验证码")).assertExists()
        composeRule.onNodeWithText("验证码发送失败").assertExists()
    }
}
