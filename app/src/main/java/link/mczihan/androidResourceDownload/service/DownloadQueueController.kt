package link.mczihan.androidResourceDownload.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import link.mczihan.androidResourceDownload.data.download.DownloadRepository

@Singleton
class DownloadQueueController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val executionRegistry: DownloadExecutionRegistry,
) {
    private val blockedOwners = ConcurrentHashMap.newKeySet<String>()

    fun activate(ownerId: String) {
        blockedOwners.remove(ownerId)
    }

    fun isBlocked(ownerId: String): Boolean = ownerId in blockedOwners

    fun block(ownerId: String) {
        blockedOwners += ownerId
        executionRegistry.cancelOwner(ownerId)
        context.stopService(Intent(context, DownloadService::class.java))
    }

    fun start(ownerId: String): Boolean {
        if (ownerId in blockedOwners || !hasPublicDownloadAccess()) return false
        return try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_START),
            )
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    suspend fun startIfNeeded(ownerId: String): Boolean =
        !repository.hasRunnable(ownerId) || start(ownerId)

    suspend fun pause(ownerId: String, taskId: String): Boolean {
        val changed = repository.pause(ownerId, taskId)
        if (changed) executionRegistry.cancelTask(taskId)
        return changed
    }

    suspend fun retry(ownerId: String, taskId: String): Boolean {
        if (ownerId in blockedOwners || !hasPublicDownloadAccess()) return false
        executionRegistry.cancelTaskAndJoin(taskId)
        val changed = repository.retry(ownerId, taskId)
        return changed && start(ownerId)
    }

    suspend fun cancel(ownerId: String, taskId: String): Boolean {
        val changed = repository.cancel(ownerId, taskId)
        if (changed) {
            executionRegistry.cancelTaskAndJoin(taskId)
            repository.cleanupCancelled(ownerId, taskId)
        }
        return changed
    }

    suspend fun deleteTerminal(ownerId: String, taskId: String): Boolean {
        executionRegistry.cancelTaskAndJoin(taskId)
        return repository.deleteTerminal(ownerId, taskId)
    }

    suspend fun deleteTerminal(ownerId: String, taskId: String, deleteLocalFile: Boolean): Boolean {
        executionRegistry.cancelTaskAndJoin(taskId)
        return repository.deleteTerminal(ownerId, taskId, deleteLocalFile)
    }

    suspend fun cancelAll(ownerId: String): Int {
        executionRegistry.cancelOwnerAndJoin(ownerId)
        return repository.cancelAll(ownerId)
    }

    suspend fun clearTerminal(ownerId: String, deleteLocalFiles: Boolean): Int =
        repository.clearTerminal(ownerId, deleteLocalFiles)

    suspend fun stop(ownerId: String) {
        block(ownerId)
        repository.pauseRunning(ownerId)
        executionRegistry.cancelOwnerAndJoin(ownerId)
    }

    fun hasPublicDownloadAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
}
