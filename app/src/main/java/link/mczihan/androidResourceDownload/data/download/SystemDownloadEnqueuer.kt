package link.mczihan.androidResourceDownload.data.download

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Wraps the platform [DownloadManager] so the app can hand a plain HTTP(S)
 * URL (including WebDAV GET with Basic Auth headers) to the system downloader.
 *
 * This is an alternative to the in-app [DownloadTransferEngine]; it does not
 * support resumable WebDAV range validation and should be used only when the
 * caller explicitly wants the system notification / queue experience.
 */
@Singleton
class SystemDownloadEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: DownloadManager?
        get() = context.getSystemService()

    /**
     * Enqueues [url] into the system DownloadManager.
     *
     * @param headers optional HTTP headers (e.g. Authorization for WebDAV).
     * @param subDirectory relative directory under the public Downloads folder,
     *   or null to save directly in Downloads.
     * @return the system download id, usable with [query] and [remove].
     */
    suspend fun enqueue(
        url: String,
        title: String,
        mimeType: String? = null,
        headers: Map<String, String> = emptyMap(),
        description: String? = null,
        subDirectory: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val downloadManager = requireNotNull(manager) { "DownloadManager is unavailable" }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(title)
            setDescription(description ?: "通过 AndroidResourceDownload 下载")
            setMimeType(mimeType ?: DEFAULT_MIME_TYPE)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            if (subDirectory.isNullOrBlank()) {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title)
            } else {
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "${subDirectory.trim('/')}/$title",
                )
            }
            headers.forEach { (name, value) -> addRequestHeader(name, value) }
        }
        downloadManager.enqueue(request).also { id ->
            Timber.d("Enqueued system download id=$id title=$title")
        }
    }

    /** Returns the current status/progress of a system download, or null if not found. */
    suspend fun query(downloadId: Long): SystemDownloadStatus? = withContext(Dispatchers.IO) {
        val downloadManager = manager ?: return@withContext null
        val query = DownloadManager.Query().setFilterById(downloadId)
        runCatching { downloadManager.query(query) }.getOrNull()?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            cursor.toDownloadStatus()
        }
    }

    /** Removes a system download and its file. Returns the number of rows removed. */
    suspend fun remove(downloadId: Long): Int = withContext(Dispatchers.IO) {
        manager?.remove(downloadId) ?: 0
    }

    /** Returns the content:// URI for a completed download, or null if not ready. */
    suspend fun completedUri(downloadId: Long): Uri? = withContext(Dispatchers.IO) {
        val downloadManager = manager ?: return@withContext null
        val query = DownloadManager.Query().setFilterById(downloadId)
        runCatching { downloadManager.query(query) }.getOrNull()?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val uriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                uriString?.let(Uri::parse)
            } else null
        }
    }

    private fun Cursor.toDownloadStatus(): SystemDownloadStatus {
        val status = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val reason = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        val total = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val downloaded = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val title = getString(getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
        return SystemDownloadStatus(
            state = when (status) {
                DownloadManager.STATUS_PENDING -> SystemDownloadState.PENDING
                DownloadManager.STATUS_RUNNING -> SystemDownloadState.RUNNING
                DownloadManager.STATUS_PAUSED -> SystemDownloadState.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> SystemDownloadState.SUCCESSFUL
                DownloadManager.STATUS_FAILED -> SystemDownloadState.FAILED
                else -> SystemDownloadState.UNKNOWN
            },
            reason = reason,
            totalBytes = total.takeIf { it > 0L },
            downloadedBytes = downloaded.coerceAtLeast(0L),
            title = title,
        )
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}

enum class SystemDownloadState {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED,
    UNKNOWN,
}

data class SystemDownloadStatus(
    val state: SystemDownloadState,
    val reason: Int,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val title: String?,
)
