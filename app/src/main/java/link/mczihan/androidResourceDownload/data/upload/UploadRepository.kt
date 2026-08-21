package link.mczihan.androidResourceDownload.data.upload

import android.net.Uri
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.data.file.FileRepository
import link.mczihan.androidResourceDownload.domain.model.UploadTask
import link.mczihan.androidResourceDownload.domain.model.UploadStatus
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath

data class UploadEnqueueSummary(
    val addedFiles: Int,
    val addedDirectories: Int,
    val skipped: Int,
) {
    val added: Int get() = addedFiles + addedDirectories
}

@Singleton
class UploadRepository @Inject constructor(
    private val dao: UploadTaskDao,
    private val scanner: UploadSelectionScanner,
    private val permissionManager: UploadUriPermissionManager,
    private val fileRepository: FileRepository,
) {
    private val mutationMutex = Mutex()

    fun observe(ownerId: String): Flow<List<UploadTask>> =
        dao.observeForOwner(ownerId).map { tasks -> tasks.map(UploadTaskEntity::toDomain) }

    suspend fun enqueueFile(
        ownerId: String,
        destination: WebDavPath,
        uri: Uri,
    ): UploadEnqueueSummary = enqueue(ownerId, destination, uri, scanTree = false)

    suspend fun enqueueTree(
        ownerId: String,
        destination: WebDavPath,
        uri: Uri,
    ): UploadEnqueueSummary = enqueue(ownerId, destination, uri, scanTree = true)

    private suspend fun enqueue(
        ownerId: String,
        destination: WebDavPath,
        permissionUri: Uri,
        scanTree: Boolean,
    ): UploadEnqueueSummary = mutationMutex.withLock {
        require(ownerId.isNotBlank()) { "Upload owner must not be blank" }
        val permissionUriValue = permissionUri.toString()
        dao.reservePermission(UploadPermissionEntity(permissionUriValue, System.currentTimeMillis()))
        if (!permissionManager.persistRead(permissionUri)) {
            dao.deletePermissionReservation(permissionUriValue)
            throw UploadSelectionException("无法保留所选内容的读取权限，请重新选择")
        }
        var retainsPermission = false
        try {
            val entries = if (scanTree) {
                scanner.scanTree(permissionUri)
            } else {
                scanner.scanFile(permissionUri)
            }
            val activePaths = dao.activeRemotePaths(ownerId).toHashSet()
            val batchId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            // Auto-rename if target already exists (queue or remote server)
            val renamedEntries = resolveUniqueEntries(entries, destination, activePaths, scanTree)
            var skipped = 0
            val entities = renamedEntries.mapIndexedNotNull { index, entry ->
                val remotePath = uploadDestinationPath(destination, entry.relativeSegments)
                if (!activePaths.add(remotePath.toString())) {
                    skipped++
                    return@mapIndexedNotNull null
                }
                UploadTaskEntity.fromDomain(
                    UploadTask(
                        id = UUID.randomUUID().toString(),
                        ownerId = ownerId,
                        batchId = batchId,
                        fileName = entry.relativeSegments.last(),
                        relativePath = entry.relativeSegments.joinToString("/"),
                        destinationRoot = destination.toString(),
                        remotePath = remotePath.toString(),
                        sourceUri = entry.sourceUri?.toString(),
                        permissionUri = if (entry.isDirectory) null else permissionUriValue,
                        isDirectory = entry.isDirectory,
                        isTreeUpload = scanTree,
                        mimeType = entry.mimeType,
                        totalBytes = entry.size,
                        queueOrder = index,
                        pathDepth = remotePath.decodedSegments.size,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            if (entities.isNotEmpty()) dao.insertAll(entities)
            retainsPermission = entities.any { !it.isDirectory }
            if (!retainsPermission) releasePermissionIfUnused(permissionUriValue)
            UploadEnqueueSummary(
                addedFiles = entities.count { !it.isDirectory },
                addedDirectories = entities.count(UploadTaskEntity::isDirectory),
                skipped = skipped,
            )
        } catch (error: Exception) {
            if (!retainsPermission) {
                withContext(NonCancellable) { releasePermissionIfUnused(permissionUriValue) }
            }
            throw error
        }
    }

    suspend fun claimNextDirectory(ownerId: String): UploadTask? =
        dao.claimNextDirectory(ownerId, System.currentTimeMillis())?.toDomain()

    suspend fun claimNextFile(ownerId: String): UploadTask? =
        dao.claimNextFile(ownerId, System.currentTimeMillis())?.toDomain()

    suspend fun recoverRunning(ownerId: String) {
        dao.recoverRunning(ownerId, System.currentTimeMillis())
    }

    suspend fun retry(ownerId: String, taskId: String): Boolean = mutationMutex.withLock {
        val task = dao.findById(ownerId, taskId) ?: return@withLock false
        val now = System.currentTimeMillis()
        if (task.status == UploadStatus.CANCELLED) {
            dao.retry(ownerId, taskId, now) == 1
        } else if (task.isTreeUpload) {
            dao.retryFailedBatch(ownerId, task.batchId, now) > 0
        } else {
            dao.retry(ownerId, taskId, now) == 1
        }
    }

    suspend fun cancel(ownerId: String, taskId: String): Boolean =
        dao.cancelFile(ownerId, taskId, System.currentTimeMillis()) == 1

    suspend fun deleteTerminal(ownerId: String, taskId: String): Boolean = mutationMutex.withLock {
        withContext(NonCancellable) {
            val task = dao.findById(ownerId, taskId) ?: return@withContext false
            if (task.isDirectory) {
                val batch = dao.tasksForBatch(ownerId, task.batchId)
                if (batch.any { it.status == UploadStatus.PENDING ||
                        it.status == UploadStatus.RUNNING
                    }
                ) {
                    return@withContext false
                }
                val permissions = batch.mapNotNull(UploadTaskEntity::permissionUri).toSet()
                if (dao.deleteTerminalBatch(ownerId, task.batchId) == 0) return@withContext false
                permissions.forEach { releasePermissionIfUnused(it) }
            } else {
                val permissionUri = task.permissionUri
                if (dao.deleteTerminal(ownerId, taskId) != 1) return@withContext false
                permissionUri?.let { releasePermissionIfUnused(it) }
            }
            true
        }
    }

    suspend fun cancelAll(ownerId: String): Int =
        dao.cancelAllPending(ownerId, System.currentTimeMillis())

    suspend fun clearTerminal(ownerId: String): Int = mutationMutex.withLock {
        withContext(NonCancellable) {
            val terminal = dao.tasksForOwnerTerminal(ownerId)
            val permissions = terminal.mapNotNull { it.permissionUri }.toSet()
            val deleted = dao.deleteTerminalAll(ownerId)
            permissions.forEach { releasePermissionIfUnused(it) }
            deleted
        }
    }

    suspend fun updatePreparation(taskId: String, totalBytes: Long?): Boolean =
        dao.updatePreparation(taskId, totalBytes, System.currentTimeMillis()) == 1

    suspend fun updateProgress(taskId: String, uploadedBytes: Long): Boolean =
        dao.updateProgress(taskId, uploadedBytes, System.currentTimeMillis()) == 1

    suspend fun markCommitting(taskId: String): Boolean =
        dao.markCommitting(taskId, System.currentTimeMillis()) == 1

    suspend fun complete(task: UploadTask, uploadedBytes: Long): Boolean = mutationMutex.withLock {
        withContext(NonCancellable) {
            val completed = dao.completeAndDelete(
                task.id,
                uploadedBytes,
                System.currentTimeMillis(),
            )
            if (completed) task.permissionUri?.let { releasePermissionIfUnused(it) }
            completed
        }
    }

    suspend fun fail(taskId: String, message: String) {
        dao.fail(taskId, message, System.currentTimeMillis())
    }

    suspend fun failDirectoryBatch(task: UploadTask, message: String) {
        dao.failDirectoryBatch(
            ownerId = task.ownerId,
            batchId = task.batchId,
            taskId = task.id,
            message = message,
            now = System.currentTimeMillis(),
        )
    }

    suspend fun requeueIfRunning(taskId: String) {
        dao.requeueIfRunning(taskId, System.currentTimeMillis())
    }

    suspend fun markReconciliationBlocked(taskId: String, message: String) {
        dao.markReconciliationBlocked(taskId, message, System.currentTimeMillis())
    }

    suspend fun status(taskId: String) = dao.status(taskId)

    suspend fun hasRunnable(ownerId: String): Boolean = dao.hasRunnable(ownerId)

    suspend fun runningTasks(ownerId: String): List<UploadTask> =
        dao.runningForOwner(ownerId).map(UploadTaskEntity::toDomain)

    suspend fun reconcilePermissionReservations() = mutationMutex.withLock {
        dao.allPermissionReservations().forEach { reservation ->
            releasePermissionIfUnused(reservation.uri)
        }
    }

    private suspend fun releasePermissionIfUnused(permissionUri: String) {
        if (dao.countPermissionReferences(permissionUri) == 0) {
            if (permissionManager.releaseRead(Uri.parse(permissionUri))) {
                dao.deletePermissionReservation(permissionUri)
            }
        }
    }

    private suspend fun resolveUniqueEntries(
        entries: List<UploadSourceEntry>,
        destination: WebDavPath,
        activePaths: HashSet<String>,
        scanTree: Boolean,
    ): List<UploadSourceEntry> {
        if (entries.isEmpty()) return entries
        val originalName = entries.first().relativeSegments.first()
        var currentName = originalName
        var counter = 1
        while (counter < 1000) {
            val targetPath = runCatching { destination.child(currentName) }.getOrNull() ?: break
            val existsInQueue = targetPath.toString() in activePaths
            val existsOnRemote = fileRepository.resourceExists(targetPath)
            if (!existsInQueue && !existsOnRemote) break
            currentName = if (scanTree) {
                "$originalName($counter)"
            } else {
                appendSuffixToFileName(originalName, "($counter)")
            }
            counter++
        }
        if (currentName == originalName) return entries
        return entries.map { entry ->
            val newSegments = listOf(currentName) + entry.relativeSegments.drop(1)
            entry.copy(relativeSegments = newSegments)
        }
    }

    private fun appendSuffixToFileName(fileName: String, suffix: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            fileName.substring(0, dotIndex) + suffix + fileName.substring(dotIndex)
        } else {
            fileName + suffix
        }
    }
}

internal fun uploadDestinationPath(
    destination: WebDavPath,
    relativeSegments: List<String>,
): WebDavPath {
    require(relativeSegments.isNotEmpty()) { "Upload relative path must not be empty" }
    return relativeSegments.fold(destination) { path, segment -> path.child(segment) }
}
