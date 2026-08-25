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
}
