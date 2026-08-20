package link.mczihan.androidResourceDownload.data.download

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath

enum class EnqueueResult {
    ADDED,
    RESTARTED,
    ALREADY_QUEUED,
    ALREADY_DOWNLOADED,
}

@Singleton
class DownloadRepository @Inject constructor(
    private val dao: DownloadTaskDao,
    private val fileStore: DownloadFileStore,
    private val publicDownloadStore: PublicDownloadStore,
) {
    private val enqueueMutex = Mutex()

    fun observe(ownerId: String): Flow<List<DownloadTask>> =
        dao.observeForOwner(ownerId).map { tasks -> tasks.map(DownloadTaskEntity::toDomain) }

    suspend fun enqueue(ownerId: String, file: FileNode): EnqueueResult = enqueueMutex.withLock {
        require(ownerId.isNotBlank()) { "Download owner must not be blank" }
        require(!file.isDirectory) { "Directories cannot be downloaded as files" }
        val path = WebDavPath.parseDecoded(file.path)
        val remoteName = requireNotNull(path.name) { "The WebDAV root cannot be downloaded" }
        val existing = dao.findLatest(ownerId, path.toString())
        if (existing != null) {
            when (existing.status) {
                DownloadStatus.PENDING,
                DownloadStatus.RUNNING,
                -> return@withLock EnqueueResult.ALREADY_QUEUED

                DownloadStatus.PAUSED,
                DownloadStatus.FAILED,
                DownloadStatus.CANCELLED,
                -> if (dao.retry(ownerId, existing.id, System.currentTimeMillis()) == 1) {
                    return@withLock EnqueueResult.RESTARTED
                }

                DownloadStatus.SUCCESS -> {
                    val sameVersion = file.etag != null && existing.etag != null &&
                        file.etag == existing.etag
                    if (sameVersion && hasCompletedFile(existing)) {
                        return@withLock EnqueueResult.ALREADY_DOWNLOADED
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        dao.insert(
            DownloadTaskEntity.fromDomain(
                DownloadTask(
                    id = UUID.randomUUID().toString(),
                    ownerId = ownerId,
                    fileName = remoteName,
                    remotePath = path.toString(),
                    storageName = DownloadFileStore.storageNameFor(remoteName),
                    mimeType = file.mimeType,
                    totalBytes = file.size,
                    etag = file.etag,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        EnqueueResult.ADDED
    }

    suspend fun claimNext(ownerId: String): DownloadTask? =
        dao.claimNext(ownerId, System.currentTimeMillis())?.toDomain()

    suspend fun recoverRunning(ownerId: String) {
        dao.recoverRunning(ownerId, System.currentTimeMillis())
    }

    suspend fun pause(ownerId: String, taskId: String): Boolean =
        dao.pause(ownerId, taskId, System.currentTimeMillis()) == 1

    suspend fun pauseRunning(ownerId: String) {
        dao.pauseRunning(ownerId, System.currentTimeMillis())
    }

    suspend fun retry(ownerId: String, taskId: String): Boolean = enqueueMutex.withLock {
        dao.retry(ownerId, taskId, System.currentTimeMillis()) == 1
    }

    suspend fun cancel(ownerId: String, taskId: String): Boolean {
        return dao.cancel(ownerId, taskId, System.currentTimeMillis()) == 1
    }

    suspend fun cleanupCancelled(ownerId: String, taskId: String) {
        val task = dao.findById(ownerId, taskId)?.toDomain()
            ?.takeIf { it.status == DownloadStatus.CANCELLED }
            ?: return
        task.publicUri?.let { publicUri ->
            if (publicDownloadStore.delete(publicUri)) {
                dao.clearPublicUri(task.id, publicUri, System.currentTimeMillis())
            }
        }
        fileStore.deleteAll(task)
    }

    suspend fun deleteTerminal(ownerId: String, taskId: String): Boolean = enqueueMutex.withLock {
        withContext(NonCancellable) {
            val task = dao.findById(ownerId, taskId)?.toDomain() ?: return@withContext false
            when (task.status) {
                DownloadStatus.SUCCESS -> {
                    if (!publicDownloadStore.delete(task.publicUri)) return@withContext false
                    fileStore.deleteAll(task)
                    dao.deleteTerminal(ownerId, taskId) == 1
                }
                DownloadStatus.FAILED,
                DownloadStatus.CANCELLED,
                -> {
                    if (task.publicUri != null && !publicDownloadStore.delete(task.publicUri)) {
                        return@withContext false
                    }
                    fileStore.deleteAll(task)
                    dao.deleteTerminal(ownerId, taskId) == 1
                }
                else -> false
            }
        }
    }

    suspend fun updatePreparation(taskId: String, preparation: DownloadPreparation): Boolean =
        dao.updatePreparation(
            taskId = taskId,
            totalBytes = preparation.totalBytes,
            downloadedBytes = preparation.downloadedBytes,
            supportRange = preparation.supportRange,
            etag = preparation.etag,
            lastModified = preparation.lastModified,
            mimeType = preparation.mimeType,
            now = System.currentTimeMillis(),
        ) == 1

    suspend fun updateProgress(taskId: String, downloadedBytes: Long): Boolean =
        dao.updateProgress(taskId, downloadedBytes, System.currentTimeMillis()) == 1

    suspend fun complete(taskId: String, result: DownloadTransferResult, publicUri: String): Boolean =
        dao.complete(
            taskId = taskId,
            totalBytes = result.totalBytes,
            downloadedBytes = result.downloadedBytes,
            supportRange = result.supportRange,
            etag = result.etag,
            lastModified = result.lastModified,
            publicUri = publicUri,
            mimeType = result.mimeType,
            now = System.currentTimeMillis(),
        ) == 1

    suspend fun stagePublicUri(taskId: String, publicUri: String): Boolean =
        dao.stagePublicUri(taskId, publicUri, System.currentTimeMillis()) == 1

    suspend fun clearPublicUri(taskId: String, publicUri: String) {
        dao.clearPublicUri(taskId, publicUri, System.currentTimeMillis())
    }

    suspend fun status(taskId: String): DownloadStatus? = dao.status(taskId)

    suspend fun fail(taskId: String, message: String) {
        dao.fail(taskId, message, System.currentTimeMillis())
    }

    suspend fun requeueIfRunning(taskId: String) {
        dao.requeueIfRunning(taskId, System.currentTimeMillis())
    }

    suspend fun hasRunnable(ownerId: String): Boolean = dao.hasRunnable(ownerId)

    suspend fun reconcileUncommittedPublications(ownerId: String) {
        dao.findUncommittedPublications(ownerId).forEach { entity ->
            val publicUri = entity.publicUri ?: return@forEach
            if (publicDownloadStore.delete(publicUri)) {
                dao.clearPublicUri(entity.id, publicUri, System.currentTimeMillis())
            }
        }
    }

    suspend fun removeMissingSuccessful(ownerId: String) = enqueueMutex.withLock {
        dao.findSuccessfulForOwner(ownerId).forEach { entity ->
            val task = entity.toDomain()
            when (publicDownloadStore.presence(task.publicUri)) {
                PublicDownloadPresence.PRESENT,
                PublicDownloadPresence.UNKNOWN,
                -> Unit
                PublicDownloadPresence.MISSING -> if (!fileStore.hasFinalFile(task)) {
                    removeConfirmedMissing(ownerId, task)
                }
                PublicDownloadPresence.PENDING -> if (!fileStore.hasFinalFile(task)) {
                    withContext(NonCancellable) {
                        if (publicDownloadStore.delete(task.publicUri)) {
                            removeConfirmedMissing(ownerId, task)
                        }
                    }
                }
            }
        }
    }

    private suspend fun removeConfirmedMissing(ownerId: String, task: DownloadTask) {
        withContext(NonCancellable) {
            if (dao.deleteTerminal(ownerId, task.id) == 1) fileStore.deleteAll(task)
        }
    }

    private suspend fun hasCompletedFile(entity: DownloadTaskEntity): Boolean {
        var task = entity.toDomain()
        if (publicDownloadStore.exists(task.publicUri)) {
            if (fileStore.hasFinalFile(task)) fileStore.deleteAll(task)
            return true
        }
        task.publicUri?.let { staleUri ->
            if (!publicDownloadStore.delete(staleUri)) return false
            dao.clearCompletedPublicUri(task.id, staleUri, System.currentTimeMillis())
            task = task.copy(publicUri = null)
        }
        if (!fileStore.hasFinalFile(task)) return false

        val stageUri = try {
            publicDownloadStore.create(task, task.mimeType)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return false
        }
        var publicUri = stageUri
        try {
            publicUri = publicDownloadStore.write(
                task = task,
                publicUri = stageUri,
                source = fileStore.finalFile(task),
                mimeType = task.mimeType,
                onPublished = { uri -> publicUri = uri },
            )
        } catch (error: CancellationException) {
            withContext(NonCancellable) { publicDownloadStore.delete(publicUri) }
            throw error
        } catch (_: Exception) {
            withContext(NonCancellable) { publicDownloadStore.delete(publicUri) }
            return false
        }
        return if (dao.attachPublicUri(task.id, publicUri, System.currentTimeMillis()) == 1) {
            fileStore.deleteAll(task)
            true
        } else {
            withContext(NonCancellable) { publicDownloadStore.delete(publicUri) }
            false
        }
    }
}
