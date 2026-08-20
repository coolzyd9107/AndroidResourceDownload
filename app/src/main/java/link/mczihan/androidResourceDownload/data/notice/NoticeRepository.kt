package link.mczihan.androidResourceDownload.data.notice

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.data.publiccontent.awaitResponse
import link.mczihan.androidResourceDownload.data.publiccontent.decodeText
import link.mczihan.androidResourceDownload.data.publiccontent.readBoundedBytes
import link.mczihan.androidResourceDownload.di.PublicHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class NoticeRepository @Inject constructor(
    @PublicHttpClient private val client: OkHttpClient,
) {
    private val mutex = Mutex()
    private var cachedContent: String? = null

    suspend fun load(): String? = mutex.withLock {
        try {
            fetch().also { content -> if (content != null) cachedContent = content }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cachedContent ?: throw error
        }
    }

    private suspend fun fetch(): String? {
        val request = Request.Builder().url(NOTICE_URL).build()
        val response = client.newCall(request).awaitResponse()
        return withContext(Dispatchers.IO) {
            response.use {
                if (it.code == 404) return@use null
                if (!it.isSuccessful) throw IOException("Notice request failed: ${it.code}")
                val bytes = it.body?.readBoundedBytes(MAX_NOTICE_BYTES)
                    ?: throw IOException("Notice is too large")
                normalizeNotice(bytes) ?: if (decodeText(bytes) == null) {
                    throw IOException("Notice is not valid UTF-8")
                } else {
                    null
                }
            }
        }
    }

    private companion object {
        const val MAX_NOTICE_BYTES = 64 * 1024
        const val NOTICE_URL =
            "https://raw.githubusercontent.com/zhuzhuzihan/AndroidResourceDownload/main/notice.txt"
    }
}

internal fun normalizeNotice(bytes: ByteArray): String? = decodeText(bytes)
    ?.removePrefix("\uFEFF")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
