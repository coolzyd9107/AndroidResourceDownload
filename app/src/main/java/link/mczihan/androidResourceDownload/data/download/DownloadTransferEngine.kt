package link.mczihan.androidResourceDownload.data.download

import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.webdav.WebDavByteRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavMetadata
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavReadResponse

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

        val head = webDavClient.head(path)
        val currentValidator = head.resumeValidator()
        val storedValidator = task.resumeValidator()
        var offset = fileStore.partialFile(task).takeIf { it.isFile }?.length() ?: 0L
        val canResume = offset > 0L &&
            head.acceptsByteRanges &&
            currentValidator != null &&
            currentValidator == storedValidator &&
            (head.contentLength == null || offset <= head.contentLength)
        if (offset > 0L && !canResume) {
            fileStore.truncatePartial(task)
            offset = 0L
        }

        onPreparation(head.toPreparation(task, offset))
        if (offset > 0L && head.contentLength == offset) {
            currentCoroutineContext().ensureActive()
            fileStore.finalize(task)
            return@withContext DownloadTransferResult(
                totalBytes = offset,
                downloadedBytes = offset,
                supportRange = head.acceptsByteRanges,
                etag = head.etag,
                lastModified = head.lastModified,
                mimeType = head.contentType ?: task.mimeType,
            )
        }
        var response = if (offset > 0L) {
            try {
                webDavClient.get(path, WebDavByteRange(offset), currentValidator)
            } catch (_: WebDavException.RangeNotSatisfiable) {
                fileStore.truncatePartial(task)
                offset = 0L
                onPreparation(head.toPreparation(task, offset))
                webDavClient.get(path)
            }
        } else {
            webDavClient.get(path)
        }

        if (offset > 0L && response.statusCode == 206 &&
            !response.metadata.matchesValidator(currentValidator)
        ) {
            response.close()
            fileStore.truncatePartial(task)
            offset = 0L
            onPreparation(head.toPreparation(task, offset))
            response = webDavClient.get(path)
        }

        if (offset > 0L && response.statusCode == 200) {
            fileStore.truncatePartial(task)
            offset = 0L
            onPreparation(response.metadata.toPreparation(task, offset))
        }

        response.use { body ->
            val totalBytes = body.totalBytes(head)
            val append = offset > 0L && body.statusCode == 206
            var downloadedBytes = if (append) offset else 0L
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
                supportRange = head.acceptsByteRanges || body.statusCode == 206,
                etag = metadata.etag ?: head.etag,
                lastModified = metadata.lastModified ?: head.lastModified,
                mimeType = metadata.contentType ?: head.contentType ?: task.mimeType,
            )
        }
    }

    private fun WebDavReadResponse.totalBytes(head: WebDavMetadata): Long? = when (statusCode) {
        206 -> contentRange?.totalLength ?: head.contentLength
        else -> metadata.contentLength ?: head.contentLength
    }

    private fun WebDavMetadata.toPreparation(task: DownloadTask, downloadedBytes: Long) =
        DownloadPreparation(
            totalBytes = contentLength ?: task.totalBytes,
            downloadedBytes = downloadedBytes,
            supportRange = acceptsByteRanges,
            etag = etag,
            lastModified = lastModified,
            mimeType = contentType ?: task.mimeType,
        )

    private fun DownloadTask.resumeValidator(): String? =
        etag?.takeIf(::isStrongEtag) ?: lastModified

    private fun WebDavMetadata.resumeValidator(): String? =
        etag?.takeIf(::isStrongEtag) ?: lastModified

    private fun WebDavMetadata.matchesValidator(validator: String?): Boolean = when {
        validator == null -> false
        isStrongEtag(validator) -> etag == validator
        else -> lastModified == validator
    }

    private fun isStrongEtag(value: String): Boolean =
        value.startsWith('"') && value.endsWith('"') && !value.startsWith("W/", ignoreCase = true)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
