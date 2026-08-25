package com.resdownload.android.feature.uploads

import kotlin.math.roundToLong
import com.resdownload.android.domain.model.UploadStatus
import com.resdownload.android.domain.model.UploadTask

internal class UploadSpeedEstimator(
    private val staleAfterMillis: Long = 2_000L,
) {
    private data class Sample(
        val uploadedBytes: Long,
        val taskUpdatedAt: Long,
        val lastProgressAt: Long,
        val measured: Boolean,
    )

    private val samples = mutableMapOf<String, Sample>()
    private val speeds = mutableMapOf<String, Long>()

    fun update(tasks: List<UploadTask>, observedAt: Long): Map<String, Long> {
        val runningIds = tasks.asSequence()
            .filter { it.status == UploadStatus.RUNNING && !it.isDirectory && !it.committing }
            .mapTo(mutableSetOf(), UploadTask::id)
        samples.keys.retainAll(runningIds)
        speeds.keys.retainAll(runningIds)

        tasks.filter { it.id in runningIds }.forEach { task ->
            val previous = samples[task.id]
            when {
                previous == null -> {
                    samples[task.id] = Sample(
                        uploadedBytes = task.uploadedBytes,
                        taskUpdatedAt = task.updatedAt,
                        lastProgressAt = observedAt,
                        measured = false,
                    )
                    speeds[task.id] = 0L
                }
                task.uploadedBytes > previous.uploadedBytes && task.updatedAt > previous.taskUpdatedAt -> {
                    val bytesDelta = task.uploadedBytes - previous.uploadedBytes
                    val timeDelta = task.updatedAt - previous.taskUpdatedAt
                    val instantSpeed = (bytesDelta.toDouble() * 1_000.0 / timeDelta)
                        .coerceIn(0.0, Long.MAX_VALUE.toDouble())
                        .roundToLong()
                    speeds[task.id] = if (previous.measured) {
                        ((speeds.getValue(task.id) * 0.65) + (instantSpeed * 0.35)).roundToLong()
                    } else {
                        instantSpeed
                    }
                    samples[task.id] = Sample(
                        uploadedBytes = task.uploadedBytes,
                        taskUpdatedAt = task.updatedAt,
                        lastProgressAt = observedAt,
                        measured = true,
                    )
                }
                task.uploadedBytes == previous.uploadedBytes &&
                    task.updatedAt == previous.taskUpdatedAt -> Unit
                task.uploadedBytes == previous.uploadedBytes &&
                    task.updatedAt > previous.taskUpdatedAt -> {
                    speeds[task.id] = 0L
                    samples[task.id] = previous.copy(taskUpdatedAt = task.updatedAt)
                }
                else -> {
                    speeds[task.id] = 0L
                    samples[task.id] = Sample(
                        uploadedBytes = task.uploadedBytes,
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
