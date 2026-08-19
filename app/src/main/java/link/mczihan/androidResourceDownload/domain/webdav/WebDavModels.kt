package link.mczihan.androidResourceDownload.domain.webdav

import java.io.Closeable
import java.io.InputStream

data class WebDavResource(
    val path: WebDavPath,
    val displayName: String,
    val isCollection: Boolean,
    val contentLength: Long?,
    val lastModifiedEpochMillis: Long?,
    val contentType: String?,
    val etag: String?,
)

data class WebDavMetadata(
    val contentLength: Long?,
    val lastModifiedEpochMillis: Long?,
    val contentType: String?,
    val etag: String?,
)

enum class WebDavDepth(val headerValue: String) {
    ZERO("0"),
    ONE("1"),
}

data class WebDavByteRange(
    val start: Long,
    val endInclusive: Long? = null,
) {
    init {
        require(start >= 0L) { "Range start must not be negative" }
        require(endInclusive == null || endInclusive >= start) {
            "Range end must not precede range start"
        }
    }

    fun toHeaderValue(): String = "bytes=$start-${endInclusive?.toString().orEmpty()}"
}

class WebDavUpload(
    val contentLength: Long? = null,
    val contentType: String? = null,
    val openStream: () -> InputStream,
) {
    init {
        require(contentLength == null || contentLength >= 0L) {
            "Upload content length must not be negative"
        }
    }
}

class WebDavReadResponse(
    val statusCode: Int,
    val metadata: WebDavMetadata,
    val contentRange: String?,
    val stream: InputStream,
    private val closeAction: () -> Unit,
) : Closeable {
    override fun close() = closeAction()
}
