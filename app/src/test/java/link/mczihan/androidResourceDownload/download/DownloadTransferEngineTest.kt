package link.mczihan.androidResourceDownload.download

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.download.DownloadFileStore
import link.mczihan.androidResourceDownload.data.download.DownloadPreparation
import link.mczihan.androidResourceDownload.data.download.DownloadTransferEngine
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.webdav.WebDavByteRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavContentRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavDepth
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavMetadata
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavReadResponse
import link.mczihan.androidResourceDownload.domain.webdav.WebDavResource
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadTransferEngineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesFullResponseAndFinalizesFile() = runBlocking {
        val store = DownloadFileStore(temporaryFolder.newFolder("full"))
        val task = task("task-full")
        val client = FakeWebDavClient(
            headMetadata = metadata(length = 5L, acceptsRanges = false),
        ) { range, _ ->
            assertNull(range)
            response(status = 200, body = "hello", length = 5L)
        }
        val preparations = mutableListOf<DownloadPreparation>()

        val result = DownloadTransferEngine(client, store).transfer(
            task = task,
            onPreparation = preparations::add,
            onProgress = { _, _ -> },
        )

        assertArrayEquals("hello".toByteArray(), store.finalFile(task).readBytes())
        assertFalse(store.partialFile(task).exists())
        assertEquals(5L, result.downloadedBytes)
        assertEquals(0L, preparations.single().downloadedBytes)
    }

    @Test
    fun resumesOnlyWithMatchingValidatorAndContentRange() = runBlocking {
        val store = DownloadFileStore(temporaryFolder.newFolder("resume"))
        val task = task("task-resume", etag = ETAG)
        store.ensureTaskDirectory(task)
        store.partialFile(task).writeText("hel")
        val client = FakeWebDavClient(
            headMetadata = metadata(length = 5L, etag = ETAG, acceptsRanges = true),
        ) { range, ifRange ->
            assertEquals(3L, range?.start)
            assertEquals(ETAG, ifRange)
            response(
                status = 206,
                body = "lo",
                length = 2L,
                etag = ETAG,
                contentRange = WebDavContentRange(3L, 4L, 5L),
            )
        }

        val result = DownloadTransferEngine(client, store).transfer(task, {}, { _, _ -> })

        assertEquals("hello", store.finalFile(task).readText())
        assertTrue(result.supportRange)
        assertEquals(3L, client.requests.single().range?.start)
    }

    @Test
    fun rangeFallbackTo200TruncatesExistingPartial() = runBlocking {
        val store = DownloadFileStore(temporaryFolder.newFolder("fallback"))
        val task = task("task-fallback", etag = ETAG)
        store.ensureTaskDirectory(task)
        store.partialFile(task).writeText("old")
        val client = FakeWebDavClient(
            headMetadata = metadata(length = 5L, etag = ETAG, acceptsRanges = true),
        ) { _, _ -> response(status = 200, body = "fresh", length = 5L, etag = NEW_ETAG) }

        val result = DownloadTransferEngine(client, store).transfer(task, {}, { _, _ -> })

        assertEquals("fresh", store.finalFile(task).readText())
        assertEquals(NEW_ETAG, result.etag)
    }

    @Test
    fun changedValidatorDiscardsPartialBeforeGet() = runBlocking {
        val store = DownloadFileStore(temporaryFolder.newFolder("changed"))
        val task = task("task-changed", etag = ETAG)
        store.ensureTaskDirectory(task)
        store.partialFile(task).writeText("old")
        val client = FakeWebDavClient(
            headMetadata = metadata(length = 5L, etag = NEW_ETAG, acceptsRanges = true),
        ) { range, ifRange ->
            assertNull(range)
            assertNull(ifRange)
            response(status = 200, body = "fresh", length = 5L, etag = NEW_ETAG)
        }

        DownloadTransferEngine(client, store).transfer(task, {}, { _, _ -> })

        assertEquals("fresh", store.finalFile(task).readText())
    }

    @Test
    fun completeValidatedPartialFinalizesWithoutAnotherGet() = runBlocking {
        val store = DownloadFileStore(temporaryFolder.newFolder("complete-partial"))
        val task = task("task-complete", etag = ETAG)
        store.ensureTaskDirectory(task)
        store.partialFile(task).writeText("hello")
        val client = FakeWebDavClient(
            headMetadata = metadata(length = 5L, etag = ETAG, acceptsRanges = true),
        ) { _, _ -> error("GET must not be called for a complete validated partial") }

        val result = DownloadTransferEngine(client, store).transfer(task, {}, { _, _ -> })

        assertEquals("hello", store.finalFile(task).readText())
        assertTrue(client.requests.isEmpty())
        assertEquals(5L, result.totalBytes)
    }

    @Test
    fun storageNameRespectsUtf8ByteLimitAndPreservesExtension() {
        val storageName = DownloadFileStore.storageNameFor("文".repeat(100) + ".pdf")

        assertTrue(storageName.endsWith(".pdf"))
        assertTrue(storageName.toByteArray(Charsets.UTF_8).size <= 200)
        assertTrue("$storageName.part".toByteArray(Charsets.UTF_8).size <= 255)
    }

    @Test
    fun responseBodyReadFailureIsReportedAsNetworkError() = runBlocking {
        val store = DownloadFileStore(temporaryFolder.newFolder("network-read"))
        val client = FakeWebDavClient(
            headMetadata = metadata(length = 5L, acceptsRanges = false),
        ) { _, _ ->
            WebDavReadResponse(
                statusCode = 200,
                metadata = metadata(length = 5L, acceptsRanges = false),
                contentRange = null,
                stream = object : InputStream() {
                    override fun read(): Int = throw IOException("connection reset")
                },
                closeAction = {},
            )
        }

        val error = runCatching {
            DownloadTransferEngine(client, store).transfer(task("task-network"), {}, { _, _ -> })
        }.exceptionOrNull()

        assertTrue(error is WebDavException.Network)
    }

    private fun task(id: String, etag: String? = null): DownloadTask = DownloadTask(
        id = id,
        ownerId = "owner",
        fileName = "file.txt",
        remotePath = "/file.txt",
        storageName = "file.txt",
        mimeType = "text/plain",
        status = DownloadStatus.RUNNING,
        etag = etag,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun metadata(
        length: Long?,
        etag: String? = null,
        acceptsRanges: Boolean,
    ): WebDavMetadata = WebDavMetadata(
        contentLength = length,
        lastModifiedEpochMillis = null,
        lastModified = LAST_MODIFIED,
        contentType = "text/plain",
        etag = etag,
        acceptsByteRanges = acceptsRanges,
    )

    private fun response(
        status: Int,
        body: String,
        length: Long,
        etag: String? = null,
        contentRange: WebDavContentRange? = null,
    ): WebDavReadResponse = WebDavReadResponse(
        statusCode = status,
        metadata = metadata(length, etag, acceptsRanges = status == 206),
        contentRange = contentRange,
        stream = ByteArrayInputStream(body.toByteArray()),
        closeAction = {},
    )

    private data class GetRequest(val range: WebDavByteRange?, val ifRange: String?)

    private class FakeWebDavClient(
        private val headMetadata: WebDavMetadata,
        private val responder: (WebDavByteRange?, String?) -> WebDavReadResponse,
    ) : WebDavClient {
        val requests = mutableListOf<GetRequest>()

        override suspend fun propFind(path: WebDavPath, depth: WebDavDepth): List<WebDavResource> =
            error("Not used")

        override suspend fun head(path: WebDavPath): WebDavMetadata = headMetadata

        override suspend fun get(
            path: WebDavPath,
            range: WebDavByteRange?,
            ifRange: String?,
        ): WebDavReadResponse {
            requests += GetRequest(range, ifRange)
            return responder(range, ifRange)
        }

        override suspend fun put(path: WebDavPath, upload: WebDavUpload) = error("Not used")

        override suspend fun makeCollection(path: WebDavPath) = error("Not used")

        override suspend fun delete(path: WebDavPath) = error("Not used")

        override suspend fun move(source: WebDavPath, destination: WebDavPath, overwrite: Boolean) =
            error("Not used")
    }

    private companion object {
        const val ETAG = "\"v1\""
        const val NEW_ETAG = "\"v2\""
        const val LAST_MODIFIED = "Wed, 21 Oct 2015 07:28:00 GMT"
    }
}
