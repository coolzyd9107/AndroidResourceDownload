package com.resdownload.android.upload

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import com.resdownload.android.data.upload.UploadDatabase
import com.resdownload.android.data.upload.UploadTaskEntity
import com.resdownload.android.domain.model.UploadStatus
import com.resdownload.android.domain.model.UploadTask
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UploadTaskDaoTest {
    private lateinit var database: UploadDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UploadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun claimsDirectoriesParentFirstBeforeFileClaims() = runBlocking {
        val dao = database.uploadTaskDao()
        dao.insertAll(
            listOf(
                entity(task("child", "/target/root/sub", directory = true, depth = 3, order = 1)),
                entity(task("file", "/target/root/sub/file.txt", directory = false, depth = 4, order = 2)),
                entity(task("root", "/target/root", directory = true, depth = 2, order = 0)),
            ),
        )

        assertEquals("root", dao.claimNextDirectory("owner", 10L)?.id)
        assertNull(dao.claimNextFile("owner", 11L))
        assertTrue(dao.completeAndDelete("root", 0L, 11L))
        assertNull(dao.status("root"))
        assertEquals("child", dao.claimNextDirectory("owner", 12L)?.id)
        assertTrue(dao.completeAndDelete("child", 0L, 13L))
        assertNull(dao.status("child"))
        assertEquals("file", dao.claimNextFile("owner", 14L)?.id)
        assertEquals(UploadStatus.RUNNING, dao.status("file"))
        assertTrue(dao.completeAndDelete("file", 10L, 15L))
        assertNull(dao.status("file"))
    }

    @Test
    fun committingFileCannotBeCancelled() = runBlocking {
        val dao = database.uploadTaskDao()
        dao.insertAll(listOf(entity(task("file", "/file.txt", directory = false, depth = 1))))
        assertNotNull(dao.claimNextFile("owner", 10L))
        assertEquals(1, dao.markCommitting("file", 11L))

        assertEquals(0, dao.cancelFile("owner", "file", 12L))
        assertEquals(UploadStatus.RUNNING, dao.status("file"))
    }

    @Test
    fun failedTreeBatchCannotClaimFilesUntilDirectoriesRetrySuccessfully() = runBlocking {
        val dao = database.uploadTaskDao()
        dao.insertAll(
            listOf(
                entity(task("root", "/target/root", directory = true, depth = 2, order = 0)),
                entity(task("file", "/target/root/file.txt", directory = false, depth = 3, order = 1)),
            ),
        )
        assertEquals("root", dao.claimNextDirectory("owner", 10L)?.id)
        dao.failDirectoryBatch("owner", "batch", "root", "folder failed", 11L)

        assertNull(dao.claimNextFile("owner", 12L))
        assertEquals(2, dao.retryFailedBatch("owner", "batch", 13L))
        assertNull(dao.claimNextFile("owner", 14L))
        assertEquals("root", dao.claimNextDirectory("owner", 15L)?.id)
        assertTrue(dao.completeAndDelete("root", 0L, 16L))
        assertNull(dao.status("root"))
        assertEquals("file", dao.claimNextFile("owner", 17L)?.id)
    }

    private fun task(
        id: String,
        path: String,
        directory: Boolean,
        depth: Int,
        order: Int = 0,
    ) = UploadTask(
        id = id,
        ownerId = "owner",
        batchId = "batch",
        fileName = path.substringAfterLast('/'),
        relativePath = path.removePrefix("/target/"),
        destinationRoot = "/target",
        remotePath = path,
        sourceUri = if (directory) null else "content://source/$id",
        permissionUri = if (directory) null else "content://source/$id",
        isDirectory = directory,
        isTreeUpload = true,
        mimeType = null,
        totalBytes = if (directory) null else 10L,
        queueOrder = order,
        pathDepth = depth,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun entity(task: UploadTask) = UploadTaskEntity.fromDomain(task)
}
