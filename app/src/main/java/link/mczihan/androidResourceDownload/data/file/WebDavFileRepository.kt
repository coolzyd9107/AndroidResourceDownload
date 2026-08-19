package link.mczihan.androidResourceDownload.data.file

import javax.inject.Inject
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavResource

class WebDavFileRepository @Inject constructor(
    private val webDavClient: WebDavClient,
) : FileRepository {
    override suspend fun list(path: WebDavPath): List<FileNode> =
        webDavClient.propFind(path).asSequence()
            .filterNot { it.path == path }
            .map(WebDavResource::toFileNode)
            .sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
}

private fun WebDavResource.toFileNode(): FileNode = FileNode(
    name = displayName,
    path = path.toString(),
    isDirectory = isCollection,
    size = contentLength,
    lastModified = lastModifiedEpochMillis,
    mimeType = contentType,
    etag = etag,
)
