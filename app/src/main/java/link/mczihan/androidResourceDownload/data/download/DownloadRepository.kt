package link.mczihan.androidResourceDownload.data.download

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                    if (sameVersion && fileStore.hasFinalFile(existing.toDomain())) {
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

    suspend fun retry(ownerId: String, taskId: String): Boolean =
        dao.retry(ownerId, taskId, System.currentTimeMillis()) == 1

    suspend fun cancel(ownerId: String, taskId: String): Boolean {
        val task = dao.findById(ownerId, taskId)?.toDomain() ?: return false
        val changed = dao.cancel(ownerId, taskId, System.currentTimeMillis()) == 1
        if (changed) fileStore.deleteAll(task)
        return changed
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

    suspend fun complete(taskId: String, result: DownloadTransferResult): Boolean =
        dao.complete(
            taskId = taskId,
            totalBytes = result.totalBytes,
            downloadedBytes = result.downloadedBytes,
            supportRange = result.supportRange,
            etag = result.etag,
            lastModified = result.lastModified,
            mimeType = result.mimeType,
            now = System.currentTimeMillis(),
        ) == 1

    suspend fun status(taskId: String): DownloadStatus? = dao.status(taskId)

    suspend fun fail(taskId: String, message: String) {
        dao.fail(taskId, message, System.currentTimeMillis())
    }

    suspend fun requeueIfRunning(taskId: String) {
        dao.requeueIfRunning(taskId, System.currentTimeMillis())
    }

    suspend fun hasRunnable(ownerId: String): Boolean = dao.hasRunnable(ownerId)
}
