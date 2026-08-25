package com.resdownload.android.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.resdownload.android.data.download.SystemDownloadEnqueuer
import timber.log.Timber

/**
 * Listens for [DownloadManager.ACTION_DOWNLOAD_COMPLETE] broadcasts so the app
 * can react when a download handed to the system DownloadManager finishes.
 *
 * Registered statically in AndroidManifest.xml so it receives completion events
 * even when the app process is not in the foreground.
 */
@AndroidEntryPoint
class SystemDownloadReceiver : BroadcastReceiver() {

    @Inject lateinit var enqueuer: SystemDownloadEnqueuer

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            Timber.d("SystemDownloadReceiver ignored action: %s", action)
            return
        }
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) {
            Timber.w("SystemDownloadReceiver received completion without a download id")
            return
        }
        Timber.d("System download completed: id=%d", downloadId)
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                // Query status on a background thread; the enqueuer uses Dispatchers.IO
                // internally but we are already off the main thread here.
                val status = runCatching {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        } else null
                    }
                }.getOrNull()
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL ->
                        Timber.i("System download %d succeeded", downloadId)
                    DownloadManager.STATUS_FAILED ->
                        Timber.w("System download %d failed", downloadId)
                    else ->
                        Timber.d("System download %d finished with status=%s", downloadId, status)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
