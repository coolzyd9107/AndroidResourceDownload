package link.mczihan.androidResourceDownload.feature.downloads

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
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

        composeRule.onNodeWithText("0 B / -- · 1.0 KB/s").assertExists()
    }

    private fun task(id: String, status: DownloadStatus) = DownloadTask(
        id = id,
        ownerId = "owner",
        fileName = "$id.txt",
        remotePath = "/$id.txt",
        status = status,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
