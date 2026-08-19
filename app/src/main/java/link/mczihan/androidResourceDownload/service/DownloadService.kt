package link.mczihan.androidResourceDownload.service

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
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.MainActivity
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.security.SessionStore
import link.mczihan.androidResourceDownload.data.download.DownloadFileStore
import link.mczihan.androidResourceDownload.data.download.DownloadIntegrityException
import link.mczihan.androidResourceDownload.data.download.DownloadPreparation
import link.mczihan.androidResourceDownload.data.download.DownloadRepository
import link.mczihan.androidResourceDownload.data.download.DownloadTransferEngine
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException

@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var transferEngine: DownloadTransferEngine
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var queueController: DownloadQueueController
    @Inject lateinit var executionRegistry: DownloadExecutionRegistry
    @Inject lateinit var fileStore: DownloadFileStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runnerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildWaitingNotification())
        serviceScope.launch {
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

    private fun ensureRunner() {
        if (runnerJob?.isActive == true) return
        runnerJob = serviceScope.launch { runQueue() }
    }

    private suspend fun runQueue() {
        val ownerId = sessionStore.read()?.user?.id ?: return stopServiceNow()
        repository.recoverRunning(ownerId)

        while (currentCoroutineContext().isActive) {
            if (sessionStore.read()?.user?.id != ownerId) break
            val task = repository.claimNext(ownerId) ?: break
            runTask(ownerId, task)
        }
        stopServiceNow()
    }

    private suspend fun runTask(ownerId: String, task: DownloadTask) {
        var lastProgressUpdate = 0L
        var currentPreparation = DownloadPreparation(
            totalBytes = task.totalBytes,
            downloadedBytes = task.downloadedBytes,
            supportRange = task.supportRange,
            etag = task.etag,
            lastModified = task.lastModified,
            mimeType = task.mimeType,
        )
        val transferJob = serviceScope.async {
            transferEngine.transfer(
                task = task,
                onPreparation = { preparation ->
                    currentPreparation = preparation
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
        }
        executionRegistry.register(ownerId, task.id, transferJob)

        try {
            val result = transferJob.await()
            if (repository.complete(task.id, result)) {
                showFinishedNotification(task, success = true, message = "下载完成")
            } else {
                fileStore.deleteAll(task)
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) { repository.requeueIfRunning(task.id) }
            if (!currentCoroutineContext().isActive) throw error
        } catch (error: Exception) {
            val message = error.toDownloadMessage()
            repository.fail(task.id, message)
            showFinishedNotification(task, success = false, message = message)
        } finally {
            executionRegistry.clear(task.id, transferJob)
        }
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

    private fun updateProgressNotification(task: DownloadTask, downloaded: Long, total: Long?) {
        val determinate = total != null && total > 0L
        val percent = if (determinate) {
            ((downloaded.toDouble() / total!!.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.fileName)
            .setContentText("${formatFileSize(downloaded)} / ${formatFileSize(total)}")
            .setContentIntent(openAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, !determinate)
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
        notificationManager().notify(NOTIFICATION_ID, notification)
    }

    private fun showFinishedNotification(task: DownloadTask, success: Boolean, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(task.fileName)
            .setContentText(message)
            .setContentIntent(openAppPendingIntent())
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

    private class TaskStoppedException : CancellationException("Download task was stopped")

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "文件下载"
        const val ACTION_START =
            "link.mczihan.androidResourceDownload.service.action.START_DOWNLOAD_QUEUE"
        const val ACTION_PAUSE =
            "link.mczihan.androidResourceDownload.service.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL =
            "link.mczihan.androidResourceDownload.service.action.CANCEL_DOWNLOAD"
        const val ACTION_STOP =
            "link.mczihan.androidResourceDownload.service.action.STOP_DOWNLOAD_SERVICE"
        const val NOTIFICATION_ID = 1001

        private const val EXTRA_TASK_ID = "task_id"
        private const val REQUEST_OPEN_APP = 100
        private const val REQUEST_STOP = 101
        private const val PROGRESS_UPDATE_INTERVAL_MILLIS = 500L
    }
}
