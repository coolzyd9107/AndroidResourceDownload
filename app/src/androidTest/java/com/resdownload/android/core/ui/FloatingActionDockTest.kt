package com.resdownload.android.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
    fun switchingToCollapsedStateRemovesExpandedContentAtomically() {
        var expanded by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                FloatingActionMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.testTag("floatingActionDock"),
                    toggleModifier = Modifier.testTag("floatingActionToggle"),
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

        composeRule.runOnIdle { expanded = false }
        composeRule.waitForIdle()
        val collapsedWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        with(composeRule.density) {
            assertEquals(64.dp.toPx(), collapsedWidth, 1f)
        }
        composeRule.onNodeWithText("A long action label").assertDoesNotExist()
    }

    @Test
    fun expandingMenuAnimatesBetweenCollapsedAndExpandedSizes() {
        composeRule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(false)
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
        val collapsedWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        composeRule.runOnIdle { expanded = true }
        composeRule.mainClock.advanceTimeBy(100)
        val enteringWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        assertTrue("entering=$enteringWidth collapsed=$collapsedWidth", enteringWidth >= collapsedWidth)

        composeRule.mainClock.advanceTimeBy(2_000)
        val expandedWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        assertTrue("entering=$enteringWidth expanded=$expandedWidth", enteringWidth < expandedWidth)
        assertTrue("expanded=$expandedWidth collapsed=$collapsedWidth", expandedWidth > collapsedWidth)
        composeRule.onNodeWithText("A long action label").assertExists()

        composeRule.runOnIdle { expanded = false }
        composeRule.mainClock.advanceTimeBy(500)
        val exitingWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        assertTrue("exiting=$exitingWidth expanded=$expandedWidth", exitingWidth < expandedWidth)
    }

    @Test
    fun transitionDoesNotDuplicateToggle() {
        composeRule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                FloatingActionMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    toggleModifier = Modifier.testTag("floatingActionToggle"),
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
        composeRule.runOnIdle { expanded = true }
        composeRule.mainClock.advanceTimeBy(100)

        assertEquals(
            1,
            composeRule.onAllNodesWithTag("floatingActionToggle").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun collapseKeepsToggleLabelUntilAnimationFinishes() {
        composeRule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                FloatingActionMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    FloatingAction(
                        icon = Icons.Default.Delete,
                        label = "Action",
                        onClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { expanded = false }
        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithText("收起菜单").assertExists()

        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("收起菜单").assertDoesNotExist()
    }

    @Test
    fun collapseRetainsWidthWhenActionContentShrinks() {
        composeRule.mainClock.autoAdvance = false
        var expanded by mutableStateOf(true)
        var useLongLabel by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                FloatingActionMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.testTag("floatingActionDock"),
                ) {
                    FloatingAction(
                        icon = Icons.Default.Delete,
                        label = if (useLongLabel) "A very long action label" else "A",
                        onClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val expandedWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode().boundsInRoot.width

        composeRule.runOnIdle {
            useLongLabel = false
            expanded = false
        }
        composeRule.mainClock.advanceTimeBy(16)
        val firstCollapseFrameWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode().boundsInRoot.width

        with(composeRule.density) {
            assertTrue(
                "expanded=$expandedWidth first=$firstCollapseFrameWidth",
                firstCollapseFrameWidth > expandedWidth - 12.dp.toPx(),
            )
        }

        composeRule.mainClock.advanceTimeBy(500)
        val collapsedWidth = composeRule.onNodeWithTag("floatingActionDock")
            .fetchSemanticsNode().boundsInRoot.width
        with(composeRule.density) {
            assertEquals(64.dp.toPx(), collapsedWidth, 1f)
        }
    }

    @Test
    fun dynamicActionCrossfadesLabelWithoutChangingWidth() {
        composeRule.mainClock.autoAdvance = false
        var submenuExpanded by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                FloatingAction(
                    icon = if (submenuExpanded) Icons.Default.Close else Icons.Default.UploadFile,
                    label = if (submenuExpanded) "收起上传选项" else "上传",
                    widthReferenceLabel = "收起上传选项",
                    animateContentChanges = true,
                    onClick = { submenuExpanded = !submenuExpanded },
                    modifier = Modifier.testTag("dynamicAction"),
                )
            }
        }
        composeRule.waitForIdle()
        val initialWidth = composeRule.onNodeWithTag("dynamicAction")
            .fetchSemanticsNode().boundsInRoot.width

        composeRule.runOnIdle { submenuExpanded = true }
        composeRule.mainClock.advanceTimeBy(90)

        composeRule.onNodeWithText("上传").assertExists()
        composeRule.onNodeWithText("收起上传选项").assertExists()
        val transitionWidth = composeRule.onNodeWithTag("dynamicAction")
            .fetchSemanticsNode().boundsInRoot.width
        assertEquals(initialWidth, transitionWidth, 1f)

        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNodeWithText("上传").assertDoesNotExist()
        composeRule.onNodeWithText("收起上传选项").assertExists()
    }
}
