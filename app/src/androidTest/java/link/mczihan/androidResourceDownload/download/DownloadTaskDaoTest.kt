package link.mczihan.androidResourceDownload.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.download.DownloadDatabase
import link.mczihan.androidResourceDownload.data.download.DownloadTaskEntity
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadTaskDaoTest {
    private lateinit var database: DownloadDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletesOnlyTerminalTasksForTheirOwner() = runBlocking {
        val dao = database.downloadTaskDao()
        val terminal = listOf(
            DownloadStatus.SUCCESS,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
        terminal.forEach { status ->
            val task = task(status)
            dao.insert(DownloadTaskEntity.fromDomain(task))

            assertEquals(1, dao.deleteTerminal(task.ownerId, task.id))
            assertNull(dao.findById(task.ownerId, task.id))
        }

        val active = task(DownloadStatus.PAUSED)
        dao.insert(DownloadTaskEntity.fromDomain(active))
        assertEquals(0, dao.deleteTerminal(active.ownerId, active.id))
        assertEquals(0, dao.deleteTerminal("another-owner", active.id))
        assertNotNull(dao.findById(active.ownerId, active.id))
    }

    private fun task(status: DownloadStatus): DownloadTask = DownloadTask(
        id = UUID.randomUUID().toString(),
        ownerId = "owner",
        fileName = "file.txt",
        remotePath = "/file.txt",
        storageName = "file.txt",
        status = status,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
