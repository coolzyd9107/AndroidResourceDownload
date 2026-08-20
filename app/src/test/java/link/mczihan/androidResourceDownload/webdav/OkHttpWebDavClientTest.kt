package link.mczihan.androidResourceDownload.webdav

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.webdav.OkHttpWebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredential
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavByteRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavDepth
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPermission
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
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

    private fun credentialProvider(): WebDavCredentialProvider =
        object : WebDavCredentialProvider {
            private val lease = CredentialLease(
                credential = WebDavCredential(
                    username = "reader",
                    password = "secret",
                    permission = WebDavPermission.READ_ONLY,
                ),
                generation = 1L,
            )

            override suspend fun acquire(): CredentialLease = lease

            override suspend fun invalidate(generation: Long) = Unit

            override suspend fun clear() = Unit
        }
}
