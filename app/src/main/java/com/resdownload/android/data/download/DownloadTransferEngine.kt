package com.resdownload.android.data.download

import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import com.resdownload.android.domain.model.DownloadTask
import com.resdownload.android.domain.webdav.WebDavByteRange
import com.resdownload.android.domain.webdav.WebDavClient
import com.resdownload.android.domain.webdav.WebDavException
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.domain.webdav.WebDavReadResponse

data class DownloadPreparation(
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val supportRange: Boolean,
    val etag: String?,
    val lastModified: String?,
    val mimeType: String?,
)

data class DownloadTransferResult(
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val supportRange: Boolean,
    val etag: String?,
    val lastModified: String?,
    val mimeType: String?,
)

class DownloadIntegrityException(message: String) : IOException(message)

class DownloadTransferEngine @Inject constructor(
    private val webDavClient: WebDavClient,
    private val fileStore: DownloadFileStore,
) {
    suspend fun transfer(
        task: DownloadTask,
        onPreparation: suspend (DownloadPreparation) -> Unit,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadTransferResult = withContext(Dispatchers.IO) {
        val path = WebDavPath.parseDecoded(task.remotePath)
        require(!path.isRoot) { "The WebDAV root cannot be downloaded" }
        fileStore.ensureTaskDirectory(task)

        // Resume logic (same approach as Windows port): check for an existing .part
        // file, try a Range request, and let the server's 206-vs-200 response decide
        // whether we append or restart. No HEAD pre-flight or etag/lastModified
        // validator — 123pan WebDAV HEAD often omits validator headers yet GET Range
        // works fine.
        var offset = fileStore.partialFile(task).takeIf { it.isFile }?.length() ?: 0L
        val range = if (offset > 0L) WebDavByteRange(offset) else null

        var response = if (range != null) {
            try {
                webDavClient.get(path, range, null)
            } catch (_: WebDavException.RangeNotSatisfiable) {
                fileStore.truncatePartial(task)
                offset = 0L
                webDavClient.get(path)
            }
        } else {
            webDavClient.get(path)
        }

        val isPartial = response.statusCode == 206
        // If server ignored Range and returned 200, discard stale partial bytes
        if (offset > 0L && !isPartial) {
            response.close()
            fileStore.truncatePartial(task)
            offset = 0L
            response = webDavClient.get(path)
        }

        val totalBytes = if (isPartial) {
            response.contentRange?.totalLength ?: response.metadata.contentLength
        } else {
            response.metadata.contentLength ?: response.contentRange?.totalLength
        }
        val mimeType = response.metadata.contentType ?: task.mimeType
        val etag = response.metadata.etag
        val lastModified = response.metadata.lastModified
        val supportRange = response.metadata.acceptsByteRanges || isPartial

        val startBytes = if (isPartial) offset else 0L
        onPreparation(
            DownloadPreparation(
                totalBytes = totalBytes,
                downloadedBytes = startBytes,
                supportRange = supportRange,
                etag = etag,
                lastModified = lastModified,
                mimeType = mimeType,
            ),
        )

        // Short-circuit: partial file already covers the whole resource
        if (isPartial && totalBytes != null && offset >= totalBytes) {
            currentCoroutineContext().ensureActive()
            response.close()
            fileStore.finalize(task)
            return@withContext DownloadTransferResult(
                totalBytes = totalBytes,
                downloadedBytes = offset,
                supportRange = supportRange,
                etag = etag,
                lastModified = lastModified,
                mimeType = mimeType,
            )
        }

        response.use { body ->
            val append = isPartial
            var downloadedBytes = startBytes
            FileOutputStream(fileStore.partialFile(task), append).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = try {
                        runInterruptible(Dispatchers.IO) { body.stream.read(buffer) }
                    } catch (error: IOException) {
                        throw WebDavException.Network(error)
                    }
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    downloadedBytes = try {
                        Math.addExact(downloadedBytes, read.toLong())
                    } catch (error: ArithmeticException) {
                        throw DownloadIntegrityException("Downloaded byte count overflowed")
                    }
                    if (totalBytes != null && downloadedBytes > totalBytes) {
                        throw DownloadIntegrityException("Downloaded data exceeded the expected size")
                    }
                    onProgress(downloadedBytes, totalBytes)
                }
                output.fd.sync()
            }

            val requiredEnd = body.contentRange?.endInclusive?.plus(1L)
            if (requiredEnd != null && downloadedBytes != requiredEnd) {
                throw DownloadIntegrityException("Partial response ended at an unexpected offset")
            }
            if (totalBytes != null && downloadedBytes != totalBytes) {
                throw DownloadIntegrityException("Downloaded file size does not match the server metadata")
            }
            currentCoroutineContext().ensureActive()
            fileStore.finalize(task)

            val metadata = body.metadata
            DownloadTransferResult(
                totalBytes = totalBytes ?: downloadedBytes,
                downloadedBytes = downloadedBytes,
                supportRange = supportRange,
                etag = metadata.etag ?: etag,
                lastModified = metadata.lastModified ?: lastModified,
                mimeType = metadata.contentType ?: mimeType,
            )
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
