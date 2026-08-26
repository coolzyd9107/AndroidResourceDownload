package com.resdownload.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.selects.select
import com.resdownload.android.MainActivity
import com.resdownload.android.core.common.formatFileSize
import com.resdownload.android.core.security.SessionStore
import com.resdownload.android.data.file.FileRepository
import com.resdownload.android.data.file.UploadSourceResolver
import com.resdownload.android.data.file.UploadRecoveryResult
import com.resdownload.android.data.file.UploadCommitUncertainException
import com.resdownload.android.data.upload.UploadRepository
import com.resdownload.android.domain.model.Role
import com.resdownload.android.domain.model.UploadStatus
import com.resdownload.android.domain.model.UploadTask
import com.resdownload.android.domain.webdav.WebDavException
import com.resdownload.android.domain.webdav.WebDavPath
import timber.log.Timber

@AndroidEntryPoint
class UploadService : Service() {
    @Inject lateinit var repository: UploadRepository
    @Inject lateinit var fileRepository: FileRepository
    @Inject lateinit var uploadSource: UploadSourceResolver
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var queueController: UploadQueueController
    @Inject lateinit var executionRegistry: UploadExecutionRegistry

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val runnerLock = Any()
    private val wakeVersion = AtomicLong()
    private val wakeSignals = Channel<Unit>(Channel.CONFLATED)
    private val concurrencyGate = TransferConcurrencyGate()
    private val serviceGenerationReady = CompletableDeferred<Unit>()
    @Volatile private var latestStartId = 0
    private var runnerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            executionRegistry.awaitQuiescenceAndOpenServiceGeneration()
            serviceGenerationReady.complete(Unit)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        wakeVersion.incrementAndGet()
        wakeSignals.trySend(Unit)
        startForeground(NOTIFICATION_ID, buildWaitingNotification())
        serviceScope.launch {
            serviceGenerationReady.await()
            val ownerId = sessionStore.read()?.user?.takeIf { it.role == Role.ADMIN }?.id
            when (intent?.action) {
                ACTION_CANCEL -> ownerId?.let { owner ->
                    intent.taskId()?.let { queueController.cancel(owner, it) }
                }
            }
            ensureRunner()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        executionRegistry.blockAll()
        runnerJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureRunner() {
        synchronized(runnerLock) {
            if (runnerJob?.isActive == true) return
            runnerJob = serviceScope.launch { runQueue() }
        }
    }

    private suspend fun runQueue() {
        val activeFiles = mutableMapOf<String, RunningFile>()
        val completions = Channel<String>(Channel.UNLIMITED)
        var ownerId: String? = null
        var claimsBlockedAtVersion: Long? = null
        try {
            while (currentCoroutineContext().isActive) {
                val observedVersion = wakeVersion.get()
                val observedStartId = latestStartId
                while (wakeSignals.tryReceive().isSuccess) Unit
                if (claimsBlockedAtVersion != null && claimsBlockedAtVersion != observedVersion) {
                    claimsBlockedAtVersion = null
                }
                val sessionUser = sessionStore.read()?.user
                val currentOwnerId = sessionUser?.takeIf { it.role == Role.ADMIN }?.id
                if (currentOwnerId == null || queueController.isBlocked(currentOwnerId)) {
                    activeFiles.values.forEach { it.job.cancel() }
                    finishAll(activeFiles, completions)
                    if (stopIfUnchanged(observedVersion, observedStartId)) return
                    continue
                }
                if (ownerId != currentOwnerId) {
                    activeFiles.values.forEach { it.job.cancel() }
                    finishAll(activeFiles, completions)
                    ownerId = currentOwnerId
                    repository.reconcilePermissionReservations()
                    recoverInterruptedUploads(currentOwnerId)
                }

                drainCompletions(activeFiles, completions).forEach { completion ->
                    completion.blockedVersionOrNull()?.let { blockedVersion ->
                        claimsBlockedAtVersion = blockedVersion
                    }
                }

                if (claimsBlockedAtVersion == null) {
                    while (currentCoroutineContext().isActive) {
                        val directory = repository.claimNextDirectory(currentOwnerId) ?: break
                        val taskVersion = wakeVersion.get()
                        val completion = runClaimedTask(
                            currentOwnerId,
                            directory,
                            taskVersion,
                        )
                        val blockedVersion = completion.blockedVersionOrNull()
                        if (blockedVersion != null) {
                            claimsBlockedAtVersion = blockedVersion
                            break
                        }
                    }
                }

                if (claimsBlockedAtVersion == null) {
                    while (activeFiles.size < MAX_PARALLEL_TRANSFERS) {
                        val task = repository.claimNextFile(currentOwnerId) ?: break
                        val taskVersion = wakeVersion.get()
                        val deferred = serviceScope.async(start = CoroutineStart.LAZY) {
                            concurrencyGate.withSlot { executeTask(task) }
                        }
                        if (!executionRegistry.register(currentOwnerId, task.id, deferred)) {
                            withContext(NonCancellable) { repository.requeueIfRunning(task.id) }
                            break
                        }
                        activeFiles[task.id] = RunningFile(task, deferred, taskVersion)
                        deferred.invokeOnCompletion { completions.trySend(task.id) }
                        deferred.start()
                    }
                }

                updateQueueNotification(activeFiles.values.map(RunningFile::task))
                if (activeFiles.isNotEmpty()) {
                    var completion: TaskCompletion? = null
                    select<Unit> {
                        completions.onReceive { completedId ->
                            completion = finishFile(completedId, activeFiles)
                        }
                        wakeSignals.onReceive { }
                    }
                    completion?.let { result ->
                        result.blockedVersionOrNull()?.let { blockedVersion ->
                            claimsBlockedAtVersion = blockedVersion
                        }
                    }
                    continue
                }

                if (claimsBlockedAtVersion == null && repository.hasRunnable(currentOwnerId)) {
                    recoverInterruptedUploads(currentOwnerId)
                    continue
                }
                if (stopIfUnchanged(observedVersion, observedStartId)) return
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Upload queue stopped unexpectedly")
            withContext(NonCancellable) { finishAll(activeFiles, completions) }
            stopServiceNow()
        } finally {
            activeFiles.values.forEach { it.job.cancel() }
            withContext(NonCancellable) { finishAll(activeFiles, completions) }
            completions.close()
        }
    }

    private suspend fun runClaimedTask(
        ownerId: String,
        task: UploadTask,
        startedAtVersion: Long,
    ): TaskCompletion {
        val deferred = serviceScope.async(start = CoroutineStart.LAZY) { executeTask(task) }
        if (!executionRegistry.register(ownerId, task.id, deferred)) {
            withContext(NonCancellable) { repository.requeueIfRunning(task.id) }
            return TaskCompletion(task, continueQueue = true, startedAtVersion)
        }
        deferred.start()
        val continueQueue = try {
            deferred.await()
        } catch (error: CancellationException) {
            if (!serviceJob.isActive) throw error
            true
        } finally {
            executionRegistry.clear(task.id, deferred)
        }
        return TaskCompletion(task, continueQueue, startedAtVersion)
    }

    private suspend fun finishFile(
        taskId: String,
        activeFiles: MutableMap<String, RunningFile>,
    ): TaskCompletion? {
        val running = activeFiles.remove(taskId) ?: return null
        val continueQueue = try {
            running.job.await()
        } catch (error: CancellationException) {
            if (!serviceJob.isActive) throw error
            true
        } finally {
            executionRegistry.clear(taskId, running.job)
        }
        return TaskCompletion(running.task, continueQueue, running.startedAtVersion)
    }

    private suspend fun drainCompletions(
        activeFiles: MutableMap<String, RunningFile>,
        completions: Channel<String>,
    ): List<TaskCompletion> {
        val results = mutableListOf<TaskCompletion>()
        while (true) {
            val taskId = completions.tryReceive().getOrNull() ?: break
            finishFile(taskId, activeFiles)?.let(results::add)
        }
        return results
    }

    private suspend fun finishAll(
        activeFiles: MutableMap<String, RunningFile>,
        completions: Channel<String>,
    ) {
        activeFiles.values.forEach { it.job.cancel() }
        activeFiles.keys.toList().forEach { taskId ->
            runCatching { finishFile(taskId, activeFiles) }
        }
        while (completions.tryReceive().isSuccess) Unit
    }

    private suspend fun executeTask(task: UploadTask): Boolean {
        var uploadedBytes = 0L
        try {
            if (repository.status(task.id) != UploadStatus.RUNNING) throw TaskStoppedException()
            if (task.isDirectory) {
                withContext(NonCancellable) {
                    fileRepository.ensureDirectory(WebDavPath.parseDecoded(task.remotePath))
                    repository.complete(task, 0L)
                }
                return true
            }

            val sourceUri = task.sourceUri?.let(Uri::parse)
                ?: throw IOException("Upload source is unavailable")
            val destination = WebDavPath.parseDecoded(task.remotePath)
            ensureParentDirectories(task, destination)
            val document = uploadSource.resolve(sourceUri)
            val totalBytes = document.contentLength ?: task.totalBytes
            if (!repository.updatePreparation(task.id, totalBytes)) throw TaskStoppedException()
            val latestUploaded = AtomicLong()
            val lastProgressUpdate = AtomicLong()
            var committed = false
            coroutineScope {
                val progressUpdates = Channel<Long>(Channel.CONFLATED)
                val progressJob = launch {
                    for (bytes in progressUpdates) {
                        repository.updateProgress(task.id, bytes)
                    }
                }
                try {
                    fileRepository.upload(
                        path = destination,
                        stagingKey = task.id,
                        upload = document.toWebDavUpload { bytes ->
                            uploadedBytes = bytes
                            latestUploaded.set(bytes)
                            val now = System.currentTimeMillis()
                            val previous = lastProgressUpdate.get()
                            if ((now - previous >= PROGRESS_UPDATE_INTERVAL_MILLIS &&
                                    lastProgressUpdate.compareAndSet(previous, now)) ||
                                bytes == totalBytes
                            ) {
                                progressUpdates.trySend(bytes)
                                updateProgressNotification(task, bytes, totalBytes)
                            }
                        },
                        overwrite = false,
                        onCommitting = {
                            uploadedBytes = latestUploaded.get()
                            repository.updateProgress(task.id, uploadedBytes)
                            if (!repository.markCommitting(task.id)) throw TaskStoppedException()
                            updateProgressNotification(task, uploadedBytes, totalBytes, committing = true)
                        },
                        onCommitted = {
                            uploadedBytes = latestUploaded.get()
                            if (!repository.complete(task, uploadedBytes)) throw TaskStoppedException()
                            committed = true
                        },
                        onCommitFailed = { error ->
                            repository.fail(task.id, error.toUploadMessage())
                        },
                    )
                } finally {
                    progressUpdates.close()
                    withContext(NonCancellable) { progressJob.join() }
                }
            }
            if (committed) {
                showFinishedNotification(task, success = true, message = "上传完成")
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) { repository.requeueIfRunning(task.id) }
            throw error
        } catch (error: UploadCommitUncertainException) {
            Timber.e(error, "Upload task %s requires commit reconciliation", task.id)
            return false
        } catch (error: Exception) {
            Timber.e(error, "Upload task %s failed", task.id)
            val message = error.toUploadMessage()
            if (task.isDirectory) {
                repository.failDirectoryBatch(task, "文件夹结构创建失败：$message")
            } else {
                repository.fail(task.id, message)
                showFinishedNotification(task, success = false, message = message)
            }
            return error !is WebDavException.AuthenticationRequired &&
                error !is WebDavException.CredentialUnavailable
        }
        return true
    }

    private suspend fun recoverInterruptedUploads(ownerId: String) {
        repository.runningTasks(ownerId).forEach { task ->
            while (currentCoroutineContext().isActive && !reconcileInterruptedTask(task)) {
                delay(RECONCILIATION_RETRY_MILLIS)
            }
        }
    }

    private suspend fun reconcileInterruptedTask(task: UploadTask): Boolean {
        if (repository.status(task.id) != UploadStatus.RUNNING) return true
        if (task.isDirectory) {
            return try {
                if (fileRepository.isCollection(WebDavPath.parseDecoded(task.remotePath)) == true) {
                    repository.complete(task, 0L)
                } else {
                    repository.requeueIfRunning(task.id)
                    true
                }
            } catch (_: WebDavException.Network) {
                false
            } catch (_: WebDavException.ServerError) {
                false
            } catch (error: WebDavException) {
                repository.markReconciliationBlocked(task.id, error.toUploadMessage())
                throw error
            }
        }
        return try {
            when (
                fileRepository.recoverUpload(
                    path = WebDavPath.parseDecoded(task.remotePath),
                    stagingKey = task.id,
                    wasCommitting = task.committing,
                )
            ) {
                UploadRecoveryResult.COMMITTED -> repository.complete(
                    task,
                    task.totalBytes ?: task.uploadedBytes,
                )
                UploadRecoveryResult.RETRY -> {
                    repository.requeueIfRunning(task.id)
                    true
                }
            }
        } catch (_: WebDavException.Network) {
            false
        } catch (_: WebDavException.ServerError) {
            false
        } catch (error: WebDavException) {
            repository.markReconciliationBlocked(task.id, error.toUploadMessage())
            throw error
        }
    }

    private suspend fun TaskCompletion.blockedVersionOrNull(): Long? {
        if (continueQueue) return null
        if (repository.status(task.id) != UploadStatus.RUNNING) return startedAtVersion
        while (currentCoroutineContext().isActive) {
            if (reconcileInterruptedTask(task)) return null
            delay(RECONCILIATION_RETRY_MILLIS)
        }
        return startedAtVersion
    }

    private suspend fun ensureParentDirectories(task: UploadTask, destination: WebDavPath) {
        val root = WebDavPath.parseDecoded(task.destinationRoot)
        val parentSegments = destination.decodedSegments.dropLast(1)
        require(parentSegments.take(root.decodedSegments.size) == root.decodedSegments) {
            "Upload destination escaped its selected root"
        }
        for (depth in (root.decodedSegments.size + 1)..parentSegments.size) {
            fileRepository.ensureDirectory(WebDavPath.fromDecodedSegments(parentSegments.take(depth)))
        }
    }

    private fun stopIfUnchanged(observedVersion: Long, observedStartId: Int): Boolean {
        if (observedVersion != wakeVersion.get() || observedStartId != latestStartId) return false
        if (!stopSelfResult(observedStartId)) return false
        stopForeground(STOP_FOREGROUND_REMOVE)
        return true
    }

    private fun Exception.toUploadMessage(): String = when (this) {
        is WebDavException.AuthenticationRequired,
        is WebDavException.CredentialUnavailable,
        -> "登录或 WebDAV 凭据已失效"
        is WebDavException.PermissionDenied,
        is WebDavException.ReadWriteCredentialRequired,
        -> "当前账户没有云端写入权限"
        is WebDavException.NotFound -> "上传目标目录不存在"
        is WebDavException.Network -> "网络连接中断，可重试"
        is WebDavException.PreconditionFailed -> "云端已有同名文件，未执行覆盖"
        is WebDavException.Conflict -> "目标目录无效或云端已有同名内容"
        is WebDavException.Locked -> "云端目标已锁定"
        is WebDavException.ServerError -> "服务器错误（HTTP $statusCode），可重试"
        is WebDavException.UnexpectedStatus -> "服务器返回 HTTP $statusCode，无法上传"
        is SecurityException -> "所选文件的读取权限已失效，请重新选择"
        is IOException -> "无法读取所选文件"
        is IllegalArgumentException -> message ?: "上传路径无效"
        else -> message?.takeIf(String::isNotBlank) ?: "上传失败，可重试"
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "文件上传任务状态"
                setShowBadge(false)
            },
        )
    }

