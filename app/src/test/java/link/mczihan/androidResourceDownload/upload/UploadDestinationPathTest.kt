package link.mczihan.androidResourceDownload.upload

import link.mczihan.androidResourceDownload.data.upload.uploadDestinationPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadDestinationPathTest {
    @Test
    fun appendsEveryRelativeFolderSegmentWithoutFlattening() {
        val destination = uploadDestinationPath(
            WebDavPath.parseDecoded("/target"),
            listOf("Selected Folder", "sub", " notes.txt "),
        )

        assertEquals("/target/Selected Folder/sub/ notes.txt ", destination.toString())
    }

    @Test
    fun rejectsTraversalInsideRelativePath() {
        val error = runCatching {
            uploadDestinationPath(WebDavPath.root(), listOf("folder", "..", "secret.txt"))
        }.exceptionOrNull()

        assertTrue(error != null)
    }
}
