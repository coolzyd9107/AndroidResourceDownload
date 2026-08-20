package link.mczihan.androidResourceDownload.feature.downloads

import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadSpeedEstimatorTest {
    private val estimator = DownloadSpeedEstimator(staleAfterMillis = 2_000L)

    @Test
    fun calculatesSpeedFromConsecutiveProgressSamples() {
        estimator.update(listOf(task(bytes = 1_000L, updatedAt = 1_000L)), observedAt = 1_000L)

        val speeds = estimator.update(
            listOf(task(bytes = 3_000L, updatedAt = 1_500L)),
            observedAt = 1_500L,
        )

        assertEquals(4_000L, speeds.getValue("task"))
    }

    @Test
    fun resetsWhenTaskStopsOrBytesRollback() {
        estimator.update(listOf(task(bytes = 3_000L, updatedAt = 1_000L)), observedAt = 1_000L)
        estimator.update(listOf(task(bytes = 4_000L, updatedAt = 2_000L)), observedAt = 2_000L)

        val rollback = estimator.update(
            listOf(task(bytes = 500L, updatedAt = 3_000L)),
            observedAt = 3_000L,
        )
        val paused = estimator.update(
            listOf(task(bytes = 500L, updatedAt = 3_500L, status = DownloadStatus.PAUSED)),
            observedAt = 3_500L,
        )

        assertEquals(0L, rollback.getValue("task"))
        assertFalse(paused.containsKey("task"))
    }

    @Test
    fun expiresStalledSpeed() {
        estimator.update(listOf(task(bytes = 1_000L, updatedAt = 1_000L)), observedAt = 1_000L)
        estimator.update(listOf(task(bytes = 2_000L, updatedAt = 2_000L)), observedAt = 2_000L)

        assertEquals(0L, estimator.snapshot(4_000L).getValue("task"))
    }

    private fun task(
        bytes: Long,
        updatedAt: Long,
        status: DownloadStatus = DownloadStatus.RUNNING,
    ) = DownloadTask(
        id = "task",
        fileName = "file.bin",
        remotePath = "/file.bin",
        downloadedBytes = bytes,
        status = status,
        createdAt = 1L,
        updatedAt = updatedAt,
    )
}
