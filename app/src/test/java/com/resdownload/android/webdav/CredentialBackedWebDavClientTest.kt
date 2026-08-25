package com.resdownload.android.webdav

import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import com.resdownload.android.data.webdav.CredentialBackedWebDavClient
import com.resdownload.android.domain.webdav.CredentialLease
import com.resdownload.android.domain.webdav.WebDavCredential
import com.resdownload.android.domain.webdav.WebDavCredentialProvider
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.domain.webdav.WebDavPermission
import com.resdownload.android.domain.webdav.WebDavUpload
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
                    overwrite = true,
                    ifMatch = "\"v1\"",
                )
            }

            assertEquals(2, opens.get())
            val firstRequest = server.takeRequest()
            val secondRequest = server.takeRequest()
            assertEquals("hello", firstRequest.body.readUtf8())
            assertEquals("hello", secondRequest.body.readUtf8())
            assertEquals("\"v1\"", firstRequest.getHeader("If-Match"))
            assertEquals("\"v1\"", secondRequest.getHeader("If-Match"))
        } finally {
            server.shutdown()
        }
    }
}
