package link.mczihan.androidResourceDownload.data.file

import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.FilePreviewContent
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload

interface FileRepository {
    suspend fun list(path: WebDavPath): List<FileNode>

    suspend fun preview(file: FileNode): FilePreviewContent {
        throw UnsupportedOperationException("Preview is not supported")
    }

    suspend fun updateText(
        file: FileNode,
        original: FilePreviewContent.Text,
        text: String,
    ) {
        throw UnsupportedOperationException("Text editing is not supported")
    }

    suspend fun upload(
        path: WebDavPath,
        upload: WebDavUpload,
        overwrite: Boolean = false,
        stagingKey: String? = null,
        onCommitting: suspend () -> Unit = {},
        onCommitted: suspend () -> Unit = {},
        onCommitFailed: suspend (Exception) -> Unit = {},
    ) {
        throw UnsupportedOperationException("Upload is not supported")
    }

    suspend fun recoverUpload(
        path: WebDavPath,
        stagingKey: String,
        wasCommitting: Boolean,
    ): UploadRecoveryResult = UploadRecoveryResult.RETRY

    suspend fun isCollection(path: WebDavPath): Boolean? = null

    suspend fun createDirectory(path: WebDavPath) {
        throw UnsupportedOperationException("Directory creation is not supported")
    }

    suspend fun ensureDirectory(path: WebDavPath) {
        createDirectory(path)
    }

    suspend fun move(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean = false,
        sourceIsCollection: Boolean = false,
        sourceEtag: String? = null,
    ) {
        throw UnsupportedOperationException("Move is not supported")
    }

    suspend fun copy(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean = false,
        sourceIsCollection: Boolean = false,
        sourceEtag: String? = null,
    ) {
        throw UnsupportedOperationException("Copy is not supported")
    }

    suspend fun delete(
        path: WebDavPath,
        isCollection: Boolean = false,
        etag: String? = null,
    ) {
        throw UnsupportedOperationException("Delete is not supported")
    }
}

enum class UploadRecoveryResult {
    COMMITTED,
    RETRY,
}

class UploadCommitUncertainException(cause: Exception) :
    Exception("The remote upload commit succeeded but local confirmation failed", cause)
