package com.resdownload.android.webdav

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import com.resdownload.android.data.webdav.OkHttpWebDavClient
import com.resdownload.android.domain.webdav.CredentialLease
import com.resdownload.android.domain.webdav.WebDavCredential
import com.resdownload.android.domain.webdav.WebDavCredentialProvider
import com.resdownload.android.domain.webdav.WebDavByteRange
import com.resdownload.android.domain.webdav.WebDavDepth
import com.resdownload.android.domain.webdav.WebDavException
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.domain.webdav.WebDavPermission
import com.resdownload.android.domain.webdav.WebDavUpload
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpWebDavClientTest {
    @Test
    fun readsPropFindBodyOffCallingThread() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setHeader("Content-Type", "text/xml; charset=utf-8")
                    .setBody(
                        """<?xml version="1.0" encoding="UTF-8"?>
                            <D:multistatus xmlns:D="DAV:">
                              <D:response>
                                <D:href>/root/</D:href>
                                <D:propstat>
                                  <D:prop><D:displayname>root</D:displayname></D:prop>
                                  <D:status>HTTP/1.1 200 OK</D:status>
                                </D:propstat>
                              </D:response>
                            </D:multistatus>
                        """.trimIndent(),
                    ),
            )
            val bodyReadThread = AtomicReference<Thread>()
            val httpClient = OkHttpClient.Builder()
                .eventListener(
                    object : EventListener() {
                        override fun responseBodyStart(call: Call) {
                            bodyReadThread.compareAndSet(null, Thread.currentThread())
                        }
                    },
                )
                .build()
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(),
                okHttpClient = httpClient,
            )
            val callingThread = Thread.currentThread()

            val resources = runBlocking {
                client.propFind(WebDavPath.root(), WebDavDepth.ONE)
            }

            assertEquals("root", resources.single().displayName)
            assertNotNull(bodyReadThread.get())
            assertNotSame(callingThread, bodyReadThread.get())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun validatesPartialContentRangeAndSendsResumeHeaders() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 3-4/5")
                    .setHeader("ETag", "\"v1\"")
                    .setBody("lo"),
            )
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(),
            )

            runBlocking {
                client.get(WebDavPath.parseDecoded("/file.txt"), WebDavByteRange(3L), "\"v1\"")
                    .use { response ->
                        assertEquals(3L, response.contentRange?.start)
                        assertEquals(4L, response.contentRange?.endInclusive)
                        assertEquals(5L, response.contentRange?.totalLength)
                    }
            }
            val request = server.takeRequest()
            assertEquals("bytes=3-", request.getHeader("Range"))
            assertEquals("\"v1\"", request.getHeader("If-Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsPartialResponseForDifferentOffset() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 2-4/5")
                    .setBody("llo"),
            )
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(),
            )

            val error = runCatching {
                runBlocking {
                    client.get(WebDavPath.parseDecoded("/file.txt"), WebDavByteRange(3L))
                }
            }.exceptionOrNull()

            assertTrue(error is WebDavException.InvalidResponse)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fallsBackToRangeProbeWhenServerDoesNotSupportHead() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(405))
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-0/5")
                    .setHeader("ETag", "\"v1\"")
                    .setBody("h"),
            )
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(),
            )

            val metadata = runBlocking {
                client.head(WebDavPath.parseDecoded("/file.txt"))
            }

            assertEquals(5L, metadata.contentLength)
            assertEquals("\"v1\"", metadata.etag)
            assertTrue(metadata.acceptsByteRanges)
            val headRequest = server.takeRequest()
            val probeRequest = server.takeRequest()
            assertEquals("HEAD", headRequest.method)
            assertEquals("GET", probeRequest.method)
            assertEquals("bytes=0-0", probeRequest.getHeader("Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fallsBackToOrdinaryGetWhenMetadataRangeProbeIsRejected() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(405))
            server.enqueue(MockResponse().setResponseCode(416))
            server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(),
            )

            val metadata = runBlocking {
                client.head(WebDavPath.parseDecoded("/file.txt"))
            }

            assertEquals(5L, metadata.contentLength)
            val headRequest = server.takeRequest()
            val probeRequest = server.takeRequest()
            val fallbackRequest = server.takeRequest()
            assertEquals("HEAD", headRequest.method)
            assertEquals("bytes=0-0", probeRequest.getHeader("Range"))
            assertEquals("GET", fallbackRequest.method)
            assertEquals(null, fallbackRequest.getHeader("Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun headRedirectProbesOriginalUrlWithoutFollowingLocation() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "https://example.com/credential-target"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-0/5")
                    .setBody("h"),
            )
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(),
            )

            runBlocking {
                client.head(WebDavPath.parseDecoded("/file.txt"))
            }

            val headRequest = server.takeRequest()
            val probeRequest = server.takeRequest()
            assertEquals("/root/file.txt", headRequest.path)
            assertEquals("/root/file.txt", probeRequest.path)
            assertEquals("bytes=0-0", probeRequest.getHeader("Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun followsSameOriginRedirectForFileGet() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "/download/file.txt"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 3-4/5")
                    .setHeader("ETag", "\"v1\"")
                    .setBody("lo"),
            )
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(),
            )

            val body = runBlocking {
                client.get(
                    path = WebDavPath.parseDecoded("/file.txt"),
                    range = WebDavByteRange(3L),
                    ifRange = "\"v1\"",
                ).use { response ->
                    val output = ByteArrayOutputStream()
                    response.stream.copyTo(output)
                    output.toString(Charsets.UTF_8.name())
                }
            }

            assertEquals("lo", body)
            val originalRequest = server.takeRequest()
            val redirectedRequest = server.takeRequest()
            assertEquals("/root/file.txt", originalRequest.path)
            assertEquals("/download/file.txt", redirectedRequest.path)
            val authorization = Credentials.basic("reader", "secret", Charsets.UTF_8)
            assertEquals(authorization, originalRequest.getHeader("Authorization"))
            assertEquals(authorization, redirectedRequest.getHeader("Authorization"))
            assertEquals("bytes=3-", redirectedRequest.getHeader("Range"))
            assertEquals("\"v1\"", redirectedRequest.getHeader("If-Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun followsCrossOriginRedirectStrippingCredentialsForFileGet() {
        val webdavServer = MockWebServer()
        val cdnServer = MockWebServer()
        webdavServer.start()
        cdnServer.start()
        try {
            webdavServer.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", cdnServer.url("/download/file.txt").toString()),
            )
            cdnServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("hello from cdn"),
            )
            val client = OkHttpWebDavClient(
                endpoint = webdavServer.url("/root/"),
                credentialProvider = credentialProvider(),
            )

            val response = runBlocking { client.get(WebDavPath.parseDecoded("/file.txt")) }
            val body = response.use {
                ByteArrayOutputStream().use { output ->
                    it.stream.copyTo(output)
                    output.toString(Charsets.UTF_8.name())
                }
            }

            assertEquals("hello from cdn", body)

            val webdavRequest = webdavServer.takeRequest()
            val cdnRequest = cdnServer.takeRequest()
            assertEquals("/root/file.txt", webdavRequest.path)
            assertEquals("/download/file.txt", cdnRequest.path)

            // WebDAV origin request carries credentials
            val authorization = Credentials.basic("reader", "secret", Charsets.UTF_8)
            assertEquals(authorization, webdavRequest.getHeader("Authorization"))
            // Cross-origin CDN request must NOT carry credentials
            assertEquals(null, cdnRequest.getHeader("Authorization"))
            assertEquals(2, webdavServer.requestCount + cdnServer.requestCount)
        } finally {
            webdavServer.shutdown()
            cdnServer.shutdown()
        }
    }

    @Test
    fun probesFilesWithoutTrailingSlashAndFallsBackForCollections() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(propFindResponse("/root/file.txt", collection = false)),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(301)
                    .setHeader("Location", "/root/folder/"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(207)
                    .setBody(propFindResponse("/root/folder/", collection = true)),
            )
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(),
            )

            val file = runBlocking {
                client.propFindResource(WebDavPath.parseDecoded("/file.txt"))
            }
            val folder = runBlocking {
                client.propFindResource(WebDavPath.parseDecoded("/folder"))
            }

            assertEquals(false, file?.isCollection)
            assertEquals(true, file?.resourceTypeKnown)
            assertEquals(true, folder?.isCollection)
            assertEquals("/root/file.txt", server.takeRequest().path)
            assertEquals("/root/folder", server.takeRequest().path)
            assertEquals("/root/folder/", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun putStreamsProgressAndPreventsOverwriteByDefault() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            val progress = AtomicLong()
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(WebDavPermission.READ_WRITE),
            )

            runBlocking {
                client.put(
                    WebDavPath.parseDecoded("/folder/file.txt"),
                    WebDavUpload(
                        contentLength = 5L,
                        contentType = "text/plain",
                        openStream = { ByteArrayInputStream("hello".toByteArray()) },
                        onProgress = progress::set,
                    ),
                )
            }

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/root/folder/file.txt", request.path)
            assertEquals("*", request.getHeader("If-None-Match"))
            assertEquals("hello", request.body.readUtf8())
            assertEquals(5L, progress.get())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun makeCollectionSendsMkcolAndMapsExistingDirectory() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(405))
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(WebDavPermission.READ_WRITE),
            )

            runBlocking {
                client.makeCollection(WebDavPath.parseDecoded("/new-folder"))
            }
            val conflict = runCatching {
                runBlocking {
                    client.makeCollection(WebDavPath.parseDecoded("/existing"))
                }
            }.exceptionOrNull()

            assertTrue(conflict is WebDavException.PreconditionFailed)
            val created = server.takeRequest()
            assertEquals("MKCOL", created.method)
            assertEquals("/root/new-folder/", created.path)
            assertEquals("MKCOL", server.takeRequest().method)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun moveCopyAndDeleteSendNativeWebDavMethods() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(204))
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(WebDavPermission.READ_WRITE),
            )
            val source = WebDavPath.parseDecoded("/source")
            val destination = WebDavPath.parseDecoded("/archive/destination")

            runBlocking {
                client.move(
                    source,
                    destination,
                    sourceIsCollection = true,
                    sourceEtag = "\"v1\"",
                )
                client.copy(
                    source,
                    destination,
                    sourceIsCollection = true,
                    sourceEtag = "\"v1\"",
                )
                client.delete(source, isCollection = true, ifMatch = "\"v1\"")
            }

            val move = server.takeRequest()
            val copy = server.takeRequest()
            val delete = server.takeRequest()
            assertEquals("MOVE", move.method)
            assertEquals("/root/source/", move.path)
            assertEquals(server.url("/root/archive/destination/").toString(), move.getHeader("Destination"))
            assertEquals("F", move.getHeader("Overwrite"))
            assertEquals("\"v1\"", move.getHeader("If-Match"))
            assertEquals("COPY", copy.method)
            assertEquals("/root/source/", copy.path)
            assertEquals("infinity", copy.getHeader("Depth"))
            assertEquals("F", copy.getHeader("Overwrite"))
            assertEquals("\"v1\"", copy.getHeader("If-Match"))
            assertEquals("DELETE", delete.method)
            assertEquals("/root/source/", delete.path)
            assertEquals("\"v1\"", delete.getHeader("If-Match"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun writeOperationsDoNotForwardWeakOrMalformedEntityTags() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(WebDavPermission.READ_WRITE),
            )

            runBlocking {
                client.move(
                    source = WebDavPath.parseDecoded("/source.txt"),
                    destination = WebDavPath.parseDecoded("/destination.txt"),
                    sourceEtag = "W/\"weak\"",
                )
                client.copy(
                    source = WebDavPath.parseDecoded("/source.txt"),
                    destination = WebDavPath.parseDecoded("/copy.txt"),
                    sourceEtag = "unquoted-etag",
                )
                client.delete(
                    path = WebDavPath.parseDecoded("/destination.txt"),
                    ifMatch = "\"contains space\"",
                )
                client.delete(
                    path = WebDavPath.parseDecoded("/copy.txt"),
                    ifMatch = "\"unicode-文件\"",
                )
            }

            assertNull(server.takeRequest().getHeader("If-Match"))
            assertNull(server.takeRequest().getHeader("If-Match"))
            assertNull(server.takeRequest().getHeader("If-Match"))
            assertNull(server.takeRequest().getHeader("If-Match"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun conditionalPutRejectsWeakEtagBeforeNetwork() {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(WebDavPermission.READ_WRITE),
            )

            val error = runCatching {
                runBlocking {
                    client.put(
                        path = WebDavPath.parseDecoded("/notes.txt"),
                        upload = WebDavUpload(
                            openStream = { ByteArrayInputStream("text".toByteArray()) },
                        ),
                        overwrite = true,
                        ifMatch = "W/\"weak\"",
                    )
                }
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun writeConflictIsMappedAndReadOnlyCredentialSendsNoRequest() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(412))
            val writer = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(WebDavPermission.READ_WRITE),
            )
            val conflict = runCatching {
                runBlocking {
                    writer.put(
                        WebDavPath.parseDecoded("/file.txt"),
                        WebDavUpload(openStream = { ByteArrayInputStream(byteArrayOf()) }),
                    )
                }
            }.exceptionOrNull()
            assertTrue(conflict is WebDavException.PreconditionFailed)
            server.takeRequest()

            val reader = OkHttpWebDavClient(
                endpoint = server.url("/root/"),
                credentialProvider = credentialProvider(WebDavPermission.READ_ONLY),
            )
            val denied = runCatching {
                runBlocking { reader.delete(WebDavPath.parseDecoded("/file.txt")) }
            }.exceptionOrNull()
            assertTrue(denied is WebDavException.ReadWriteCredentialRequired)
            assertNull(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }

    private fun credentialProvider(
        permission: WebDavPermission = WebDavPermission.READ_ONLY,
    ): WebDavCredentialProvider =
        object : WebDavCredentialProvider {
            private val lease = CredentialLease(
                credential = WebDavCredential(
                    username = "reader",
                    password = "secret",
                    permission = permission,
                ),
                generation = 1L,
            )

            override suspend fun acquire(): CredentialLease = lease

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
