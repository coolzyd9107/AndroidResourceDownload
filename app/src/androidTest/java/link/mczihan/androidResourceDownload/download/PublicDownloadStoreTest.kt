package link.mczihan.androidResourceDownload.download

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.download.PublicDownloadStore
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicDownloadStoreTest {
    @Test
    fun createsPublishesReadsAndDeletesMediaStoreDownload() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PublicDownloadStore(context)
        val id = UUID.randomUUID().toString()
        val bytes = "android-resource-download".toByteArray()
        val source = File(context.cacheDir, "$id.txt").apply { writeBytes(bytes) }
        val task = DownloadTask(
            id = id,
            ownerId = "instrumented-test",
            fileName = "$id.txt",
            remotePath = "/$id.txt",
            storageName = "$id.txt",
            mimeType = "text/plain",
            status = DownloadStatus.RUNNING,
            createdAt = 1L,
            updatedAt = 1L,
        )
        var destination: String? = null

        try {
            val stage = store.create(task, task.mimeType)
            destination = stage
            destination = store.write(task, stage, source, task.mimeType)

            assertTrue(store.exists(destination))
            val actual = context.contentResolver.openInputStream(Uri.parse(destination))?.use {
                it.readBytes()
            }
            assertArrayEquals(bytes, actual)
            assertTrue(store.delete(destination))
            assertFalse(store.exists(destination))
            assertTrue(store.delete(destination))
            destination = null
        } finally {
            destination?.let { store.delete(it) }
            source.delete()
        }
    }
}
