package link.mczihan.androidResourceDownload.data.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
