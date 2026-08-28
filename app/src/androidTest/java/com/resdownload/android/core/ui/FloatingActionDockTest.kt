package com.resdownload.android.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FloatingActionDockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsedMenuDoesNotRetainExpandedContentWidth() {
        composeRule.setContent {
            MaterialTheme {
                FloatingActionMenu(
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.testTag("floatingActionDock"),
                ) {
                    FloatingAction(
                        icon = Icons.Default.Delete,
                        label = "A long action label",
                        onClick = {},
                    )
                }
            }
        }

        val dockBounds = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot

        with(composeRule.density) {
            assertEquals(64.dp.toPx(), dockBounds.width, 1f)
        }
        composeRule.onNodeWithText("A long action label").assertDoesNotExist()
    }

    @Test
    fun collapsingMenuKeepsExpandedWidthDuringExit() {
        composeRule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                FloatingActionMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.testTag("floatingActionDock"),
                ) {
                    FloatingAction(
                        icon = Icons.Default.Delete,
                        label = "A long action label",
                        onClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val expandedWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        composeRule.runOnIdle { expanded = false }
        composeRule.mainClock.advanceTimeBy(1)

        val exitWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        assertEquals(expandedWidth, exitWidth, 1f)

        composeRule.mainClock.advanceTimeBy(2_000)
        val collapsedWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        with(composeRule.density) {
            assertEquals(64.dp.toPx(), collapsedWidth, 1f)
        }
        assertTrue(exitWidth > collapsedWidth)
    }
}
