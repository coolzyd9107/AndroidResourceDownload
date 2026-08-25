package com.resdownload.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalFileNamesTest {
    @Test
    fun suffixIsInsertedBeforeFinalExtension() {
        assertEquals("file.txt", collisionFileName("file.txt", 0))
        assertEquals("file(1).txt", collisionFileName("file.txt", 1))
        assertEquals("archive.tar(2).gz", collisionFileName("archive.tar.gz", 2))
        assertEquals("README(3)", collisionFileName("README", 3))
        assertEquals(".env(4)", collisionFileName(".env", 4))
        assertEquals(".config(5).json", collisionFileName(".config.json", 5))
        assertEquals("name(6).", collisionFileName("name.", 6))
    }
}
