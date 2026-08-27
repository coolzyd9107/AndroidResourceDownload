package com.resdownload.android.data.file

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import com.resdownload.android.data.webdav.OkHttpWebDavClient
import com.resdownload.android.domain.webdav.CredentialLease
import com.resdownload.android.domain.webdav.WebDavCredential
import com.resdownload.android.domain.webdav.WebDavCredentialProvider
import com.resdownload.android.domain.webdav.WebDavException
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.domain.webdav.WebDavPermission
import com.resdownload.android.domain.webdav.WebDavUpload
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            var committed = false
            val stagingKey = "11111111-1111-1111-1111-111111111111"

            runBlocking {
                repository.upload(
                    WebDavPath.parseDecoded("/folder/file.txt"),
                    WebDavUpload(
                        contentLength = 5L,
                        openStream = { ByteArrayInputStream("hello".toByteArray()) },
                    ),
                    stagingKey = stagingKey,
                    onCommitted = { committed = true },
                )
            }

            val upload = server.takeRequest()
            val commit = server.takeRequest()
            assertEquals("PUT", upload.method)
            assertEquals("/root/folder/.ard-upload-$stagingKey.part", upload.path)
            assertEquals("MOVE", commit.method)
            assertEquals(server.url("/root/folder/file.txt").toString(), commit.getHeader("Destination"))
            assertEquals("F", commit.getHeader("Overwrite"))
            assertTrue(committed)
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
    fun moveFailureIsRecordedBeforeTemporaryCleanup() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(412))
            server.enqueue(MockResponse().setResponseCode(204))
            val repository = repository(server)
            var requestsWhenFailureRecorded = -1

            val error = runCatching {
                runBlocking {
                    repository.upload(
                        path = WebDavPath.parseDecoded("/file.txt"),
                        upload = WebDavUpload(
                            openStream = { ByteArrayInputStream(byteArrayOf(1)) },
                        ),
                        stagingKey = "44444444-4444-4444-4444-444444444444",
                        onCommitFailed = { requestsWhenFailureRecorded = server.requestCount },
                    )
                }
            }.exceptionOrNull()

            assertTrue(error is WebDavException.PreconditionFailed)
            assertEquals(2, requestsWhenFailureRecorded)
            assertEquals(3, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun localCommitFailureLeavesTaskForRemoteReconciliation() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))
            val repository = repository(server)

            val error = runCatching {
                runBlocking {
                    repository.upload(
                        path = WebDavPath.parseDecoded("/file.txt"),
                        upload = WebDavUpload(
                            openStream = { ByteArrayInputStream(byteArrayOf(1)) },
                        ),
                        stagingKey = "55555555-5555-5555-5555-555555555555",
                        onCommitted = { error("database unavailable") },
                    )
                }
            }.exceptionOrNull()

            assertTrue(error is UploadCommitUncertainException)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun moveTransportFailureRemainsUncertainForReconciliation() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val repository = repository(server)
            var definitiveFailureRecorded = false

            val error = runCatching {
                runBlocking {
                    repository.upload(
                        path = WebDavPath.parseDecoded("/file.txt"),
                        upload = WebDavUpload(
                            openStream = { ByteArrayInputStream(byteArrayOf(1)) },
                        ),
                        stagingKey = "66666666-6666-6666-6666-666666666666",
                        onCommitFailed = { definitiveFailureRecorded = true },
                    )
                }
            }.exceptionOrNull()

            assertTrue(error is UploadCommitUncertainException)
            assertFalse(definitiveFailureRecorded)
            assertEquals(2, server.requestCount)
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
    fun sameDirectoryMoveRenamesFilesAndDirectories() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(204))
            val repository = repository(server)

            runBlocking {
                repository.move(
                    source = WebDavPath.parseDecoded("/资料/old name.txt"),
                    destination = WebDavPath.parseDecoded("/资料/新名称.txt"),
                    sourceEtag = "\"v1\"",
                )
                repository.move(
                    source = WebDavPath.parseDecoded("/资料/旧目录"),
                    destination = WebDavPath.parseDecoded("/资料/新目录"),
                    sourceIsCollection = true,
                )
            }

            val fileRename = server.takeRequest()
            assertEquals("MOVE", fileRename.method)
            assertEquals("/root/%E8%B5%84%E6%96%99/old%20name.txt", fileRename.path)
            assertEquals(
                server.url("/root/%E8%B5%84%E6%96%99/%E6%96%B0%E5%90%8D%E7%A7%B0.txt").toString(),
                fileRename.getHeader("Destination"),
            )
            assertEquals("F", fileRename.getHeader("Overwrite"))
            assertEquals("\"v1\"", fileRename.getHeader("If-Match"))

            val directoryRename = server.takeRequest()
            assertEquals("MOVE", directoryRename.method)
            assertEquals("/root/%E8%B5%84%E6%96%99/%E6%97%A7%E7%9B%AE%E5%BD%95/", directoryRename.path)
            assertEquals(
                server.url("/root/%E8%B5%84%E6%96%99/%E6%96%B0%E7%9B%AE%E5%BD%95/").toString(),
                directoryRename.getHeader("Destination"),
            )
            assertEquals("F", directoryRename.getHeader("Overwrite"))
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

    @Test
    fun createDirectoryUsesNativeMkcol() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            val repository = repository(server)

            runBlocking {
                repository.createDirectory(WebDavPath.parseDecoded("/资料/新目录"))
            }

            val request = server.takeRequest()
            assertEquals("MKCOL", request.method)
            assertEquals("/root/%E8%B5%84%E6%96%99/%E6%96%B0%E7%9B%AE%E5%BD%95/", request.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun ensureDirectoryAcceptsAnExistingCollection() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(405))
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(propFindResponse("/root/existing/", collection = true)),
            )
            val repository = repository(server)

            runBlocking {
                repository.ensureDirectory(WebDavPath.parseDecoded("/existing"))
            }

            assertEquals("MKCOL", server.takeRequest().method)
            assertEquals("PROPFIND", server.takeRequest().method)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun recoveryRecognizesCommittedMoveWhenTemporaryIsGone() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(propFindResponse("/root/final.txt", collection = false)),
            )
            val repository = repository(server)

            val result = runBlocking {
                repository.recoverUpload(
                    path = WebDavPath.parseDecoded("/final.txt"),
                    stagingKey = "22222222-2222-2222-2222-222222222222",
                    wasCommitting = true,
                )
            }

            assertEquals(UploadRecoveryResult.COMMITTED, result)
            assertEquals(3, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun recoveryDeletesInterruptedTaskTemporaryBeforeRetry() {
        val server = MockWebServer()
        server.start()
        try {
            val stagingKey = "33333333-3333-3333-3333-333333333333"
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(
                        propFindResponse(
                            "/root/.ard-upload-$stagingKey.part",
                            collection = false,
                        ),
                    ),
            )
            server.enqueue(MockResponse().setResponseCode(204))
            val repository = repository(server)

            val result = runBlocking {
                repository.recoverUpload(
                    path = WebDavPath.parseDecoded("/final.txt"),
                    stagingKey = stagingKey,
                    wasCommitting = false,
                )
            }

            assertEquals(UploadRecoveryResult.RETRY, result)
            assertEquals("PROPFIND", server.takeRequest().method)
            val cleanup = server.takeRequest()
            assertEquals("DELETE", cleanup.method)
            assertEquals("/root/.ard-upload-$stagingKey.part", cleanup.path)
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
