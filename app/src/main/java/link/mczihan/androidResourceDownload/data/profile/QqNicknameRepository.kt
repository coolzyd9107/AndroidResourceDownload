package link.mczihan.androidResourceDownload.data.profile

import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import link.mczihan.androidResourceDownload.data.publiccontent.decodeText
import link.mczihan.androidResourceDownload.data.publiccontent.awaitResponse
import link.mczihan.androidResourceDownload.data.publiccontent.readBoundedBytes
import link.mczihan.androidResourceDownload.di.PublicHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class QqNicknameRepository @Inject constructor(
    @PublicHttpClient private val client: OkHttpClient,
) {
    private data class CacheEntry(val nickname: String?, val expiresAt: Long)
    private data class CacheLookup(val hit: Boolean, val nickname: String?, val generation: Long)

    private val endpoint = "https://users.qzone.qq.com/fcg-bin/cgi_get_portrait.fcg".toHttpUrl()
    private val cache = mutableMapOf<String, CacheEntry>()
    private val mutex = Mutex()

    suspend fun nickname(qqNumber: String): String? {
        require(qqNumber.isNotEmpty() && qqNumber.all { it in '0'..'9' }) { "Invalid QQ number" }
        val lookup = mutex.withLock {
            val now = System.currentTimeMillis()
            cache.entries.removeAll { it.value.expiresAt <= now }
            val entry = cache[qqNumber]?.takeIf { it.expiresAt > now }
            CacheLookup(entry != null, entry?.nickname, cacheGeneration)
        }
        if (lookup.hit) return lookup.nickname
        val nickname = fetch(qqNumber)
        return mutex.withLock {
            if (cacheGeneration != lookup.generation) return@withLock nickname
            val now = System.currentTimeMillis()
            val ttl = if (nickname == null) FAILURE_CACHE_TTL_MILLIS else CACHE_TTL_MILLIS
            if (cache.size >= MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
            cache[qqNumber] = CacheEntry(nickname, now + ttl)
            nickname
        }
    }

    suspend fun clear() = mutex.withLock {
        cacheGeneration++
        cache.clear()
    }

    private suspend fun fetch(qqNumber: String): String? {
        val url = endpoint.newBuilder().addQueryParameter("uins", qqNumber).build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AndroidResourceDownload/2.0")
            .build()
        return try {
            val response = client.newCall(request).awaitResponse()
            withContext(Dispatchers.IO) {
                response.use {
                    if (!it.isSuccessful) return@use null
                    val bytes = it.body?.readBoundedBytes(MAX_RESPONSE_BYTES) ?: return@use null
                    val text = decodeText(bytes, Charset.forName("GB18030")) ?: return@use null
                    parseQqNickname(text, qqNumber)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 16 * 1024
        const val CACHE_TTL_MILLIS = 60 * 60 * 1_000L
        const val FAILURE_CACHE_TTL_MILLIS = 5 * 60 * 1_000L
        const val MAX_CACHE_ENTRIES = 32
    }

    private var cacheGeneration = 0L
}

internal fun parseQqNickname(payload: String, expectedQqNumber: String): String? {
    val trimmed = payload.trim().removeSuffix(";")
    val prefix = "portraitCallBack("
    if (!trimmed.startsWith(prefix) || !trimmed.endsWith(')')) return null
    val json = trimmed.substring(prefix.length, trimmed.length - 1)
    val values = runCatching {
        Json.parseToJsonElement(json).jsonObject[expectedQqNumber]?.jsonArray
    }.getOrNull() ?: return null
    val nickname = values.getOrNull(6)?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    return nickname.takeIf {
        it.isNotEmpty() && it.length <= 100 && '\uFFFD' !in it && it.none(Character::isISOControl)
    }
}
