package link.mczihan.androidResourceDownload.feature.uploads

import link.mczihan.androidResourceDownload.domain.model.UploadStatus
import link.mczihan.androidResourceDownload.domain.model.UploadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UploadSpeedEstimatorTest {
    private val estimator = UploadSpeedEstimator(staleAfterMillis = 2_000L)

    @Test
    fun calculatesSpeedForConcurrentRunningFiles() {
        estimator.update(
            listOf(task("one", 1_000L, 1_000L), task("two", 2_000L, 1_000L)),
            observedAt = 1_000L,
        )

        val speeds = estimator.update(
            listOf(task("one", 3_000L, 1_500L), task("two", 3_000L, 1_500L)),
            observedAt = 1_500L,
        )

        assertEquals(4_000L, speeds.getValue("one"))
        assertEquals(2_000L, speeds.getValue("two"))
    }

    @Test
    fun removesDirectoryCommittingAndStoppedTasks() {
        estimator.update(listOf(task("file", 1_000L, 1_000L)), observedAt = 1_000L)

        val speeds = estimator.update(
            listOf(
                task("file", 2_000L, 2_000L, committing = true),
                task("directory", 0L, 2_000L, isDirectory = true),
                task("failed", 0L, 2_000L, status = UploadStatus.FAILED),
            ),
            observedAt = 2_000L,
        )

        assertFalse(speeds.containsKey("file"))
        assertFalse(speeds.containsKey("directory"))
        assertFalse(speeds.containsKey("failed"))
    }

    private fun task(
        id: String,
        bytes: Long,
        updatedAt: Long,
        status: UploadStatus = UploadStatus.RUNNING,
        isDirectory: Boolean = false,
        committing: Boolean = false,
    ) = UploadTask(
        id = id,
        ownerId = "owner",
        batchId = "batch",
        fileName = "$id.bin",
        relativePath = "$id.bin",
        destinationRoot = "/",
        remotePath = "/$id.bin",
        sourceUri = if (isDirectory) null else "content://source/$id",
        permissionUri = if (isDirectory) null else "content://source/$id",
        isDirectory = isDirectory,
        isTreeUpload = false,
        mimeType = null,
        totalBytes = null,
        uploadedBytes = bytes,
        status = status,
        committing = committing,
        queueOrder = 0,
        pathDepth = 1,
        createdAt = 1L,
        updatedAt = updatedAt,
    )
}
