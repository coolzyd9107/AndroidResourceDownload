package com.resdownload.android.data.notice

import com.resdownload.android.data.publiccontent.NOTICE_URL
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeRepositoryTest {
    @Test
    fun normalizesUtf8BomAndWhitespace() {
        val bytes = "\uFEFF  版本公告\n".toByteArray(Charsets.UTF_8)

        assertEquals("版本公告", normalizeNotice(bytes))
    }

    @Test
    fun blankNoticeIsEmpty() {
        assertNull(normalizeNotice(" \n\t".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun usesNoticeFromCurrentRepository() {
        assertEquals(
            "https://raw.githubusercontent.com/zhuzhuzihan/AndroidResourceDownload/main/notice.txt",
            NOTICE_URL,
        )
    }

    @Test
    fun missingNoticeIsAnErrorInsteadOfEmptyContent() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .build()
            }
            .build()

        val error = runCatching { NoticeRepository(client).load() }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun parsesOnlyRequestedVersionReleaseNotes() {
        val notice = """
            更新日志:
            v2.3.7:
            - older entry
            v2.3.8:
            - 修复 WebDAV 复制
            - 更新 M3E 界面
            v2.3.9:
            - future entry
        """.trimIndent()

        assertEquals(
            listOf("修复 WebDAV 复制", "更新 M3E 界面"),
            parseReleaseNotesForVersion(notice, "2.3.8"),
        )
    }

    @Test
    fun missingOrMalformedVersionHasNoReleaseNotes() {
        assertEquals(emptyList<String>(), parseReleaseNotesForVersion("v2.3.8:\n- entry", "2.3"))
        assertEquals(emptyList<String>(), parseReleaseNotesForVersion("v2.3.7:\n- entry", "2.3.8"))
    }

    @Test
    fun releaseNotesStopAtFollowingNonVersionSection() {
        val notice = """
            v2.3.8:
            - included
            已知问题:
            - excluded
        """.trimIndent()

        assertEquals(listOf("included"), parseReleaseNotesForVersion(notice, "2.3.8"))
    }

    @Test
    fun duplicateVersionSectionDoesNotMergeReleaseNotes() {
        val notice = "v2.3.8:\n- first\nv2.3.8:\n- duplicate"

        assertEquals(listOf("first"), parseReleaseNotesForVersion(notice, "2.3.8"))
    }
}
