package link.mczihan.androidResourceDownload.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import link.mczihan.androidResourceDownload.data.upload.UploadRepository

@Singleton
class UploadQueueController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UploadRepository,
    private val executionRegistry: UploadExecutionRegistry,
) {
    private val blockedOwners = ConcurrentHashMap.newKeySet<String>()

    fun activate(ownerId: String) {
        blockedOwners.remove(ownerId)
        executionRegistry.activate(ownerId)
    }

    fun isBlocked(ownerId: String): Boolean = ownerId in blockedOwners

    fun block(ownerId: String) {
        blockedOwners += ownerId
        executionRegistry.blockOwner(ownerId)
        context.stopService(Intent(context, UploadService::class.java))
    }

    fun start(ownerId: String): Boolean {
        if (ownerId in blockedOwners) return false
        executionRegistry.activate(ownerId)
        if (ownerId in blockedOwners) {
            executionRegistry.blockOwner(ownerId)
            return false
        }
        return try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UploadService::class.java).setAction(UploadService.ACTION_START),
            )
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    suspend fun startIfNeeded(ownerId: String): Boolean =
        !repository.hasRunnable(ownerId) || start(ownerId)

    suspend fun retry(ownerId: String, taskId: String): Boolean {
        if (ownerId in blockedOwners) return false
        executionRegistry.cancelTaskAndJoin(taskId)
        val changed = repository.retry(ownerId, taskId)
        return changed && start(ownerId)
    }

    suspend fun cancel(ownerId: String, taskId: String): Boolean {
        val changed = repository.cancel(ownerId, taskId)
        if (changed) executionRegistry.cancelTaskAndJoin(taskId)
        return changed
    }

    suspend fun deleteTerminal(ownerId: String, taskId: String): Boolean {
        executionRegistry.cancelTaskAndJoin(taskId)
        return repository.deleteTerminal(ownerId, taskId)
    }

    suspend fun cancelAll(ownerId: String): Int {
        executionRegistry.cancelOwnerAndJoin(ownerId)
        return repository.cancelAll(ownerId)
    }

    suspend fun clearTerminal(ownerId: String): Int =
        repository.clearTerminal(ownerId)

    suspend fun stop(ownerId: String) {
        block(ownerId)
        executionRegistry.cancelOwnerAndJoin(ownerId)
        repository.recoverRunning(ownerId)
    }

}
