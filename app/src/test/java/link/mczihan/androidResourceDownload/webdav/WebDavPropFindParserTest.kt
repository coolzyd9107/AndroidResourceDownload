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

    @Test
    fun parsesCompactUppercasePrefixResponseUsedBy123Pan() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><D:multistatus xmlns:D="DAV:"><D:response><D:href>/root/</D:href><D:propstat><D:prop><D:displayname>root</D:displayname><D:resourcetype><D:collection/></D:resourcetype><D:getetag>etag</D:getetag></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat><D:propstat><D:prop><D:getcontentlength/></D:prop><D:status>HTTP/1.1 404 Not Found</D:status></D:propstat></D:response></D:multistatus>"""

        val resource = parser.parse(
            ByteArrayInputStream(xml.toByteArray()),
            endpoint,
            endpoint.collectionUrlFor(WebDavPath.root()),
        ).single()

        assertTrue(resource.isCollection)
        assertEquals("root", resource.displayName)
        assertEquals("etag", resource.etag)
    }

    @Test
    fun ignoresUndeclaredPrefixesOnMetadataAttributes() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/root/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>root</D:displayname>
                    <D:getlastmodified ns0:dt="dateTime.rfc1123">Wed, 19 Aug 2026 10:00:00 GMT</D:getlastmodified>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val resource = parser.parse(
            ByteArrayInputStream(xml.toByteArray()),
            endpoint,
            endpoint.collectionUrlFor(WebDavPath.root()),
        ).single()

        assertEquals("root", resource.displayName)
        assertTrue(resource.lastModifiedEpochMillis != null)
    }
}
