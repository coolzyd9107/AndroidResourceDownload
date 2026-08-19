package link.mczihan.androidResourceDownload.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

@Singleton
class DownloadExecutionRegistry @Inject constructor() {
    private data class ActiveTransfer(
        val ownerId: String,
        val taskId: String,
        val job: Job,
    )

    private val lock = Any()
    private var activeTransfer: ActiveTransfer? = null

    fun register(ownerId: String, taskId: String, job: Job) {
        synchronized(lock) {
            activeTransfer = ActiveTransfer(ownerId, taskId, job)
        }
    }

    fun clear(taskId: String, job: Job) {
        synchronized(lock) {
            activeTransfer?.takeIf { it.taskId == taskId && it.job === job }?.let {
                activeTransfer = null
            }
        }
    }

    fun cancelTask(taskId: String) {
        synchronized(lock) { activeTransfer?.takeIf { it.taskId == taskId }?.job }?.cancel()
    }

    suspend fun cancelTaskAndJoin(taskId: String) {
        synchronized(lock) { activeTransfer?.takeIf { it.taskId == taskId }?.job }
            ?.cancelAndJoin()
    }

    fun cancelOwner(ownerId: String) {
        synchronized(lock) { activeTransfer?.takeIf { it.ownerId == ownerId }?.job }?.cancel()
    }

    suspend fun cancelOwnerAndJoin(ownerId: String) {
        synchronized(lock) { activeTransfer?.takeIf { it.ownerId == ownerId }?.job }
            ?.cancelAndJoin()
    }

    fun cancelActive() {
        synchronized(lock) { activeTransfer?.job }?.cancel()
    }
}
