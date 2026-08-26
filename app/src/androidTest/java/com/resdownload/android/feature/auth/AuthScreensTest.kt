package com.resdownload.android.feature.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        var qqLogin = false
        var policyAccepted = false
        composeRule.setContent {
            MaterialTheme {
                LoginScreen(
                    onGithubLogin = { githubLogin = true },
                    onQqLogin = { qqLogin = true },
                    onPolicyAccepted = { policyAccepted = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("资源云盘 应用图标").assertExists()
        val githubBounds = composeRule.onNodeWithText("GitHub 登录")
            .fetchSemanticsNode().boundsInRoot
        val qqBounds = composeRule.onNodeWithText("QQ 登录")
            .fetchSemanticsNode().boundsInRoot
        val agreementBounds = composeRule.onNodeWithText("我已阅读并同意")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(githubBounds.top, qqBounds.top, 1f)
        assertEquals(githubBounds.height, qqBounds.height, 1f)
        assertEquals(githubBounds.height, agreementBounds.height, 1f)
        assertEquals(
            qqBounds.left - githubBounds.right,
            agreementBounds.top - githubBounds.bottom,
            1f,
        )

        composeRule.onNodeWithText("GitHub 登录").performClick()
        composeRule.onNodeWithText("QQ 登录").performClick()
        composeRule.onNodeWithText("请先同意用户协议与隐私政策").assertExists()
        composeRule.runOnIdle {
            assertFalse(githubLogin)
            assertFalse(qqLogin)
        }

        composeRule.onNodeWithText("用户协议与隐私政策").performClick()
        composeRule.onNodeWithText("同意").performClick()
        composeRule.onNodeWithText("GitHub 登录").performClick()
        composeRule.onNodeWithText("QQ 登录").performClick()

        composeRule.runOnIdle {
            assertTrue(policyAccepted)
            assertTrue(githubLogin)
            assertTrue(qqLogin)
        }
    }
}
