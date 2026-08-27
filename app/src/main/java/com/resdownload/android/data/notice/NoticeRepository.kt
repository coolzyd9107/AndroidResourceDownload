package com.resdownload.android.data.notice

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.resdownload.android.data.publiccontent.awaitResponse
import com.resdownload.android.data.publiccontent.decodeText
import com.resdownload.android.data.publiccontent.NOTICE_URL
import com.resdownload.android.data.publiccontent.readBoundedBytes
import com.resdownload.android.di.PublicHttpClient
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
    }
}

internal fun normalizeNotice(bytes: ByteArray): String? = decodeText(bytes)
    ?.removePrefix("\uFEFF")
    ?.trim()
    ?.takeIf(String::isNotEmpty)

internal fun parseReleaseNotesForVersion(notice: String, version: String): List<String> {
    val normalizedVersion = version.trim()
    if (!VERSION_PATTERN.matches(normalizedVersion)) return emptyList()
    val targetHeading = Regex(
        "^v${Regex.escape(normalizedVersion)}\\s*:\\s*$",
        RegexOption.IGNORE_CASE,
    )
    var collecting = false
    val notes = mutableListOf<String>()
    for (rawLine in notice.lineSequence()) {
        val line = rawLine.trim()
        if (targetHeading.matches(line)) {
            if (collecting) break
            collecting = true
            continue
        }
        if (collecting && VERSION_HEADING_PATTERN.matches(line)) break
        if (collecting && !line.startsWith('-') && SECTION_HEADING_PATTERN.matches(line)) break
        if (collecting && line.startsWith('-')) {
            line.removePrefix("-").trim().takeIf(String::isNotEmpty)?.let(notes::add)
        }
    }
    return notes
}

private val VERSION_PATTERN = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")
private val VERSION_HEADING_PATTERN = Regex(
    "^v[0-9]+\\.[0-9]+\\.[0-9]+\\s*:\\s*$",
    RegexOption.IGNORE_CASE,
)
private val SECTION_HEADING_PATTERN = Regex("^.+[:：]\\s*$")
