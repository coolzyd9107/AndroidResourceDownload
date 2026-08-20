package link.mczihan.androidResourceDownload.data.file

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.data.webdav.OkHttpWebDavClient
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.FilePreviewContent
import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredential
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPermission
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavFilePreviewTest {
    @Test
    fun textPreviewUsesBoundedRangeAndDecodesUtf8() {
        val server = MockWebServer()
        server.start()
        try {
            val body = "你好 preview".toByteArray()
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "text/plain; charset=utf-8")
                    .setHeader("Content-Range", "bytes 0-${body.lastIndex}/${body.size}")
                    .setBody(Buffer().write(body)),
            )

            val preview = runBlocking {
                repository(server).preview(file("notes.txt", body.size.toLong(), "text/plain"))
            } as FilePreviewContent.Text

            assertEquals("你好 preview", preview.text)
            assertFalse(preview.truncated)
            assertEquals("bytes=0-524288", server.takeRequest().getHeader("Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun decodesUtf16AndGbkText() {
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "预览".toByteArray(Charsets.UTF_16LE)
        assertEquals("预览", decodePlainText(utf16, "text/plain", false)?.text)

        val gbk = "中文内容".toByteArray(charset("GB18030"))
        assertEquals("中文内容", decodePlainText(gbk, "text/plain; charset=gbk", false)?.text)
        assertEquals(null, decodePlainText(gbk, "text/plain", false))

        val windows1252 = "résumé".toByteArray(charset("windows-1252"))
        assertEquals(null, decodePlainText(windows1252, "text/plain", false))
        assertEquals(
            "résumé",
            decodePlainText(windows1252, "text/plain; charset=windows-1252", false)?.text,
        )
    }

    @Test
    fun extractsReadableTextFromRtf() {
        val rtf = """{\rtf1\ansi Hello\par \u20013?\u25991?}""".toByteArray()

        val preview = requireNotNull(parseRtfText(rtf))

        assertTrue(preview.text.contains("Hello"))
        assertTrue(preview.text.contains("中文"))
        assertTrue(
            requireNotNull(parseRtfText("{\\rtf1{\\fonttbl{\\f0 Arial;}}Text}".toByteArray()))
                .text.contains("Text"),
        )
    }

    @Test
    fun extractsTextFromDocxAndOdtWithoutWritingFiles() {
        val server = MockWebServer()
        server.start()
        try {
            val docx = zip(
                "word/document.xml",
                """
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>Hello &amp; world</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Second&#160;line</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                """.trimIndent(),
            )
            val odt = zip(
                "content.xml",
                """
                    <office:document-content
                        xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                        xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                      <office:body><office:text>
                        <text:h>Title</text:h>
                        <text:p>Hello<text:s text:c="2"/>world</text:p>
                      </office:text></office:body>
                    </office:document-content>
                """.trimIndent(),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(docx)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(odt)))
            val repository = repository(server)

            val docxPreview = runBlocking {
                repository.preview(file("report.docx", docx.size.toLong()))
            } as FilePreviewContent.Text
            val odtPreview = runBlocking {
                repository.preview(file("report.odt", odt.size.toLong()))
            } as FilePreviewContent.Text

            assertEquals("Hello & world\nSecond\u00A0line", docxPreview.text)
            assertEquals("Title\nHello  world", odtPreview.text)
            assertFalse(docxPreview.monospace)
            assertFalse(odtPreview.monospace)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun imagePreviewRequiresSupportedMagicBytes() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00,
        )
        assertTrue(hasSupportedImageSignature(png))
        assertFalse(hasSupportedImageSignature("not an image".toByteArray()))
    }

    @Test
    fun ignoredRangeStillRejectsOversizedFullFile() {
        val server = MockWebServer()
        server.start()
        try {
            val oversized = ByteArray(1 * 1024 * 1024 + 1)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setChunkedBody(Buffer().write(oversized), 8 * 1024),
            )

            val error = runCatching {
                runBlocking { repository(server).preview(file("large.rtf", 0L)) }
            }.exceptionOrNull()

            assertTrue(error is WebDavException.ResponseTooLarge)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsRtfWithExcessiveGroupDepth() {
        val source = "{\\rtf1" + "{".repeat(129) + "text" + "}".repeat(130)

        assertEquals(null, parseRtfText(source.toByteArray()))
        assertEquals(null, parseRtfText("{\\rtfoobar text}".toByteArray()))
    }

    @Test
    fun rejectsZipBombBeforeDocumentXml() {
        val server = MockWebServer()
        server.start()
        try {
            val archive = zipWithInflatedPrefix(16 * 1024 * 1024 + 1)
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(archive)))

            val error = runCatching {
                runBlocking {
                    repository(server).preview(file("bomb.docx", archive.size.toLong()))
                }
            }.exceptionOrNull()

            assertTrue(error is WebDavException.ResponseTooLarge)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsDataHiddenInZipDirectoryEntry() {
        val server = MockWebServer()
        server.start()
        try {
            val archive = zipWithInflatedPrefix(16 * 1024 * 1024 + 1, "padding/")
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(archive)))

            val error = runCatching {
                runBlocking {
                    repository(server).preview(file("directory-bomb.docx", archive.size.toLong()))
                }
            }.exceptionOrNull()

            assertTrue(error is WebDavException.ResponseTooLarge)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsDeeplyNestedOfficeXml() {
        val server = MockWebServer()
        server.start()
        try {
            val nested = "<w:r>".repeat(129) + "<w:t>text</w:t>" + "</w:r>".repeat(129)
            val archive = zip(
                "word/document.xml",
                """
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p>$nested</w:p></w:body>
                    </w:document>
                """.trimIndent(),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(archive)))

            val error = runCatching {
                runBlocking {
                    repository(server).preview(file("deep.docx", archive.size.toLong()))
                }
            }.exceptionOrNull()

            assertTrue(error is WebDavException.InvalidResponse)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsOfficeXmlWithExcessiveAttributesOrDtd() {
        val server = MockWebServer()
        server.start()
        try {
            val attributes = (1..65).joinToString(separator = "") { index -> " a$index=\"x\"" }
            val excessiveAttributes = zip(
                "word/document.xml",
                """
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p><w:r$attributes><w:t>text</w:t></w:r></w:p></w:body>
                    </w:document>
                """.trimIndent(),
            )
            val dtd = zip(
                "word/document.xml",
                """
                    <!DOCTYPE document [<!ENTITY x "unsafe">]>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body>
                    </w:document>
                """.trimIndent(),
            )
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(Buffer().write(excessiveAttributes)),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(dtd)))
            val repository = repository(server)

            val attributesError = runCatching {
                runBlocking {
                    repository.preview(file("attributes.docx", excessiveAttributes.size.toLong()))
                }
            }.exceptionOrNull()
            val dtdError = runCatching {
                runBlocking { repository.preview(file("dtd.docx", dtd.size.toLong())) }
            }.exceptionOrNull()

            assertTrue(attributesError is WebDavException.InvalidResponse)
            assertTrue(dtdError is WebDavException.InvalidResponse)
        } finally {
            server.shutdown()
        }
    }

    private fun repository(server: MockWebServer): WebDavFileRepository = WebDavFileRepository(
        OkHttpWebDavClient(
            endpoint = server.url("/root/"),
            credentialProvider = credentialProvider(),
        ),
    )

    private fun credentialProvider(): WebDavCredentialProvider = object : WebDavCredentialProvider {
        private val lease = CredentialLease(
            WebDavCredential("reader", "secret", WebDavPermission.READ_ONLY),
            generation = 1L,
        )

        override suspend fun acquire(): CredentialLease = lease
        override suspend fun invalidate(generation: Long) = Unit
        override suspend fun clear() = Unit
    }

    private fun file(name: String, size: Long, mimeType: String? = null) = FileNode(
        name = name,
        path = "/$name",
        isDirectory = false,
        size = size,
        mimeType = mimeType,
    )

    private fun zip(entryName: String, text: String): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        output.toByteArray()
    }

    private fun zipWithInflatedPrefix(
        prefixBytes: Int,
        entryName: String = "padding.bin",
    ): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            val block = ByteArray(8 * 1024)
            var remaining = prefixBytes
            while (remaining > 0) {
                val count = minOf(block.size, remaining)
                zip.write(block, 0, count)
                remaining -= count
            }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"/>"""
                    .toByteArray(),
            )
            zip.closeEntry()
        }
        output.toByteArray()
    }

}
