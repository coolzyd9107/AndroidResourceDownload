package com.resdownload.android.feature.uploads

import androidx.compose.material3.MaterialTheme
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
import com.resdownload.android.domain.model.UploadStatus
import com.resdownload.android.domain.model.UploadTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UploadsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun runningTaskShowsProgressSpeedAndCancel() {
        setScreen(
            task(
                id = "running",
                status = UploadStatus.RUNNING,
                totalBytes = 4_096L,
                uploadedBytes = 1_024L,
            ),
            speeds = mapOf("running" to 1_024L),
        )

        composeRule.onNodeWithText("1.0 KB / 4.0 KB · 25%").assertExists()
        composeRule.onNodeWithTag("uploadCurrentSpeed-running")
            .assertTextEquals("1.0 KB/s")
        composeRule.onNode(hasContentDescription("取消上传")).assertExists()
    }

    @Test
    fun pendingFileShowsTheSameSizeAndPercentageFormatAsDownloads() {
        setScreen(
            task(
                id = "pending",
                status = UploadStatus.PENDING,
                totalBytes = 4_096L,
                uploadedBytes = 1_024L,
            ),
        )

        composeRule.onNodeWithText("1.0 KB / 4.0 KB · 25%").assertExists()
    }

    @Test
    fun cancelledTaskRestartsFromLeftStatusIcon() {
        var retried = false
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(task("cancelled", UploadStatus.CANCELLED)),
                    onRetry = { retried = true },
                    onCancel = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNode(hasContentDescription("重试上传")).performClick()

        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun failedTaskRetriesFromLeftStatusIcon() {
        var retried = false
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(task("failed", UploadStatus.FAILED)),
                    onRetry = { retried = true },
                    onCancel = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithTag("uploadStatusAction-failed").performClick()

        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun taskCardHeightIsStableAcrossUploadStates() {
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(
                        task("running", UploadStatus.RUNNING),
                        task("success", UploadStatus.SUCCESS),
                    ),
                    onRetry = {},
                    onCancel = {},
                    onDelete = {},
                )
            }
        }

        val runningCard = composeRule.onNodeWithTag("uploadTaskCard-running")
            .fetchSemanticsNode().boundsInRoot
        val successCard = composeRule.onNodeWithTag("uploadTaskCard-success")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(runningCard.height, successCard.height, 1f)
    }

    @Test
    fun committingTaskCannotBeCancelled() {
        setScreen(task("committing", UploadStatus.RUNNING, committing = true))

        composeRule.onNodeWithText("正在提交到云端").assertExists()
        composeRule.onNodeWithTag("uploadCurrentSpeed-committing").assertDoesNotExist()
        composeRule.onNode(hasContentDescription("取消上传")).assertDoesNotExist()
    }

    @Test
    fun failedTaskShowsRetryAndDeleteActions() {
        setScreen(task("failed", UploadStatus.FAILED, errorMessage = "网络连接中断，可重试"))

        composeRule.onNodeWithText("网络连接中断，可重试").assertExists()
        composeRule.onNode(hasContentDescription("重试上传")).assertExists()
        composeRule.onNode(hasContentDescription("删除上传任务")).assertExists()
    }

    @Test
    fun searchFiltersTasksAndClearRestoresTheList() {
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(
                        task("alpha", UploadStatus.SUCCESS),
                        task("beta", UploadStatus.FAILED),
                    ),
                    onRetry = {},
                    onCancel = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNode(hasContentDescription("搜索上传任务")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("BETA")

        composeRule.onNodeWithText("beta.bin").assertExists()
        composeRule.onNodeWithText("alpha.bin").assertDoesNotExist()
        composeRule.onNodeWithText("1 / 2 个任务").assertExists()

        composeRule.onNode(hasContentDescription("清空搜索")).performClick()

        composeRule.onNodeWithText("alpha.bin").assertExists()
        composeRule.onNodeWithText("beta.bin").assertExists()
    }

    @Test
    fun searchNoMatchUsesDistinctEmptyState() {
        setScreen(task("alpha", UploadStatus.SUCCESS))

        composeRule.onNode(hasContentDescription("搜索上传任务")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("missing")

        composeRule.onNodeWithText("未找到匹配的上传任务").assertExists()
    }

    @Test
    fun adminCanSelectUploadSearchResults() {
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(
                        task("alpha", UploadStatus.SUCCESS),
                        task("beta", UploadStatus.FAILED),
                    ),
                    onRetry = {},
                    onCancel = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNode(hasContentDescription("搜索上传任务")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("beta")
        composeRule.onNodeWithText("beta.bin").performTouchInput { longClick() }

        composeRule.onNodeWithText("已选择 1 项").assertExists()
        composeRule.onNodeWithText("重试").assertExists()
        composeRule.onNodeWithText("删除").assertExists()
    }

    @Test
    fun batchRetryOnlyRetriesSelectedTerminalTasks() {
        val retried = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(
                        task("failed", UploadStatus.FAILED),
                        task("success", UploadStatus.SUCCESS),
                    ),
                    onRetry = { retried += it },
                    onCancel = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("failed.bin").performTouchInput { longClick() }
        composeRule.onNodeWithText("全选").performClick()

        composeRule.onNodeWithText("重试").performClick()

        composeRule.runOnIdle { assertEquals(listOf("failed"), retried) }
    }

    @Test
    fun longPressingTaskActionSelectsWithoutRunningAction() {
        val retried = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(task("failed", UploadStatus.FAILED)),
                    onRetry = { retried += it },
                    onCancel = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNode(hasContentDescription("重试上传")).performTouchInput { longClick() }

        composeRule.onNodeWithText("已选择 1 项").assertExists()
        composeRule.onNode(hasContentDescription("选择上传任务")).assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(retried.isEmpty()) }
    }

    @Test
    fun selectionSearchExcludesUnselectedUploadTasks() {
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(
                        task("selected", UploadStatus.SUCCESS),
                        task("unselected", UploadStatus.FAILED),
                    ),
                    onRetry = {},
                    onCancel = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("selected.bin").performTouchInput { longClick() }
        composeRule.onNode(hasContentDescription("在已选上传任务中搜索")).performClick()

        composeRule.onNode(hasSetTextAction()).performTextReplacement("unselected")

        composeRule.onNodeWithText("未找到匹配的上传任务").assertExists()
        composeRule.onNodeWithText("0 / 1 个已选任务").assertExists()
        composeRule.onNode(hasContentDescription("返回上传任务选择")).performClick()
        composeRule.onNodeWithText("已选择 1 项").assertExists()
    }

    @Test
    fun selectedTreeBatchRetriesOnceAndReportsExpandedDeleteCount() {
        val retried = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val treeTasks = listOf(
            task(
                "directory",
                UploadStatus.FAILED,
                isDirectory = true,
                isTreeUpload = true,
                batchId = "tree",
            ),
            task(
                "child-one",
                UploadStatus.FAILED,
                isTreeUpload = true,
                batchId = "tree",
            ),
            task(
                "child-two",
                UploadStatus.SUCCESS,
                isTreeUpload = true,
                batchId = "tree",
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = treeTasks,
                    onRetry = { retried += it },
                    onCancel = {},
                    onDelete = { deleted += it },
                )
            }
        }
        composeRule.onNodeWithText("directory.bin").performTouchInput { longClick() }
        composeRule.onNodeWithText("全选").performClick()

        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertEquals(listOf("directory"), retried) }

        composeRule.onNodeWithText("directory.bin").performTouchInput { longClick() }
        composeRule.onNodeWithText("全选").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("此操作将删除 3 个已结束上传任务，确定继续吗？").assertExists()
        composeRule.onNodeWithText("确认删除").performClick()

        composeRule.runOnIdle { assertEquals(listOf("directory"), deleted) }
    }

    @Test
    fun activeTreeBatchDeletesOnlyExplicitTerminalFiles() {
        val deleted = mutableListOf<String>()
        val treeTasks = listOf(
            task(
                "directory",
                UploadStatus.SUCCESS,
                isDirectory = true,
                isTreeUpload = true,
                batchId = "tree",
            ),
            task(
                "terminal-child",
                UploadStatus.SUCCESS,
                isTreeUpload = true,
                batchId = "tree",
            ),
            task(
                "active-child",
                UploadStatus.RUNNING,
                isTreeUpload = true,
                batchId = "tree",
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = treeTasks,
                    onRetry = {},
                    onCancel = {},
                    onDelete = { deleted += it },
                )
            }
        }
        composeRule.onNodeWithText("directory.bin").performTouchInput { longClick() }
        composeRule.onNodeWithText("全选").performClick()

        composeRule.onNodeWithText("删除").performClick()
        composeRule.onNodeWithText("此操作将删除 1 个已结束上传任务，确定继续吗？").assertExists()
        composeRule.onNodeWithText("确认删除").performClick()

        composeRule.runOnIdle { assertEquals(listOf("terminal-child"), deleted) }
    }

    @Test
    fun bulkCancelRemainsGlobalWhileSearchHidesActiveTask() {
        var cancelled = false
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(
                        task("visible", UploadStatus.SUCCESS),
                        task("hidden", UploadStatus.RUNNING),
                    ),
                    onRetry = {},
                    onCancel = {},
                    onDelete = {},
                    onCancelAll = { cancelled = true },
                )
            }
        }
        composeRule.onNode(hasContentDescription("搜索上传任务")).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("visible")
        composeRule.onNodeWithText("hidden.bin").assertDoesNotExist()

        composeRule.onNodeWithTag("cancelAllTasks").performClick()
        composeRule.onNodeWithText("取消全部任务").performClick()

        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun cancelAllFloatingActionRequiresConfirmation() {
        var cancelled = false
        setScreen(
            task = task("running", UploadStatus.RUNNING),
            onCancelAll = { cancelled = true },
        )

        composeRule.onNode(hasContentDescription("更多操作")).assertDoesNotExist()
        composeRule.onNodeWithTag("cancelAllTasks").performClick()
        composeRule.runOnIdle { assertFalse(cancelled) }
        composeRule.onNodeWithText("全部取消？").assertExists()
        composeRule.onNodeWithText("取消全部任务").performClick()

        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun clearAllFloatingActionRequiresConfirmation() {
        var cleared = false
        setScreen(
            task = task("failed", UploadStatus.FAILED),
            onClearTerminal = { cleared = true },
        )

        composeRule.onNodeWithTag("clearTerminalTasks").performClick()
        composeRule.runOnIdle { assertFalse(cleared) }
        composeRule.onNodeWithText("全部清除？").assertExists()
        composeRule.onNodeWithText("清除").performClick()

        composeRule.runOnIdle { assertTrue(cleared) }
    }

    @Test
    fun taskListViewportContinuesBehindFloatingActionButton() {
        setScreen(task("failed", UploadStatus.FAILED))

        val listBottom = composeRule.onNodeWithTag("uploadTaskList")
            .fetchSemanticsNode().boundsInRoot.bottom
        val floatingActionTop = composeRule.onNodeWithTag("clearTerminalTasks")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(listBottom > floatingActionTop)
    }

    private fun setScreen(
        task: UploadTask,
        speeds: Map<String, Long> = emptyMap(),
        onCancelAll: () -> Unit = {},
        onClearTerminal: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(task),
                    currentSpeeds = speeds,
                    onRetry = {},
                    onCancel = {},
                    onDelete = {},
                    onCancelAll = onCancelAll,
                    onClearTerminal = onClearTerminal,
                )
            }
        }
    }

    private fun task(
        id: String,
        status: UploadStatus,
        totalBytes: Long? = null,
        uploadedBytes: Long = 0L,
        committing: Boolean = false,
        errorMessage: String? = null,
        isDirectory: Boolean = false,
        isTreeUpload: Boolean = false,
        batchId: String = "batch",
    ) = UploadTask(
        id = id,
        ownerId = "owner",
        batchId = batchId,
        fileName = "$id.bin",
        relativePath = "$id.bin",
        destinationRoot = "/",
        remotePath = "/$id.bin",
        sourceUri = "content://source/$id",
        permissionUri = "content://source/$id",
        isDirectory = isDirectory,
        isTreeUpload = isTreeUpload,
        mimeType = null,
        totalBytes = totalBytes,
        uploadedBytes = uploadedBytes,
        status = status,
        committing = committing,
        errorMessage = errorMessage,
        queueOrder = 0,
        pathDepth = 1,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
