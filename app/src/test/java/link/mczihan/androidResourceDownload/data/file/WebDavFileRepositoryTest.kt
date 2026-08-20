package link.mczihan.androidResourceDownload.data.file

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.webdav.OkHttpWebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredential
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPermission
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavFileRepositoryTest {
    @Test
    fun uploadStagesThenMovesToFinalDestination() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))
            val repository = repository(server)

            runBlocking {
                repository.upload(
                    WebDavPath.parseDecoded("/folder/file.txt"),
                    WebDavUpload(
                        contentLength = 5L,
                        openStream = { ByteArrayInputStream("hello".toByteArray()) },
                    ),
                )
            }

            val upload = server.takeRequest()
            val commit = server.takeRequest()
            assertEquals("PUT", upload.method)
            assertTrue(upload.path.orEmpty().startsWith("/root/folder/.ard-upload-"))
            assertEquals("MOVE", commit.method)
            assertEquals(server.url("/root/folder/file.txt").toString(), commit.getHeader("Destination"))
            assertEquals("F", commit.getHeader("Overwrite"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun failedCommitDeletesTemporaryUpload() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(412))
            server.enqueue(MockResponse().setResponseCode(204))
            val repository = repository(server)

            val error = runCatching {
                runBlocking {
                    repository.upload(
                        WebDavPath.parseDecoded("/file.txt"),
                        WebDavUpload(openStream = { ByteArrayInputStream(byteArrayOf(1)) }),
                    )
                }
            }.exceptionOrNull()

            assertTrue(error is WebDavException.PreconditionFailed)
            val upload = server.takeRequest()
            server.takeRequest()
            val cleanup = server.takeRequest()
            assertEquals("DELETE", cleanup.method)
            assertEquals(upload.path, cleanup.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun transferRejectsAncestorDestinationBeforeNetwork() {
        val server = MockWebServer()
        server.start()
        try {
            val repository = repository(server)

            val error = runCatching {
                runBlocking {
                    repository.move(
                        WebDavPath.parseDecoded("/folder/file.txt"),
                        WebDavPath.parseDecoded("/folder"),
                    )
                }
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun confirmedOverwriteRejectsCollectionTargetBeforeWrite() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(propFindResponse("/root/existing/", collection = true)),
            )
            val repository = repository(server)

            val error = runCatching {
                runBlocking {
                    repository.move(
                        source = WebDavPath.parseDecoded("/source.txt"),
                        destination = WebDavPath.parseDecoded("/existing"),
                        overwrite = true,
                    )
                }
            }.exceptionOrNull()

            assertTrue(error is WebDavException.CollectionOverwriteDenied)
            val probe = server.takeRequest()
            assertEquals("PROPFIND", probe.method)
            assertEquals("/root/existing", probe.path)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun confirmedOverwriteUsesNoOverwriteWhenTargetDisappeared() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(201))
            val repository = repository(server)

            runBlocking {
                repository.move(
                    source = WebDavPath.parseDecoded("/source.txt"),
                    destination = WebDavPath.parseDecoded("/existing.txt"),
                    overwrite = true,
                )
            }

            assertEquals("/root/existing.txt", server.takeRequest().path)
            assertEquals("/root/existing.txt/", server.takeRequest().path)
            val move = server.takeRequest()
            assertEquals("MOVE", move.method)
            assertEquals("F", move.getHeader("Overwrite"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun staleUploadTemporaryRemainsVisibleForAdministratorCleanup() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(
                        propFindResponse(
                            "/root/.ard-upload-00000000-0000-0000-0000-000000000000.part",
                            collection = false,
                        ),
                    ),
            )
            val repository = repository(server)

            val files = runBlocking { repository.list(WebDavPath.root()) }

            assertEquals(1, files.size)
            assertEquals(
                ".ard-upload-00000000-0000-0000-0000-000000000000.part",
                files.single().name,
            )
            assertTrue(files.single().isUploadTemporary)
        } finally {
            server.shutdown()
        }
    }

    private fun repository(server: MockWebServer): WebDavFileRepository = WebDavFileRepository(
        OkHttpWebDavClient(
            endpoint = server.url("/root/"),
            credentialProvider = writerCredentialProvider(),
        ),
    )

    private fun writerCredentialProvider(): WebDavCredentialProvider =
        object : WebDavCredentialProvider {
            override suspend fun acquire() = CredentialLease(
                WebDavCredential("admin", "secret", WebDavPermission.READ_WRITE),
                generation = 1L,
            )

            override suspend fun invalidate(generation: Long) = Unit
            override suspend fun clear() = Unit
        }

    private fun propFindResponse(href: String, collection: Boolean): String =
        """<?xml version="1.0" encoding="UTF-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>$href</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>${href.trimEnd('/').substringAfterLast('/')}</D:displayname>
                    <D:resourcetype>${if (collection) "<D:collection/>" else ""}</D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
}
