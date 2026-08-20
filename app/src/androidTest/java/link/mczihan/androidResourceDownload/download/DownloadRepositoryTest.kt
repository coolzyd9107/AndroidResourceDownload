package link.mczihan.androidResourceDownload.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.download.DownloadDatabase
import link.mczihan.androidResourceDownload.data.download.DownloadFileStore
import link.mczihan.androidResourceDownload.data.download.DownloadRepository
import link.mczihan.androidResourceDownload.data.download.DownloadTaskEntity
import link.mczihan.androidResourceDownload.data.download.PublicDownloadStore
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: DownloadDatabase
    private lateinit var fileStore: DownloadFileStore
    private lateinit var publicStore: PublicDownloadStore
    private lateinit var repository: DownloadRepository
    private lateinit var privateRoot: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        privateRoot = File(context.cacheDir, "repository-test-${UUID.randomUUID()}")
        fileStore = DownloadFileStore(privateRoot)
        publicStore = PublicDownloadStore(context)
        repository = DownloadRepository(database.downloadTaskDao(), fileStore, publicStore)
    }

    @After
    fun tearDown() {
        database.close()
        privateRoot.deleteRecursively()
    }

    @Test
    fun deletingSuccessRemovesPublicFileAndRow() = runBlocking {
        val task = task(DownloadStatus.SUCCESS)
        val source = File(context.cacheDir, "${task.id}.txt").apply { writeText("complete") }
        var publicUri: String? = null
        try {
            val stage = publicStore.create(task, task.mimeType)
            publicUri = stage
            publicUri = publicStore.write(task, stage, source, task.mimeType)
            val stored = task.copy(publicUri = publicUri)
            database.downloadTaskDao().insert(DownloadTaskEntity.fromDomain(stored))

            assertTrue(repository.deleteTerminal(task.ownerId, task.id))
            assertFalse(publicStore.exists(publicUri))
            assertNull(database.downloadTaskDao().findById(task.ownerId, task.id))
            publicUri = null
        } finally {
            publicUri?.let { publicStore.delete(it) }
            source.delete()
        }
    }

    @Test
    fun deletingSuccessWithAlreadyMissingFileRemovesRow() = runBlocking {
        val task = task(DownloadStatus.SUCCESS)
        val source = File(context.cacheDir, "${task.id}.txt").apply { writeText("complete") }
        val stage = publicStore.create(task, task.mimeType)
        val publicUri = publicStore.write(task, stage, source, task.mimeType)
        assertTrue(publicStore.delete(publicUri))
        database.downloadTaskDao().insert(
            DownloadTaskEntity.fromDomain(task.copy(publicUri = publicUri)),
        )

        assertTrue(repository.deleteTerminal(task.ownerId, task.id))
        assertNull(database.downloadTaskDao().findById(task.ownerId, task.id))
        source.delete()
        Unit
    }

    @Test
    fun deletingFailedTaskCleansPrivateResumeDataAndRow() = runBlocking {
        val task = task(DownloadStatus.FAILED)
        fileStore.ensureTaskDirectory(task)
        fileStore.partialFile(task).writeText("partial")
        database.downloadTaskDao().insert(DownloadTaskEntity.fromDomain(task))

        assertTrue(repository.deleteTerminal(task.ownerId, task.id))
        assertFalse(fileStore.partialFile(task).exists())
        assertNull(database.downloadTaskDao().findById(task.ownerId, task.id))
    }

    private fun task(status: DownloadStatus): DownloadTask {
        val id = UUID.randomUUID().toString()
        return DownloadTask(
            id = id,
            ownerId = "owner",
            fileName = "$id.txt",
            remotePath = "/$id.txt",
            storageName = "$id.txt",
            mimeType = "text/plain",
            status = status,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}
