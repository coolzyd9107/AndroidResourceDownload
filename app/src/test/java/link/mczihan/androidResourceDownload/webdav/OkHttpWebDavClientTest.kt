package link.mczihan.androidResourceDownload.webdav

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.webdav.OkHttpWebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredential
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavDepth
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
