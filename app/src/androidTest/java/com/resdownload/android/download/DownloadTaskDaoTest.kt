package com.resdownload.android.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import com.resdownload.android.data.download.DownloadDatabase
import com.resdownload.android.data.download.DownloadTaskEntity
import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask
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

    @Test
    fun fourthTaskRemainsPendingUntilAClaimedTaskFinishes() = runBlocking {
        val dao = database.downloadTaskDao()
        val tasks = List(4) { index ->
            task(
                status = DownloadStatus.PENDING,
                id = "task-$index",
                createdAt = index.toLong() + 1L,
            )
        }
        tasks.forEach { dao.insert(DownloadTaskEntity.fromDomain(it)) }

        val claimedIds = List(3) {
            requireNotNull(dao.claimNext("owner", 10L + it)).id
        }

        assertEquals(listOf("task-0", "task-1", "task-2"), claimedIds)
        assertEquals(
            DownloadStatus.PENDING,
            dao.findById("owner", "task-3")?.status,
        )

        dao.fail("task-0", "finished", 20L)
        assertEquals("task-3", dao.claimNext("owner", 21L)?.id)
    }

    private fun task(
        status: DownloadStatus,
        id: String = UUID.randomUUID().toString(),
        createdAt: Long = 1L,
    ): DownloadTask = DownloadTask(
        id = id,
        ownerId = "owner",
        fileName = "$id.txt",
        remotePath = "/$id.txt",
        storageName = "$id.txt",
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
