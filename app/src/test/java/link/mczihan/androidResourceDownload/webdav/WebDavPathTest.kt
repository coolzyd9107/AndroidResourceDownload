package link.mczihan.androidResourceDownload.webdav

import link.mczihan.androidResourceDownload.core.webdav.WebDavEndpoint
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPathTest {
    @Test
    fun decodedSegmentsAreEncodedByHttpUrl() {
        val endpoint = WebDavEndpoint.create("https://example.com/dav/root/".toHttpUrl())
        val path = WebDavPath.fromDecodedSegments(listOf("folder name", "100%.zip"))

        assertEquals(
            "https://example.com/dav/root/folder%20name/100%25.zip",
            endpoint.urlFor(path).toString(),
        )
    }

    @Test
    fun collectionUrlsAlwaysEndWithSlash() {
        val endpoint = WebDavEndpoint.create("https://example.com/dav/root/".toHttpUrl())

        assertEquals(
            "https://example.com/dav/root/folder/",
            endpoint.collectionUrlFor(WebDavPath.fromDecodedSegments(listOf("folder"))).toString(),
        )
    }

    @Test
    fun traversalControlsAndEncodedSeparatorsAreRejected() {
        assertUnsafe { WebDavPath.fromDecodedSegments(listOf("..")) }
        assertUnsafe { WebDavPath.fromDecodedSegments(listOf("a/b")) }
        assertUnsafe { WebDavPath.fromDecodedSegments(listOf("line\nfeed")) }
        assertUnsafe { WebDavPath.parseDecoded("folder%2Fsecret") }
    }

    @Test
    fun hrefMustRemainOnOriginAndUnderRoot() {
        val endpoint = WebDavEndpoint.create("https://example.com/dav/root/".toHttpUrl())
        val request = endpoint.urlFor(WebDavPath.root())

        assertEquals(
            WebDavPath.fromDecodedSegments(listOf("file name.txt")),
            endpoint.resolveHref("/dav/root/file%20name.txt", request),
        )
        assertUnsafe { endpoint.resolveHref("https://other.example/dav/root/file", request) }
        assertUnsafe { endpoint.resolveHref("/dav/outside/file", request) }
        assertUnsafe { endpoint.resolveHref("../outside", request) }
        assertUnsafe { endpoint.resolveHref("/dav/root/a%2Fb", request) }
    }

    private fun assertUnsafe(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue(error is WebDavException.UnsafePath)
    }
}
