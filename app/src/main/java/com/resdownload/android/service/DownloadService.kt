package com.resdownload.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import com.resdownload.android.MainActivity
import com.resdownload.android.core.common.formatFileSize
import com.resdownload.android.core.security.SessionStore
import com.resdownload.android.data.download.DownloadFileStore
import com.resdownload.android.data.download.DownloadFileOpener
import com.resdownload.android.data.download.DownloadIntegrityException
import com.resdownload.android.data.download.DownloadRepository
import com.resdownload.android.data.download.DownloadTransferEngine
import com.resdownload.android.data.download.PublicDownloadException
import com.resdownload.android.data.download.PublicDownloadOperation
import com.resdownload.android.data.download.PublicDownloadStore
import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask
import com.resdownload.android.domain.webdav.WebDavException
import timber.log.Timber

@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var transferEngine: DownloadTransferEngine
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var queueController: DownloadQueueController
    @Inject lateinit var executionRegistry: DownloadExecutionRegistry
    @Inject lateinit var fileStore: DownloadFileStore
    @Inject lateinit var publicDownloadStore: PublicDownloadStore
    @Inject lateinit var fileOpener: DownloadFileOpener

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val runnerLock = Any()
    private val wakeVersion = AtomicLong()
    private val wakeSignals = Channel<Unit>(Channel.CONFLATED)
    private val concurrencyGate = TransferConcurrencyGate()
    private val serviceGenerationReady = CompletableDeferred<Unit>()
    @Volatile private var latestStartId = 0
    @Volatile private var activeDownloadCount = 0
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
            val ownerId = sessionStore.read()?.user?.id
            when (intent?.action) {
                ACTION_PAUSE -> ownerId?.let { owner ->
                    intent.taskId()?.let { queueController.pause(owner, it) }
                }
                ACTION_CANCEL -> ownerId?.let { owner ->
                    intent.taskId()?.let { queueController.cancel(owner, it) }
                }
                ACTION_STOP -> {
                    if (ownerId != null) queueController.stop(ownerId) else stopServiceNow()
                    return@launch
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
        val activeTasks = mutableMapOf<String, RunningDownload>()
        val completions = Channel<DownloadCompletion>(Channel.UNLIMITED)
        var ownerId: String? = null
        var claimsBlockedAtVersion: Long? = null

        fun blockClaimsAt(version: Long) {
            claimsBlockedAtVersion = maxOf(claimsBlockedAtVersion ?: version, version)
        }

        try {
            while (currentCoroutineContext().isActive) {
                val observedVersion = wakeVersion.get()
                val observedStartId = latestStartId
                while (wakeSignals.tryReceive().isSuccess) Unit
                if (claimsBlockedAtVersion != null && claimsBlockedAtVersion != observedVersion) {
                    claimsBlockedAtVersion = null
                }
                val currentOwnerId = sessionStore.read()?.user?.id
                if (currentOwnerId == null || queueController.isBlocked(currentOwnerId)) {
                    activeTasks.values.forEach { it.job.cancel() }
                    finishAll(activeTasks, completions)
                    if (stopIfUnchanged(observedVersion, observedStartId)) return
                    continue
                }
                if (ownerId != currentOwnerId) {
                    activeTasks.values.forEach { it.job.cancel() }
                    finishAll(activeTasks, completions)
                    ownerId = currentOwnerId
                    repository.recoverRunning(currentOwnerId)
                }

                drainCompletions(activeTasks, completions).forEach { completion ->
                    completion.blockedVersionOrNull()?.let { blockedVersion ->
                        blockClaimsAt(blockedVersion)
                    }
                }

                if (claimsBlockedAtVersion == null) {
                    while (activeTasks.size < MAX_PARALLEL_TRANSFERS) {
                        val task = repository.claimNext(currentOwnerId) ?: break
                        val deferred = serviceScope.async(start = CoroutineStart.LAZY) {
                            concurrencyGate.withSlot { executeTask(task) }
                        }
                        if (!executionRegistry.register(currentOwnerId, task.id, deferred)) {
                            withContext(NonCancellable) { repository.requeueIfRunning(task.id) }
                            break
                        }
                        activeTasks[task.id] = RunningDownload(task, deferred)
                        deferred.invokeOnCompletion {
                            completions.trySend(
                                DownloadCompletion(task.id, deferred, wakeVersion.get()),
                            )
                        }
                        if (repository.status(task.id) != DownloadStatus.RUNNING) {
                            deferred.cancel()
                            finishTask(
                                DownloadCompletion(task.id, deferred, wakeVersion.get()),
                                activeTasks,
                            )
                            continue
                        }
                        deferred.start()
                    }
                }

                updateQueueNotification(activeTasks.values.map(RunningDownload::task))
                if (activeTasks.isNotEmpty()) {
                    var completion: TaskCompletion? = null
                    select<Unit> {
                        completions.onReceive { completed ->
                            completion = finishTask(completed, activeTasks)
                        }
                        wakeSignals.onReceive { }
                    }
                    completion?.blockedVersionOrNull()?.let { blockedVersion ->
                        blockClaimsAt(blockedVersion)
                    }
                    continue
                }

                if (claimsBlockedAtVersion == null && repository.hasRunnable(currentOwnerId)) {
                    repository.recoverRunning(currentOwnerId)
                    continue
                }
                if (stopIfUnchanged(observedVersion, observedStartId)) return
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Download queue stopped unexpectedly")
            withContext(NonCancellable) { finishAll(activeTasks, completions) }
            stopServiceNow()
        } finally {
            activeTasks.values.forEach { it.job.cancel() }
            withContext(NonCancellable) { finishAll(activeTasks, completions) }
            completions.close()
        }
    }

    private fun stopIfUnchanged(observedVersion: Long, observedStartId: Int): Boolean {
        if (observedVersion != wakeVersion.get() || observedStartId != latestStartId) return false
        if (!stopSelfResult(observedStartId)) return false
        stopForeground(STOP_FOREGROUND_REMOVE)
        return true
    }

    private suspend fun finishTask(
        completion: DownloadCompletion,
        activeTasks: MutableMap<String, RunningDownload>,
    ): TaskCompletion? {
        val taskId = completion.taskId
        val running = activeTasks[taskId]
            ?.takeIf { it.job === completion.job }
            ?: return null
        activeTasks.remove(taskId)
        val continueQueue = try {
            running.job.await()
        } catch (error: CancellationException) {
            if (!serviceJob.isActive) throw error
            true
        } finally {
            executionRegistry.clear(taskId, running.job)
        }
        return TaskCompletion(continueQueue, completion.completedAtVersion)
    }

    private suspend fun drainCompletions(
        activeTasks: MutableMap<String, RunningDownload>,
        completions: Channel<DownloadCompletion>,
    ): List<TaskCompletion> {
        val results = mutableListOf<TaskCompletion>()
        while (true) {
            val completion = completions.tryReceive().getOrNull() ?: break
            finishTask(completion, activeTasks)?.let(results::add)
        }
        return results
    }

    private suspend fun finishAll(
        activeTasks: MutableMap<String, RunningDownload>,
        completions: Channel<DownloadCompletion>,
    ) {
        activeTasks.values.forEach { it.job.cancel() }
        activeTasks.keys.toList().forEach { taskId ->
            val running = activeTasks[taskId] ?: return@forEach
            repository.requeueIfRunning(taskId)
            runCatching {
                finishTask(
                    DownloadCompletion(taskId, running.job, wakeVersion.get()),
                    activeTasks,
                )
            }
        }
        while (completions.tryReceive().isSuccess) Unit
    }

    private fun TaskCompletion.blockedVersionOrNull(): Long? =
        if (continueQueue) null else completedAtVersion

    private suspend fun executeTask(task: DownloadTask): Boolean {
        var lastProgressUpdate = 0L
        var continueQueue = true
        var stagedUri: String? = null
        var publishedUri: String? = null
        try {
            if (fileStore.hasFinalFile(task)) {
                fileStore.restoreFinalAsPartial(task)
            }
            task.publicUri?.let { staleUri ->
                withContext(NonCancellable) {
                    if (!discardPublication(task.id, staleUri)) {
                        throw IOException("Unable to remove an incomplete public download")
                    }
                }
            }
            val result = transferEngine.transfer(
                task = task,
                onPreparation = { preparation ->
                    if (!repository.updatePreparation(task.id, preparation)) {
                        throw TaskStoppedException()
                    }
                    updateProgressNotification(task, preparation.downloadedBytes, preparation.totalBytes)
                },
                onProgress = { downloadedBytes, totalBytes ->
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_MILLIS ||
                        downloadedBytes == totalBytes
                    ) {
                        if (!repository.updateProgress(task.id, downloadedBytes)) {
                            throw TaskStoppedException()
                        }
                        lastProgressUpdate = now
                        updateProgressNotification(task, downloadedBytes, totalBytes)
                    }
                },
            )
            val stage = publicDownloadStore.create(task, result.mimeType)
            stagedUri = stage
            publishedUri = stage
            if (!repository.stagePublicUri(task.id, stage)) {
                throw TaskStoppedException()
            }
            val destinationUri = publicDownloadStore.write(
                task = task,
                publicUri = stage,
                source = fileStore.finalFile(task),
                mimeType = result.mimeType,
                onPublished = { uri -> publishedUri = uri },
            )
            val committed = withContext(NonCancellable) {
                repository.complete(task.id, result, destinationUri).also { completed ->
                    if (completed) {
                        stagedUri = null
                        publishedUri = null
                    }
                }
            }
            if (committed) {
                fileStore.deleteAll(task)
                showFinishedNotification(
                    task.copy(
                        status = DownloadStatus.SUCCESS,
                        publicUri = destinationUri,
                        mimeType = result.mimeType,
                    ),
                    success = true,
                    message = "下载完成",
                )
            } else {
                withContext(NonCancellable) {
                    discardPublication(task.id, stagedUri, publishedUri)
                    stagedUri = null
                    publishedUri = null
                }
                if (repository.status(task.id) == DownloadStatus.PAUSED) {
                    fileStore.restoreFinalAsPartial(task)
                } else {
                    fileStore.deleteAll(task)
                }
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                discardPublication(task.id, stagedUri, publishedUri)
                stagedUri = null
                publishedUri = null
                if (repository.status(task.id) == DownloadStatus.SUCCESS) {
                    fileStore.deleteAll(task)
                } else if (fileStore.hasFinalFile(task)) {
                    runCatching { fileStore.restoreFinalAsPartial(task) }
                }
                repository.requeueIfRunning(task.id)
            }
            if (!currentCoroutineContext().isActive) throw error
        } catch (error: Exception) {
            withContext(NonCancellable) {
                discardPublication(task.id, stagedUri, publishedUri)
                stagedUri = null
                publishedUri = null
                if (fileStore.hasFinalFile(task)) {
                    runCatching { fileStore.restoreFinalAsPartial(task) }
                }
            }
            Timber.e(error, "Download task %s failed", task.id)
            val message = error.toDownloadMessage()
            repository.fail(task.id, message)
            showFinishedNotification(task, success = false, message = message)
            continueQueue = error !is WebDavException.AuthenticationRequired &&
                error !is WebDavException.CredentialUnavailable
        }
        return continueQueue
    }

    private suspend fun discardPublication(
        taskId: String,
        stagedUri: String?,
        publishedUri: String? = stagedUri,
    ): Boolean {
        if (publishedUri == null) return true
        if (publicDownloadStore.delete(publishedUri)) {
            stagedUri?.let { repository.clearPublicUri(taskId, it) }
            return true
        }
        return false
    }

    private fun Exception.toDownloadMessage(): String = when (this) {
        is WebDavException.AuthenticationRequired,
        is WebDavException.CredentialUnavailable,
        -> "登录或 WebDAV 凭据已失效"
        is WebDavException.PermissionDenied -> "没有下载该文件的权限"
        is WebDavException.NotFound -> "远程文件不存在"
        is WebDavException.Network -> "网络连接中断，可重试"
        is WebDavException.InvalidResponse,
        is DownloadIntegrityException,
        -> "服务器返回的文件数据无效"
        is WebDavException.CrossOriginRedirect ->
            "服务器将文件重定向到其他域名（HTTP $statusCode），已阻止凭据外泄"
        is WebDavException.RedirectRejected ->
            "服务器返回重定向（HTTP $statusCode），请检查 WebDAV 地址"
        is WebDavException.ServerError -> "服务器错误（HTTP $statusCode），可重试"
        is WebDavException.UnexpectedStatus -> "服务器返回 HTTP $statusCode，无法下载"
        is WebDavException.Conflict -> "服务器报告文件状态冲突（HTTP $statusCode）"
        is WebDavException.PreconditionFailed -> "远程文件已发生变化（HTTP $statusCode），可重试"
        is WebDavException.RangeNotSatisfiable -> "服务器拒绝下载范围（HTTP $statusCode），可重试"
        is WebDavException.Locked -> "远程文件已锁定（HTTP $statusCode）"
        is WebDavException.UnsafePath,
        is WebDavException.ResponseTooLarge,
        -> "服务器返回的文件信息无效"
        is WebDavException.ReadWriteCredentialRequired -> "当前 WebDAV 凭据不允许此操作"
        is PublicDownloadException -> when (operation) {
            PublicDownloadOperation.CREATE -> "无法在系统下载目录创建文件"
            PublicDownloadOperation.WRITE -> "无法写入系统下载目录"
            PublicDownloadOperation.PUBLISH -> "系统无法完成下载文件发布"
        }
        is SecurityException -> "需要存储权限才能写入系统下载目录"
        is IOException -> "存储空间不足或文件写入失败"
        else -> "下载失败，可重试"
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "文件下载任务状态"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildWaitingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载队列")
            .setContentText("正在准备任务")
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                servicePendingIntent(ACTION_STOP, null, REQUEST_STOP),
            )
            .build()

    private fun updateQueueNotification(tasks: List<DownloadTask>) {
        activeDownloadCount = tasks.size
        if (tasks.isEmpty()) return
        val text = if (tasks.size == 1) tasks.single().fileName else "${tasks.size} 个文件正在下载"
        notifyQueueProgress(text)
    }

    private fun notifyQueueProgress(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载队列")
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                servicePendingIntent(ACTION_STOP, null, REQUEST_STOP),
            )
            .build()
        try {
            notificationManager().notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The foreground transfer remains valid without notification permission.
        }
    }

    private fun updateProgressNotification(task: DownloadTask, downloaded: Long, total: Long?) {
        val activeCount = activeDownloadCount
        if (activeCount > 1) {
            notifyQueueProgress("$activeCount 个文件正在下载")
            return
        }
        val determinateTotal = total?.takeIf { it > 0L }
        val percent = determinateTotal?.let {
            ((downloaded.toDouble() / it.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } ?: 0
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.fileName)
            .setContentText("${formatFileSize(downloaded)} / ${formatFileSize(total)}")
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, determinateTotal == null)
            .addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                servicePendingIntent(ACTION_PAUSE, task.id, taskRequestCode(task, 1)),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                servicePendingIntent(ACTION_CANCEL, task.id, taskRequestCode(task, 2)),
            )
            .build()
        try {
            notificationManager().notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The foreground transfer remains valid without notification permission.
        }
    }

    private fun showFinishedNotification(task: DownloadTask, success: Boolean, message: String) {
        val contentIntent = if (success) {
            runCatching { fileOpener.intentFor(task) }.getOrNull()?.let { intent ->
                PendingIntent.getActivity(
                    this,
                    taskRequestCode(task, 4),
                    intent,
                    pendingIntentFlags(),
                )
            }
        } else {
            null
        } ?: openAppPendingIntent()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(task.fileName)
            .setContentText(message)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            notificationManager().notify(taskRequestCode(task, 3), notification)
        } catch (_: SecurityException) {
            // The queue still works when Android 13+ notification permission is denied.
        }
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java),
        pendingIntentFlags(),
    )

    private fun servicePendingIntent(action: String, taskId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).setAction(action)
        if (taskId != null) intent.putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags())
    }

    private fun taskRequestCode(task: DownloadTask, salt: Int): Int =
        (task.id.hashCode() * 31 + salt) and 0x0fffffff

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun stopServiceNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun Intent.taskId(): String? = getStringExtra(EXTRA_TASK_ID)?.takeIf { it.isNotBlank() }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private data class RunningDownload(
        val task: DownloadTask,
        val job: Deferred<Boolean>,
    )

    private data class DownloadCompletion(
        val taskId: String,
        val job: Deferred<Boolean>,
        val completedAtVersion: Long,
    )

    private data class TaskCompletion(
        val continueQueue: Boolean,
        val completedAtVersion: Long,
    )

    private class TaskStoppedException : CancellationException("Download task was stopped")

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "文件下载"
        const val ACTION_START =
            "com.resdownload.android.service.action.START_DOWNLOAD_QUEUE"
        const val ACTION_PAUSE =
            "com.resdownload.android.service.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL =
            "com.resdownload.android.service.action.CANCEL_DOWNLOAD"
        const val ACTION_STOP =
            "com.resdownload.android.service.action.STOP_DOWNLOAD_SERVICE"
        const val NOTIFICATION_ID = 1001

        private const val EXTRA_TASK_ID = "task_id"
        private const val REQUEST_OPEN_APP = 100
        private const val REQUEST_STOP = 101
        private const val PROGRESS_UPDATE_INTERVAL_MILLIS = 500L
    }
}
