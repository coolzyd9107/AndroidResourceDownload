package link.mczihan.androidResourceDownload.download

import link.mczihan.androidResourceDownload.data.download.DownloadTaskEntity
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadTaskEntityTest {
    @Test
    fun preservesPublicUriWhenMappingTask() {
        val task = DownloadTask(
            id = "task-id",
            ownerId = "owner",
            fileName = "file.txt",
            remotePath = "/file.txt",
            storageName = "file.txt",
            publicUri = "content://media/external/downloads/42",
            mimeType = "text/plain",
            status = DownloadStatus.SUCCESS,
            createdAt = 1L,
            updatedAt = 2L,
        )

        val mapped = DownloadTaskEntity.fromDomain(task).toDomain()

        assertEquals(task, mapped)
    }
}
