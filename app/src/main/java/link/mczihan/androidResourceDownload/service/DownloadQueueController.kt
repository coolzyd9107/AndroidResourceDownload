package link.mczihan.androidResourceDownload.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import link.mczihan.androidResourceDownload.data.download.DownloadRepository

@Singleton
class DownloadQueueController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val executionRegistry: DownloadExecutionRegistry,
) {
    fun start(): Boolean = try {
        ContextCompat.startForegroundService(
            context,
            Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_START),
        )
        true
    } catch (_: RuntimeException) {
        false
    }

    suspend fun startIfNeeded(ownerId: String): Boolean =
        !repository.hasPending(ownerId) || start()

    suspend fun pause(ownerId: String, taskId: String): Boolean {
        val changed = repository.pause(ownerId, taskId)
        if (changed) executionRegistry.cancelTask(taskId)
        return changed
    }

    suspend fun retry(ownerId: String, taskId: String): Boolean {
        val changed = repository.retry(ownerId, taskId)
        return changed && start()
    }

    suspend fun cancel(ownerId: String, taskId: String): Boolean {
        val changed = repository.cancel(ownerId, taskId)
        if (changed) executionRegistry.cancelTask(taskId)
        return changed
    }

    suspend fun stop(ownerId: String) {
        repository.pauseRunning(ownerId)
        executionRegistry.cancelOwner(ownerId)
        context.stopService(Intent(context, DownloadService::class.java))
    }
}
