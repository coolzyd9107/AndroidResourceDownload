package link.mczihan.androidResourceDownload.upload

import link.mczihan.androidResourceDownload.data.upload.UploadTaskEntity
import link.mczihan.androidResourceDownload.domain.model.UploadStatus
import link.mczihan.androidResourceDownload.domain.model.UploadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadTaskEntityTest {
    @Test
    fun preservesSourcePermissionAndFolderMetadataWhenMappingTask() {
        val task = UploadTask(
            id = "task-id",
            ownerId = "owner",
            batchId = "batch",
            fileName = "notes.txt",
            relativePath = "selected/sub/notes.txt",
            destinationRoot = "/target",
            remotePath = "/target/selected/sub/notes.txt",
            sourceUri = "content://provider/document/notes",
            permissionUri = "content://provider/tree/root",
            isDirectory = false,
            isTreeUpload = true,
            mimeType = "text/plain",
            totalBytes = 42L,
            uploadedBytes = 21L,
            status = UploadStatus.RUNNING,
            committing = true,
            queueOrder = 3,
            pathDepth = 4,
            createdAt = 1L,
            updatedAt = 2L,
        )

        assertEquals(task, UploadTaskEntity.fromDomain(task).toDomain())
    }
}
