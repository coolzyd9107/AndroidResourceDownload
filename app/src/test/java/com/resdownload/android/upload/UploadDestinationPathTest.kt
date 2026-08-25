package com.resdownload.android.upload

import com.resdownload.android.data.upload.uploadDestinationPath
import com.resdownload.android.domain.webdav.WebDavPath
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
