package com.resdownload.android.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.resdownload.android.data.download.DownloadRepository

@Singleton
class DownloadQueueController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val executionRegistry: DownloadExecutionRegistry,
) {
    private val stateLock = Any()
    private val blockedOwners = mutableSetOf<String>()
    private val suspendedOwners = mutableSetOf<String>()
    private val cancelAllMutex = Mutex()

    fun activate(ownerId: String) {
        synchronized(stateLock) {
            blockedOwners.remove(ownerId)
            if (ownerId !in suspendedOwners) executionRegistry.activate(ownerId)
        }
    }

    fun isBlocked(ownerId: String): Boolean = synchronized(stateLock) {
        ownerId in blockedOwners || ownerId in suspendedOwners
    }

    fun block(ownerId: String) {
        synchronized(stateLock) {
            blockedOwners += ownerId
            executionRegistry.blockOwner(ownerId)
        }
        context.stopService(Intent(context, DownloadService::class.java))
    }

    fun start(ownerId: String): Boolean = synchronized(stateLock) {
        if (ownerId in blockedOwners || ownerId in suspendedOwners || !hasPublicDownloadAccess()) {
            return@synchronized false
        }
        executionRegistry.activate(ownerId)
        try {
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
        if (isBlocked(ownerId) || !hasPublicDownloadAccess()) return false
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

    suspend fun cancelAll(ownerId: String): Int = cancelAllMutex.withLock {
        synchronized(stateLock) {
            suspendedOwners += ownerId
            executionRegistry.closeOwner(ownerId)
        }
        try {
            repository.cancelAll(ownerId)
        } finally {
            withContext(NonCancellable) {
                executionRegistry.blockOwner(ownerId)
                executionRegistry.cancelOwnerAndJoin(ownerId)
                val shouldRestart = synchronized(stateLock) {
                    suspendedOwners.remove(ownerId)
                    if (ownerId !in blockedOwners) {
                        executionRegistry.activate(ownerId)
                        true
                    } else {
                        false
                    }
                }
                if (shouldRestart && repository.hasRunnable(ownerId)) start(ownerId)
            }
        }
    }

    suspend fun clearTerminal(ownerId: String, deleteLocalFiles: Boolean): Int =
        repository.clearTerminal(ownerId, deleteLocalFiles)

    suspend fun stop(ownerId: String) {
        synchronized(stateLock) {
            suspendedOwners += ownerId
            executionRegistry.closeOwner(ownerId)
        }
        try {
            repository.pauseRunning(ownerId)
        } finally {
            withContext(NonCancellable) {
                synchronized(stateLock) {
                    blockedOwners += ownerId
                    executionRegistry.blockOwner(ownerId)
                }
                executionRegistry.cancelOwnerAndJoin(ownerId)
                val shouldRestart = synchronized(stateLock) {
                    suspendedOwners.remove(ownerId)
                    if (ownerId !in blockedOwners) {
                        executionRegistry.activate(ownerId)
                        true
                    } else {
                        false
                    }
                }
                context.stopService(Intent(context, DownloadService::class.java))
                if (shouldRestart && repository.hasRunnable(ownerId)) start(ownerId)
            }
        }
    }

    fun hasPublicDownloadAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
}
