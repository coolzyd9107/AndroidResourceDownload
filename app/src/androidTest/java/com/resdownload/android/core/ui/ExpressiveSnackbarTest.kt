package com.resdownload.android.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpressiveSnackbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun snackbarCentersOnCollapsedFloatingActionButton() {
        val message = "已清除 3 个已结束任务"
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize().testTag("snackbarRoot"),
                ) {
                    ExpressiveSnackbarHost(
                        hostState = hostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("collapsedFloatingActionButton"),
                        )
                    }
                }
            }
            LaunchedEffect(hostState) {
                hostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
            }
        }

        composeRule.onNodeWithText(message).assertIsDisplayed()
        val rootBounds = composeRule.onNodeWithTag("snackbarRoot")
            .fetchSemanticsNode().boundsInRoot
        val snackbarBounds = composeRule.onNodeWithTag("expressiveSnackbar")
            .fetchSemanticsNode().boundsInRoot
        val floatingActionBounds = composeRule.onNodeWithTag("collapsedFloatingActionButton")
            .fetchSemanticsNode().boundsInRoot

        with(composeRule.density) {
            assertTrue(snackbarBounds.width <= 320.dp.toPx() + 1f)
            assertTrue(snackbarBounds.width < rootBounds.width - 32.dp.toPx())
        }
        assertEquals(floatingActionBounds.center.y, snackbarBounds.center.y, 1f)
    }

    @Test
    fun snackbarActionRemainsFunctional() {
        var result: SnackbarResult? = null
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            MaterialTheme {
                ExpressiveSnackbarHost(hostState)
            }
            LaunchedEffect(hostState) {
                result = hostState.showSnackbar(
                    message = "操作失败",
                    actionLabel = "重试",
                    duration = SnackbarDuration.Indefinite,
                )
            }
        }

        composeRule.onNodeWithText("重试").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(SnackbarResult.ActionPerformed, result)
        }
    }
}