    private fun buildWaitingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("上传队列")
            .setContentText("正在准备任务")
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

    private fun updateQueueNotification(tasks: List<UploadTask>) {
        if (tasks.isEmpty()) return
        val text = if (tasks.size == 1) tasks.single().fileName else "${tasks.size} 个文件正在上传"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("上传队列")
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        notifySafely(NOTIFICATION_ID, notification)
    }

    private fun updateProgressNotification(
        task: UploadTask,
        uploaded: Long,
        total: Long?,
        committing: Boolean = false,
    ) {
        val determinateTotal = total?.takeIf { it > 0L }
        val percent = determinateTotal?.let {
            ((uploaded.toDouble() / it.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } ?: 0
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(task.fileName)
            .setContentText(
                if (committing) "正在提交到云端"
                else "${formatFileSize(uploaded)} / ${formatFileSize(total)}",
            )
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, determinateTotal == null)
        if (!committing) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                servicePendingIntent(ACTION_CANCEL, task.id, taskRequestCode(task, 1)),
            )
        }
        notifySafely(NOTIFICATION_ID, builder.build())
    }

    private fun showFinishedNotification(task: UploadTask, success: Boolean, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_upload_done
                else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(task.fileName)
            .setContentText(message)
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .build()
        notifySafely(taskRequestCode(task, 2), notification)
    }

    private fun notifySafely(id: Int, notification: Notification) {
        try {
            notificationManager().notify(id, notification)
        } catch (_: SecurityException) {
            // Android 13+ can run the foreground transfer without notification permission.
        }
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java),
        pendingIntentFlags(),
    )

    private fun servicePendingIntent(action: String, taskId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(this, UploadService::class.java).setAction(action)
        if (taskId != null) intent.putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags())
    }

    private fun taskRequestCode(task: UploadTask, salt: Int): Int =
        (task.id.hashCode() * 31 + salt) and 0x0fffffff

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun stopServiceNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun Intent.taskId(): String? = getStringExtra(EXTRA_TASK_ID)?.takeIf(String::isNotBlank)

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private data class RunningFile(
        val task: UploadTask,
        val job: Deferred<Boolean>,
        val startedAtVersion: Long,
    )

    private data class TaskCompletion(
        val task: UploadTask,
        val continueQueue: Boolean,
        val startedAtVersion: Long,
    )

    private class TaskStoppedException : CancellationException("Upload task was stopped")

    companion object {
        const val CHANNEL_ID = "upload_channel"
        const val CHANNEL_NAME = "文件上传"
        const val ACTION_START =
            "com.resdownload.android.service.action.START_UPLOAD_QUEUE"
        const val ACTION_CANCEL =
            "com.resdownload.android.service.action.CANCEL_UPLOAD"
        const val NOTIFICATION_ID = 1002
        private const val EXTRA_TASK_ID = "task_id"
        private const val REQUEST_OPEN_APP = 200
        private const val PROGRESS_UPDATE_INTERVAL_MILLIS = 500L
        private const val RECONCILIATION_RETRY_MILLIS = 5_000L
    }
}
