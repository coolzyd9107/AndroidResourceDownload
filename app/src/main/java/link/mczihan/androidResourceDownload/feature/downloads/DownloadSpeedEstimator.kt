package link.mczihan.androidResourceDownload.feature.downloads

import kotlin.math.roundToLong
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

internal class DownloadSpeedEstimator(
    private val staleAfterMillis: Long = 2_000L,
) {
    private data class Sample(
        val downloadedBytes: Long,
        val taskUpdatedAt: Long,
        val lastProgressAt: Long,
        val measured: Boolean,
    )

    private val samples = mutableMapOf<String, Sample>()
    private val speeds = mutableMapOf<String, Long>()

    fun update(tasks: List<DownloadTask>, observedAt: Long): Map<String, Long> {
        val runningIds = tasks.asSequence()
            .filter { it.status == DownloadStatus.RUNNING }
            .mapTo(mutableSetOf(), DownloadTask::id)
        samples.keys.retainAll(runningIds)
        speeds.keys.retainAll(runningIds)

        tasks.filter { it.status == DownloadStatus.RUNNING }.forEach { task ->
            val previous = samples[task.id]
            when {
                previous == null -> {
                    samples[task.id] = Sample(
                        downloadedBytes = task.downloadedBytes,
                        taskUpdatedAt = task.updatedAt,
                        lastProgressAt = observedAt,
                        measured = false,
                    )
                    speeds[task.id] = 0L
                }
                task.downloadedBytes > previous.downloadedBytes &&
                    task.updatedAt > previous.taskUpdatedAt -> {
                    val bytesDelta = task.downloadedBytes - previous.downloadedBytes
                    val timeDelta = task.updatedAt - previous.taskUpdatedAt
                    val instantSpeed = (bytesDelta.toDouble() * 1_000.0 / timeDelta)
                        .coerceIn(0.0, Long.MAX_VALUE.toDouble())
                        .roundToLong()
                    val speed = if (previous.measured) {
                        ((speeds.getValue(task.id) * 0.65) + (instantSpeed * 0.35)).roundToLong()
                    } else {
                        instantSpeed
                    }
                    speeds[task.id] = speed
                    samples[task.id] = Sample(
                        downloadedBytes = task.downloadedBytes,
                        taskUpdatedAt = task.updatedAt,
                        lastProgressAt = observedAt,
                        measured = true,
                    )
                }
                task.downloadedBytes == previous.downloadedBytes &&
                    task.updatedAt == previous.taskUpdatedAt -> Unit
                task.downloadedBytes == previous.downloadedBytes &&
                    task.updatedAt > previous.taskUpdatedAt -> {
                    speeds[task.id] = 0L
                    samples[task.id] = previous.copy(taskUpdatedAt = task.updatedAt)
                }
                else -> {
                    speeds[task.id] = 0L
                    samples[task.id] = Sample(
                        downloadedBytes = task.downloadedBytes,
                        taskUpdatedAt = task.updatedAt,
                        lastProgressAt = observedAt,
                        measured = false,
                    )
                }
            }
        }
        return snapshot(observedAt)
    }

    fun snapshot(observedAt: Long): Map<String, Long> {
        samples.forEach { (taskId, sample) ->
            if (observedAt - sample.lastProgressAt >= staleAfterMillis) speeds[taskId] = 0L
        }
        return speeds.toMap()
    }

    fun clear() {
        samples.clear()
        speeds.clear()
    }
}
