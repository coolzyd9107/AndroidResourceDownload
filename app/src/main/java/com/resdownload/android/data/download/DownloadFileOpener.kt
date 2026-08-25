package com.resdownload.android.data.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask

@Singleton
class DownloadFileOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: DownloadFileStore,
    private val publicDownloadStore: PublicDownloadStore,
) {
    fun intentFor(task: DownloadTask): Intent? {
        if (task.status != DownloadStatus.SUCCESS) return null
        val uri = publicDownloadStore.uriForViewing(task.publicUri) ?: run {
            val file = fileStore.finalFile(task).takeIf { it.isFile } ?: return null
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, resolveMimeType(task))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Resolves a usable MIME type for opening the file. Servers and CDNs often return
     * a generic [application/octet-stream] or no Content-Type at all, which prevents
     * Android from finding a matching viewer. Fall back to the file extension via
     * [MimeTypeMap] so that PDFs, images, videos and archives open correctly.
     */
    private fun resolveMimeType(task: DownloadTask): String {
        val explicit = task.mimeType
        if (explicit != null &&
            explicit != "application/octet-stream" &&
            explicit != "*/*" &&
            explicit.isNotBlank()
        ) {
            return explicit
        }
        val extension = task.fileName.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }
        return "application/octet-stream"
    }

    fun open(task: DownloadTask): Boolean {
        val intent = try {
            intentFor(task)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return false
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
