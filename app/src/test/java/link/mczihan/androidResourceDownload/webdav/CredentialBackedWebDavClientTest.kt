package link.mczihan.androidResourceDownload.webdav

import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.webdav.CredentialBackedWebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredential
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPermission
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialBackedWebDavClientTest {
    @Test
    fun uploadReopensStreamAfterSingleCredentialRefresh() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(201))
            val opens = AtomicInteger()
            var currentGeneration = 1L
            val provider = object : WebDavCredentialProvider {
                override suspend fun acquire() = CredentialLease(
                    credential = WebDavCredential(
                        username = "admin",
                        password = "secret",
                        permission = WebDavPermission.READ_WRITE,
                        baseUrl = server.url("/root/").toString(),
                    ),
                    generation = currentGeneration,
                )

                override suspend fun invalidate(generation: Long) {
                    currentGeneration = generation + 1
                }

                override suspend fun clear() = Unit
            }
            val client = CredentialBackedWebDavClient(provider, OkHttpClient())

            runBlocking {
                client.put(
                    WebDavPath.parseDecoded("/file.txt"),
                    WebDavUpload(
                        contentLength = 5L,
                        openStream = {
                            opens.incrementAndGet()
                            ByteArrayInputStream("hello".toByteArray())
                        },
                    ),
                )
            }

            assertEquals(2, opens.get())
            assertEquals("hello", server.takeRequest().body.readUtf8())
            assertEquals("hello", server.takeRequest().body.readUtf8())
        } finally {
            server.shutdown()
        }
    }
}
