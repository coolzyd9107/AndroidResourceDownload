package link.mczihan.androidResourceDownload.domain.model

data class DownloadTask(
    val id: String,
    val fileName: String,
    val remotePath: String,
    val downloadUrl: String? = null,
    val localPath: String,
    val tempPath: String,
    val totalBytes: Long? = null,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val supportRange: Boolean = false,
    val etag: String? = null,
    val lastModified: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * PENDING waits to start, RUNNING transfers bytes, and PAUSED can resume.
 * SUCCESS is complete, while FAILED and CANCELLED are terminal until retried.
 */
enum class DownloadStatus {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESS,
    FAILED,
    CANCELLED,
}
