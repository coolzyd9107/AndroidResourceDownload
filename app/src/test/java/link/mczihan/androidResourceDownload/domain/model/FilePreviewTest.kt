package link.mczihan.androidResourceDownload.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilePreviewTest {
    @Test
    fun recognizesCommonTextImageAndDocumentFormats() {
        assertEquals(FilePreviewFormat.PLAIN_TEXT, file("notes.TXT").previewFormat())
        assertEquals(FilePreviewFormat.PLAIN_TEXT, file("config.yaml").previewFormat())
        assertEquals(FilePreviewFormat.PLAIN_TEXT, file("README").previewFormat())
        assertEquals(FilePreviewFormat.PLAIN_TEXT, file("opaque", "application/json").previewFormat())
        assertEquals(FilePreviewFormat.IMAGE, file("photo.JPEG").previewFormat())
        assertEquals(FilePreviewFormat.IMAGE, file("opaque", "image/webp").previewFormat())
        assertEquals(FilePreviewFormat.RTF, file("letter.rtf").previewFormat())
        assertEquals(FilePreviewFormat.RTF, file("letter.rtf", "text/plain").previewFormat())
        assertEquals(FilePreviewFormat.DOCX, file("letter.docx").previewFormat())
        assertEquals(FilePreviewFormat.DOCX, file("letter.docx", "application/zip").previewFormat())
        assertEquals(FilePreviewFormat.ODT, file("letter.odt").previewFormat())
    }

    @Test
    fun usesHrefNameWhenDisplayNameDoesNotContainExtension() {
        val file = FileNode(
            name = "友好名称",
            path = "/opaque/report.json",
            isDirectory = false,
        )

        assertEquals(FilePreviewFormat.PLAIN_TEXT, file.previewFormat())
    }

    @Test
    fun hidesUnsupportedUnsafeAndOversizedFormats() {
        assertNull(file("legacy.doc", "application/msword").previewFormat())
        assertNull(file("manual.pdf", "application/pdf").previewFormat())
        assertNull(file("manual.pdf", "text/plain").previewFormat())
        assertNull(file("photo.jpg", "text/plain").previewFormat())
        assertNull(file("photo.jpg", "application/zip").previewFormat())
        assertNull(file("vector.svg", "image/svg+xml").previewFormat())
        assertNull(file("archive.zip", "application/zip").previewFormat())
        assertNull(file("app.apk", "application/vnd.android.package-archive").previewFormat())
        assertNull(file("folder", directory = true).previewFormat())
        assertNull(file("upload.txt", temporary = true).previewFormat())
        assertNull(file("huge.png", size = 8L * 1024L * 1024L + 1L).previewFormat())
        assertNull(file("huge.docx", size = 8L * 1024L * 1024L + 1L).previewFormat())
    }

    @Test
    fun largePlainTextStillSupportsBoundedPrefixPreview() {
        assertEquals(
            FilePreviewFormat.PLAIN_TEXT,
            file("large.log", size = Long.MAX_VALUE).previewFormat(),
        )
    }

    private fun file(
        name: String,
        mimeType: String? = null,
        size: Long? = null,
        directory: Boolean = false,
        temporary: Boolean = false,
    ) = FileNode(
        name = name,
        path = "/$name",
        isDirectory = directory,
        size = size,
        mimeType = mimeType,
        isUploadTemporary = temporary,
    )
}
