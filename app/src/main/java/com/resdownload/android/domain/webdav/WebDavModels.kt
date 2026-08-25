package com.resdownload.android.domain.webdav

import java.io.Closeable
import java.io.InputStream

data class WebDavResource(
    val path: WebDavPath,
    val displayName: String,
    val isCollection: Boolean,
    val resourceTypeKnown: Boolean,
    val contentLength: Long?,
    val lastModifiedEpochMillis: Long?,
    val contentType: String?,
    val etag: String?,
)

data class WebDavMetadata(
    val contentLength: Long?,
    val lastModifiedEpochMillis: Long?,
    val lastModified: String?,
    val contentType: String?,
    val etag: String?,
    val acceptsByteRanges: Boolean,
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

data class WebDavContentRange(
    val start: Long,
    val endInclusive: Long,
    val totalLength: Long?,
) {
    init {
        require(start >= 0L) { "Content-Range start must not be negative" }
        require(endInclusive >= start) { "Content-Range end must not precede its start" }
        require(totalLength == null || totalLength > endInclusive) {
            "Content-Range total must exceed its end"
        }
    }
}

class WebDavUpload(
    val contentLength: Long? = null,
    val contentType: String? = null,
    val openStream: () -> InputStream,
    val onProgress: (uploadedBytes: Long) -> Unit = {},
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
    val contentRange: WebDavContentRange?,
    val stream: InputStream,
    private val closeAction: () -> Unit,
) : Closeable {
    override fun close() = closeAction()
}
