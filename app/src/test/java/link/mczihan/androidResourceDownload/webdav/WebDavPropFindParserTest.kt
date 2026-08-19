package link.mczihan.androidResourceDownload.webdav

import java.io.ByteArrayInputStream
import link.mczihan.androidResourceDownload.core.webdav.WebDavEndpoint
import link.mczihan.androidResourceDownload.core.webdav.WebDavPropFindParser
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPropFindParserTest {
    private val endpoint = WebDavEndpoint.create("https://dav.example.com/root/".toHttpUrl())
    private val parser = WebDavPropFindParser()

    @Test
    fun parsesUtf16ResponseUsingXmlDeclaration() {
        val xml = """<?xml version="1.0" encoding="UTF-16"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/root/文件.txt</d:href>
                <d:propstat>
                  <d:prop><d:displayname>文件.txt</d:displayname></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val resources = parser.parse(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_16)),
            endpoint,
            endpoint.collectionUrlFor(WebDavPath.root()),
        )

        assertEquals(1, resources.size)
        assertEquals("文件.txt", resources.single().displayName)
    }

    @Test
    fun resolvesRelativeHrefAgainstCollectionUrl() {
        val folder = WebDavPath.fromDecodedSegments(listOf("folder"))
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>child.txt</d:href>
                <d:propstat>
                  <d:prop><d:displayname>child.txt</d:displayname></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val resource = parser.parse(
            ByteArrayInputStream(xml.toByteArray()),
            endpoint,
            endpoint.collectionUrlFor(folder),
        ).single()

        assertEquals(WebDavPath.fromDecodedSegments(listOf("folder", "child.txt")), resource.path)
    }

    @Test
    fun rejectsNonDavXmlInsteadOfShowingEmptyDirectory() {
        val error = runCatching {
            parser.parse(
                ByteArrayInputStream("<html><body>proxy error</body></html>".toByteArray()),
                endpoint,
                endpoint.collectionUrlFor(WebDavPath.root()),
            )
        }.exceptionOrNull()

        assertTrue(error is WebDavException.InvalidResponse)
        assertTrue(error?.message?.contains("DAV multistatus") == true)
    }
}
