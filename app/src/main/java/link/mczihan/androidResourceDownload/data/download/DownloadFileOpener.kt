package link.mczihan.androidResourceDownload.data.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

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
            .setDataAndType(uri, task.mimeType ?: "application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
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
