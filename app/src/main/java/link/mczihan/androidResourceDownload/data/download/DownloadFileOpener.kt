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
) {
    fun open(task: DownloadTask): Boolean {
        if (task.status != DownloadStatus.SUCCESS) return false
        val file = fileStore.finalFile(task).takeIf { it.isFile } ?: return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, task.mimeType ?: "application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
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
