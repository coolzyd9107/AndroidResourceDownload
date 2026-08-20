package link.mczihan.androidResourceDownload.domain.model

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val lastModified: Long? = null,
    val mimeType: String? = null,
    val etag: String? = null,
    val isUploadTemporary: Boolean = false,
)
