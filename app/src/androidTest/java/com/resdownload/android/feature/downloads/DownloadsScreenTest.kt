package com.resdownload.android.feature.downloads

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DownloadsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successfulTaskExposesFileDeleteAction() {
        assertDeleteAction(DownloadStatus.SUCCESS, "删除下载任务和本地文件")
    }

    @Test
    fun failedTaskExposesDeleteAction() {
        assertDeleteAction(DownloadStatus.FAILED, "删除下载任务")
    }

    @Test
    fun cancelledTaskExposesDeleteAction() {
        assertDeleteAction(DownloadStatus.CANCELLED, "删除下载任务")
    }

    private fun assertDeleteAction(status: DownloadStatus, description: String) {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(task(status.name.lowercase(), status)),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNode(hasContentDescription(description)).assertExists()
    }

    @Test
    fun activeTasksDoNotExposeDeleteActions() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(
                        task("pending", DownloadStatus.PENDING),
                        task("running", DownloadStatus.RUNNING),
                        task("paused", DownloadStatus.PAUSED),
                    ),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNode(hasContentDescription("删除下载任务")).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("删除下载任务和本地文件")).assertDoesNotExist()
    }

    @Test
    fun runningTaskShowsCurrentSpeed() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(task("running", DownloadStatus.RUNNING)),
                    currentSpeeds = mapOf("running" to 1_024L),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("0 B / --").assertExists()
        composeRule.onNodeWithTag("downloadCurrentSpeed-running")
            .assertTextEquals("1.0 KB/s")
    }

    @Test
    fun runningTaskPausesFromLeftStatusIcon() {
        val changes = mutableListOf<Pair<String, DownloadStatus>>()
        setScreen(
            task("running", DownloadStatus.RUNNING),
            onStatusChange = { id, status -> changes += id to status },
        )

        composeRule.onNode(hasContentDescription("暂停下载")).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("running" to DownloadStatus.PAUSED), changes)
        }
        composeRule.onNode(hasContentDescription("取消下载")).assertExists()
    }

    @Test
    fun resumableHintOnlyAppearsForPausedDownloads() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(
                        task("pending", DownloadStatus.PENDING, supportRange = true),
                        task("running", DownloadStatus.RUNNING, supportRange = true),
                        task("paused", DownloadStatus.PAUSED, supportRange = true),
                        task("failed", DownloadStatus.FAILED, supportRange = true),
                        task("cancelled", DownloadStatus.CANCELLED, supportRange = true),
                    ),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("downloadResumableHint-paused").assertExists()
        composeRule.onNodeWithTag("downloadResumableHint-pending").assertDoesNotExist()
        composeRule.onNodeWithTag("downloadResumableHint-running").assertDoesNotExist()
        composeRule.onNodeWithTag("downloadResumableHint-failed").assertDoesNotExist()
        composeRule.onNodeWithTag("downloadResumableHint-cancelled").assertDoesNotExist()
    }

    @Test
    fun completedTaskOpensFromCardAndHasNoOpenActionButton() {
        var opened = false
        setScreen(
            task("success", DownloadStatus.SUCCESS),
            onOpen = { opened = true },
        )

        composeRule.onNode(hasContentDescription("打开文件")).assertDoesNotExist()
        composeRule.onNodeWithTag("downloadTaskCard-success").performClick()

        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun cancelledTaskRestartsFromLeftStatusIcon() {
        val changes = mutableListOf<Pair<String, DownloadStatus>>()
        setScreen(
            task("cancelled", DownloadStatus.CANCELLED),
            onStatusChange = { id, status -> changes += id to status },
        )

        composeRule.onNode(hasContentDescription("重试下载")).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("cancelled" to DownloadStatus.RUNNING), changes)
        }
    }

    @Test
    fun failedTaskRetriesFromLeftStatusIcon() {
        val changes = mutableListOf<Pair<String, DownloadStatus>>()
        setScreen(
            task("failed", DownloadStatus.FAILED),
            onStatusChange = { id, status -> changes += id to status },
        )

        composeRule.onNodeWithTag("downloadStatusAction-failed").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("failed" to DownloadStatus.RUNNING), changes)
        }
    }

    @Test
    fun taskCardHeightIsStableAcrossDownloadStates() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(
                        task("running", DownloadStatus.RUNNING),
                        task("success", DownloadStatus.SUCCESS),
                    ),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }

        val runningCard = composeRule.onNodeWithTag("downloadTaskCard-running")
            .fetchSemanticsNode().boundsInRoot
        val successCard = composeRule.onNodeWithTag("downloadTaskCard-success")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(runningCard.height, successCard.height, 1f)
    }

    @Test
    fun searchFiltersTasksAndClearRestoresTheList() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(
                        task("alpha", DownloadStatus.SUCCESS),
                        task("beta", DownloadStatus.FAILED),
                    ),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNode(hasContentDescription("搜索下载任务")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("BETA")

        composeRule.onNodeWithText("beta.txt").assertExists()
        composeRule.onNodeWithText("alpha.txt").assertDoesNotExist()
        composeRule.onNodeWithText("1 / 2 个任务").assertExists()

        composeRule.onNode(hasContentDescription("清空搜索")).performClick()

        composeRule.onNodeWithText("alpha.txt").assertExists()
        composeRule.onNodeWithText("beta.txt").assertExists()
    }

    @Test
    fun searchNoMatchUsesDistinctEmptyState() {
        setScreen(task("alpha", DownloadStatus.SUCCESS))

        composeRule.onNode(hasContentDescription("搜索下载任务")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("missing")

        composeRule.onNodeWithText("未找到匹配的下载任务").assertExists()
    }

    @Test
    fun userCanSelectSearchResults() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(
                        task("alpha", DownloadStatus.SUCCESS),
                        task("beta", DownloadStatus.FAILED),
                    ),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNode(hasContentDescription("搜索下载任务")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("beta")
        composeRule.onNodeWithText("beta.txt").performTouchInput { longClick() }

        composeRule.onNodeWithText("已选择 1 项").assertExists()
        composeRule.onNodeWithText("继续").assertExists()
        composeRule.onNodeWithText("删除").assertExists()
    }

    @Test
    fun batchPauseOnlyChangesSelectedRunningTasks() {
        val changes = mutableListOf<Pair<String, DownloadStatus>>()
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(
                        task("running", DownloadStatus.RUNNING),
                        task("paused", DownloadStatus.PAUSED),
                    ),
                    onStatusChange = { id, status -> changes += id to status },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("running.txt").performTouchInput { longClick() }
        composeRule.onNodeWithText("全选").performClick()

        composeRule.onNodeWithText("暂停").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("running" to DownloadStatus.PAUSED), changes)
        }
    }

    @Test
    fun longPressingTaskActionSelectsWithoutRunningAction() {
        val changes = mutableListOf<Pair<String, DownloadStatus>>()
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(task("running", DownloadStatus.RUNNING)),
                    onStatusChange = { id, status -> changes += id to status },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNode(hasContentDescription("暂停下载")).performTouchInput { longClick() }

        composeRule.onNodeWithText("已选择 1 项").assertExists()
        composeRule.onNode(hasContentDescription("选择下载任务")).assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test
    fun selectionSearchExcludesUnselectedDownloadTasks() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(
                        task("selected", DownloadStatus.SUCCESS),
                        task("unselected", DownloadStatus.FAILED),
                    ),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("selected.txt").performTouchInput { longClick() }
        composeRule.onNode(hasContentDescription("在已选下载任务中搜索")).performClick()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("unselected")

        composeRule.onNodeWithText("未找到匹配的下载任务").assertExists()
        composeRule.onNodeWithText("0 / 1 个已选任务").assertExists()
        composeRule.onNode(hasContentDescription("返回下载任务选择")).performClick()
        composeRule.onNodeWithText("已选择 1 项").assertExists()
    }

    @Test
    fun bulkCancelRemainsGlobalWhileSearchHidesActiveTask() {
        var cancelled = false
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(
                        task("visible", DownloadStatus.SUCCESS),
                        task("hidden", DownloadStatus.RUNNING),
                    ),
                    onStatusChange = { _, _ -> },
                    onOpen = {},
                    onDelete = {},
                    onCancelAll = { cancelled = true },
                )
            }
        }
        composeRule.onNode(hasContentDescription("搜索下载任务")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("visible")
        composeRule.onNodeWithText("hidden.txt").assertDoesNotExist()

        composeRule.onNode(hasContentDescription("更多操作")).performClick()
        composeRule.onNodeWithTag("cancelAllTasks").performClick()
        composeRule.onNodeWithText("取消全部任务").performClick()

        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun cancelAllFloatingActionRequiresConfirmation() {
        var cancelled = false
        setScreen(
            task = task("running", DownloadStatus.RUNNING),
            onCancelAll = { cancelled = true },
        )

        composeRule.onNode(hasContentDescription("更多操作")).assertExists().performClick()
        composeRule.onNodeWithText("收起菜单").assertIsDisplayed()
        composeRule.onNodeWithText("全部取消").assertIsDisplayed()
        composeRule.onNodeWithTag("cancelAllTasks").performClick()
        composeRule.runOnIdle { assertFalse(cancelled) }
        composeRule.onNodeWithText("全部取消？").assertExists()
        composeRule.onNode(hasContentDescription("更多操作")).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("收起菜单")).assertDoesNotExist()
        composeRule.onNodeWithText("取消全部任务").performClick()

        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun clearAllFloatingActionReusesDeleteLocalFilesDialog() {
        var clearLocalFiles: Boolean? = null
        setScreen(
            task = task("failed", DownloadStatus.FAILED),
            onClearTerminal = { clearLocalFiles = it },
        )

        composeRule.onNode(hasContentDescription("更多操作")).performClick()
        composeRule.onNodeWithText("收起菜单").assertIsDisplayed()
        composeRule.onNodeWithText("全部清除").assertIsDisplayed()
        composeRule.onNodeWithTag("clearTerminalTasks").performClick()
        composeRule.runOnIdle { assertTrue(clearLocalFiles == null) }
        composeRule.onNodeWithText("同时删除本地文件").assertExists()
        composeRule.onNode(hasContentDescription("更多操作")).assertDoesNotExist()
        composeRule.onNode(hasContentDescription("收起菜单")).assertDoesNotExist()
        composeRule.onNodeWithText("清除").performClick()

        composeRule.runOnIdle { assertTrue(clearLocalFiles == true) }
    }

    @Test
    fun taskListViewportContinuesBehindFloatingActionButton() {
        setScreen(task("failed", DownloadStatus.FAILED))

        composeRule.onNode(hasContentDescription("更多操作")).performClick()
        val listBottom = composeRule.onNodeWithTag("downloadTaskList")
            .fetchSemanticsNode().boundsInRoot.bottom
        val floatingActionTop = composeRule.onNodeWithTag("clearTerminalTasks")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(listBottom > floatingActionTop)
    }

    private fun setScreen(
        task: DownloadTask,
        onCancelAll: () -> Unit = {},
        onClearTerminal: (Boolean) -> Unit = {},
        onStatusChange: (String, DownloadStatus) -> Unit = { _, _ -> },
        onOpen: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                DownloadsScreen(
                    tasks = listOf(task),
                    onStatusChange = onStatusChange,
                    onOpen = { onOpen() },
                    onDelete = {},
                    onCancelAll = onCancelAll,
                    onClearTerminal = onClearTerminal,
                )
            }
        }
    }

    private fun task(
        id: String,
        status: DownloadStatus,
        supportRange: Boolean = false,
    ) = DownloadTask(
        id = id,
        ownerId = "owner",
        fileName = "$id.txt",
        remotePath = "/$id.txt",
        status = status,
        supportRange = supportRange,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
