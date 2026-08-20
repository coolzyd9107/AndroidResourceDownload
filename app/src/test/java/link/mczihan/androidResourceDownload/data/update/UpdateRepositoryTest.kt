package link.mczihan.androidResourceDownload.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {
    @Test
    fun parsesVersionAndHttpsUpdateUrl() {
        val manifest = parseUpdateManifest(
            """
                latest_version=2.1.1
                update_url=https://example.com/download?id=42
            """.trimIndent().toByteArray(Charsets.UTF_8),
        )

        requireNotNull(manifest)
        assertEquals("2.1.1", manifest.latestVersion)
        assertEquals("https://example.com/download?id=42", manifest.updateUrl)
    }

    @Test
    fun comparesEachNumericVersionComponent() {
        assertTrue(requireNotNull(compareAppVersions("2.0.0", "2.0.1")) < 0)
        assertTrue(requireNotNull(compareAppVersions("2.0.0", "2.1.1")) < 0)
        assertTrue(requireNotNull(compareAppVersions("2.9.9", "2.10.0")) < 0)
        assertEquals(0, compareAppVersions("2.0.0", "2.0.0"))
        assertTrue(requireNotNull(compareAppVersions("3.0.0", "2.9.9")) > 0)
    }

    @Test
    fun rejectsMalformedManifestValues() {
        assertNull(
            parseUpdateManifest(
                "latest_version=2.1\nupdate_url=https://example.com".toByteArray(),
            ),
        )
        assertNull(
            parseUpdateManifest(
                "latest_version=2.1.0\nupdate_url=http://example.com".toByteArray(),
            ),
        )
        assertNull(
            parseUpdateManifest(
                "latest_version=2.1.0\nupdate_url=https://user:secret@example.com".toByteArray(),
            ),
        )
        assertNull(parseUpdateManifest("latest_version=2.1.0".toByteArray()))
        assertNull(
            parseUpdateManifest(
                """
                    latest_version=2.1.0
                    latest_version=2.2.0
                    update_url=https://example.com
                """.trimIndent().toByteArray(),
            ),
        )
        assertNull(parseUpdateManifest(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(compareAppVersions("2.0", "2.0.1"))
    }
}
