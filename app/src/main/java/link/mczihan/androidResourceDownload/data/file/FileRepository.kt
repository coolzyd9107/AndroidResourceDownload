package link.mczihan.androidResourceDownload.data.file

import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath

interface FileRepository {
    suspend fun list(path: WebDavPath): List<FileNode>
}
