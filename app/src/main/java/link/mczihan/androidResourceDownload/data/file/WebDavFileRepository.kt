package link.mczihan.androidResourceDownload.data.file

import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.FilePreviewContent
import link.mczihan.androidResourceDownload.domain.model.FilePreviewFormat
import link.mczihan.androidResourceDownload.domain.model.previewFormat
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavResource
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload
import link.mczihan.androidResourceDownload.domain.webdav.strongEntityTagOrNull

class WebDavFileRepository @Inject constructor(
    private val webDavClient: WebDavClient,
) : FileRepository {
    private val activeUploads: MutableSet<WebDavPath> = ConcurrentHashMap.newKeySet()

    override suspend fun list(path: WebDavPath): List<FileNode> =
        webDavClient.propFind(path).asSequence()
            .filterNot { it.path == path }
            .filterNot { it.path in activeUploads }
            .map(WebDavResource::toFileNode)
            .sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()

    override suspend fun preview(file: FileNode): FilePreviewContent =
        loadWebDavFilePreview(webDavClient, file)

    override suspend fun updateText(
        file: FileNode,
        original: FilePreviewContent.Text,
        text: String,
    ) {
        require(
            file.previewFormat() == FilePreviewFormat.PLAIN_TEXT &&
                !original.truncated && original.encodingEditable,
        ) {
            "Only complete plain-text previews can be edited"
        }
        require(isPlainTextRepresentation(original.contentType)) {
            "Preview response is not an editable plain-text representation"
        }
        val entityTag = requireNotNull(original.entityTag.strongEntityTagOrNull()) {
            "A strong preview ETag is required for editing"
        }
        val bytes = encodeEditedText(text, original.charsetName, original.hasBom)
        if (bytes.size > MAX_EDITED_TEXT_BYTES) {
            throw WebDavException.ResponseTooLarge(MAX_EDITED_TEXT_BYTES.toLong())
        }
        val path = WebDavPath.parseDecoded(file.path)
        requireMutablePath(path)
        webDavClient.put(
            path = path,
            upload = WebDavUpload(
                contentLength = bytes.size.toLong(),
                contentType = original.contentType ?: "text/plain; charset=${original.charsetName}",
                openStream = { ByteArrayInputStream(bytes) },
            ),
            overwrite = true,
            ifMatch = entityTag,
        )
    }

    override suspend fun upload(
        path: WebDavPath,
        upload: WebDavUpload,
        overwrite: Boolean,
        stagingKey: String?,
        onCommitting: suspend () -> Unit,
        onCommitted: suspend () -> Unit,
        onCommitFailed: suspend (Exception) -> Unit,
    ) {
        requireMutablePath(path)
        val parent = WebDavPath.fromDecodedSegments(path.decodedSegments.dropLast(1))
        val temporary = uploadTemporaryPath(parent, stagingKey)
        activeUploads += temporary
        var committing = false
        var commitFailureRecorded = false
        try {
            webDavClient.put(temporary, upload, overwrite = stagingKey != null)
            val commitOverwrite = resolvedOverwrite(path, overwrite)
            onCommitting()
            committing = true
            withContext(NonCancellable) {
                try {
                    webDavClient.move(temporary, path, commitOverwrite)
                } catch (error: Exception) {
                    if (error is WebDavException.Network) {
                        throw UploadCommitUncertainException(error)
                    }
                    onCommitFailed(error)
                    commitFailureRecorded = true
                    throw error
                }
                try {
                    onCommitted()
                } catch (error: Exception) {
                    throw UploadCommitUncertainException(error)
                }
            }
        } catch (error: CancellationException) {
            if (!committing || commitFailureRecorded) {
                withContext(NonCancellable) { runCatching { webDavClient.delete(temporary) } }
            }
            throw error
        } catch (error: Exception) {
            if (!committing || commitFailureRecorded) {
                withContext(NonCancellable) { runCatching { webDavClient.delete(temporary) } }
            }
            throw error
        } finally {
            activeUploads -= temporary
        }
    }

    override suspend fun recoverUpload(
        path: WebDavPath,
        stagingKey: String,
        wasCommitting: Boolean,
    ): UploadRecoveryResult {
        requireMutablePath(path)
        val parent = WebDavPath.fromDecodedSegments(path.decodedSegments.dropLast(1))
        val temporary = uploadTemporaryPath(parent, stagingKey)
        val temporaryResource = webDavClient.propFindResource(temporary)
        val destinationResource = if (wasCommitting) webDavClient.propFindResource(path) else null
        if (wasCommitting &&
            destinationResource?.requireKnownResourceType()?.isCollection == false &&
            temporaryResource == null
        ) {
            return UploadRecoveryResult.COMMITTED
        }
        if (temporaryResource != null) {
            webDavClient.delete(temporary)
        }
        return UploadRecoveryResult.RETRY
    }

    override suspend fun isCollection(path: WebDavPath): Boolean? =
        webDavClient.propFindResource(path)?.requireKnownResourceType()?.isCollection

    override suspend fun createDirectory(path: WebDavPath) {
        requireMutablePath(path)
        webDavClient.makeCollection(path)
    }

    override suspend fun ensureDirectory(path: WebDavPath) {
        if (path.isRoot) return
        requireMutablePath(path)
        try {
            webDavClient.makeCollection(path)
        } catch (error: WebDavException) {
            if (error !is WebDavException.PreconditionFailed && error !is WebDavException.Conflict) {
                throw error
            }
            if (webDavClient.propFindResource(path)?.requireKnownResourceType()?.isCollection != true) {
                throw error
            }
        }
    }

    override suspend fun move(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean,
        sourceIsCollection: Boolean,
        sourceEtag: String?,
    ) {
        validateTransfer(source, destination)
        webDavClient.move(
            source,
            destination,
            resolvedOverwrite(destination, overwrite),
            sourceIsCollection,
            sourceEtag,
        )
    }

    override suspend fun copy(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean,
        sourceIsCollection: Boolean,
        sourceEtag: String?,
    ) {
        validateTransfer(source, destination)
        webDavClient.copy(
            source,
            destination,
            resolvedOverwrite(destination, overwrite),
            sourceIsCollection,
            sourceEtag,
        )
    }

    override suspend fun delete(path: WebDavPath, isCollection: Boolean, etag: String?) {
        requireMutablePath(path)
        webDavClient.delete(path, isCollection, etag)
    }

    private suspend fun resolvedOverwrite(destination: WebDavPath, requested: Boolean): Boolean {
        if (!requested) return false
        val target = webDavClient.propFindResource(destination) ?: return false
        if (target.requireKnownResourceType().isCollection) {
            throw WebDavException.CollectionOverwriteDenied()
        }
        return true
    }

    private fun requireMutablePath(path: WebDavPath) {
        require(!path.isRoot) { "The WebDAV root cannot be modified" }
    }

    private fun uploadTemporaryPath(parent: WebDavPath, stagingKey: String?): WebDavPath {
        val normalizedKey = stagingKey?.let { UUID.fromString(it).toString() }
            ?: UUID.randomUUID().toString()
        return parent.child(".ard-upload-$normalizedKey.part")
    }

    private fun validateTransfer(source: WebDavPath, destination: WebDavPath) {
        requireMutablePath(source)
        requireMutablePath(destination)
        require(source != destination) { "Source and destination must differ" }
        val sourceSegments = source.decodedSegments
        val destinationSegments = destination.decodedSegments
        require(
            destinationSegments.size <= sourceSegments.size ||
                destinationSegments.take(sourceSegments.size) != sourceSegments,
        ) { "A resource cannot be moved or copied into itself" }
        require(
            sourceSegments.size <= destinationSegments.size ||
                sourceSegments.take(destinationSegments.size) != destinationSegments,
        ) { "A resource cannot replace one of its ancestor directories" }
    }
}

private fun WebDavResource.requireKnownResourceType(): WebDavResource {
    if (!resourceTypeKnown) {
        throw WebDavException.InvalidResponse("WebDAV response omitted the target resource type")
    }
    return this
}

private fun WebDavResource.toFileNode(): FileNode = FileNode(
    name = displayName,
    path = path.toString(),
    isDirectory = isCollection,
    size = contentLength,
    lastModified = lastModifiedEpochMillis,
    mimeType = contentType,
    etag = etag,
    isUploadTemporary = path.name?.matches(UPLOAD_TEMPORARY_NAME) == true,
)

private val UPLOAD_TEMPORARY_NAME = Regex(
    "^\\.ard-upload-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
        "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.part$",
)

private const val MAX_EDITED_TEXT_BYTES = 512 * 1024
