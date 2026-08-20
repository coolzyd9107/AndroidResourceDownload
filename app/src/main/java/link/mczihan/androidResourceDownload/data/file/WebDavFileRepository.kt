package link.mczihan.androidResourceDownload.data.file

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavResource
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload

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

    override suspend fun upload(
        path: WebDavPath,
        upload: WebDavUpload,
        overwrite: Boolean,
        onCommitting: () -> Unit,
    ) {
        requireMutablePath(path)
        val parent = WebDavPath.fromDecodedSegments(path.decodedSegments.dropLast(1))
        val temporary = parent.child(".ard-upload-${UUID.randomUUID()}.part")
        activeUploads += temporary
        try {
            webDavClient.put(temporary, upload, overwrite = false)
            val commitOverwrite = resolvedOverwrite(path, overwrite)
            onCommitting()
            webDavClient.move(temporary, path, commitOverwrite)
        } catch (error: CancellationException) {
            withContext(NonCancellable) { runCatching { webDavClient.delete(temporary) } }
            throw error
        } catch (error: Exception) {
            withContext(NonCancellable) { runCatching { webDavClient.delete(temporary) } }
            throw error
        } finally {
            activeUploads -= temporary
        }
    }

    override suspend fun isCollection(path: WebDavPath): Boolean? =
        webDavClient.propFindResource(path)?.requireKnownResourceType()?.isCollection

    override suspend fun createDirectory(path: WebDavPath) {
        requireMutablePath(path)
        webDavClient.makeCollection(path)
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
