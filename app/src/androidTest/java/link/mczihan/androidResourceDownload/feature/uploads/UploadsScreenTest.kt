package link.mczihan.androidResourceDownload.feature.uploads

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import link.mczihan.androidResourceDownload.domain.model.UploadStatus
import link.mczihan.androidResourceDownload.domain.model.UploadTask
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

        composeRule.onNodeWithText("1.0 KB / 4.0 KB · 25% · 1.0 KB/s").assertExists()
        composeRule.onNode(hasContentDescription("取消上传")).assertExists()
    }

    @Test
    fun committingTaskCannotBeCancelled() {
        setScreen(task("committing", UploadStatus.RUNNING, committing = true))

        composeRule.onNodeWithText("正在提交到云端").assertExists()
        composeRule.onNode(hasContentDescription("取消上传")).assertDoesNotExist()
    }

    @Test
    fun failedTaskShowsRetryAndDeleteActions() {
        setScreen(task("failed", UploadStatus.FAILED, errorMessage = "网络连接中断，可重试"))

        composeRule.onNodeWithText("网络连接中断，可重试").assertExists()
        composeRule.onNode(hasContentDescription("重试上传")).assertExists()
        composeRule.onNode(hasContentDescription("删除上传任务")).assertExists()
    }

    private fun setScreen(task: UploadTask, speeds: Map<String, Long> = emptyMap()) {
        composeRule.setContent {
            MaterialTheme {
                UploadsScreen(
                    tasks = listOf(task),
                    currentSpeeds = speeds,
                    onRetry = {},
                    onCancel = {},
                    onDelete = {},
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
    ) = UploadTask(
        id = id,
        ownerId = "owner",
        batchId = "batch",
        fileName = "$id.bin",
        relativePath = "$id.bin",
        destinationRoot = "/",
        remotePath = "/$id.bin",
        sourceUri = "content://source/$id",
        permissionUri = "content://source/$id",
        isDirectory = false,
        isTreeUpload = false,
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
