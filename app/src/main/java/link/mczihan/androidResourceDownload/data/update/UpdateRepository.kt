package link.mczihan.androidResourceDownload.data.update

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.data.publiccontent.awaitResponse
import link.mczihan.androidResourceDownload.data.publiccontent.decodeText
import link.mczihan.androidResourceDownload.data.publiccontent.readBoundedBytes
import link.mczihan.androidResourceDownload.di.PublicHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class UpdateManifest(
    val latestVersion: String,
    val updateUrl: String,
)

@Singleton
class UpdateRepository @Inject constructor(
    @PublicHttpClient private val client: OkHttpClient,
) {
    suspend fun load(): UpdateManifest {
        val request = Request.Builder()
            .url(MANIFEST_URL)
            .header("Cache-Control", "no-cache")
            .build()
        val response = client.newCall(request).awaitResponse()
        return withContext(Dispatchers.IO) {
            response.use {
                if (!it.isSuccessful) throw IOException("Update manifest request failed: ${it.code}")
                val bytes = it.body?.readBoundedBytes(MAX_MANIFEST_BYTES)
                    ?: throw IOException("Update manifest is missing or too large")
                parseUpdateManifest(bytes)
                    ?: throw IOException("Update manifest is invalid")
            }
        }
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 8 * 1024
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/zhuzhuzihan/AndroidResourceDownload/main/latest_version.txt"
    }
}

internal fun parseUpdateManifest(bytes: ByteArray): UpdateManifest? {
    val text = decodeText(bytes)?.removePrefix("\uFEFF") ?: return null
    val values = mutableMapOf<String, String>()
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith('#')) continue
        val separator = line.indexOf('=')
        if (separator <= 0) return null
        val key = line.substring(0, separator).trim()
        if (key != LATEST_VERSION_KEY && key != UPDATE_URL_KEY) continue
        if (values.containsKey(key)) return null
        values[key] = line.substring(separator + 1).trim()
    }

    val latestVersion = values[LATEST_VERSION_KEY]
        ?.takeIf { parseVersionParts(it) != null }
        ?: return null
    val updateUrl = values[UPDATE_URL_KEY]
        ?.toHttpUrlOrNull()
        ?.takeIf { url ->
            url.isHttps && url.username.isEmpty() && url.password.isEmpty()
        }
        ?.toString()
        ?: return null
    return UpdateManifest(latestVersion, updateUrl)
}

internal fun compareAppVersions(left: String, right: String): Int? {
    val leftParts = parseVersionParts(left) ?: return null
    val rightParts = parseVersionParts(right) ?: return null
    for (index in leftParts.indices) {
        val comparison = leftParts[index].compareTo(rightParts[index])
        if (comparison != 0) return comparison
    }
    return 0
}

private fun parseVersionParts(value: String): List<Long>? {
    val normalized = value.trim()
    if (!VERSION_PATTERN.matches(normalized)) return null
    return normalized.split('.').map { it.toLongOrNull() ?: return null }
}

private const val LATEST_VERSION_KEY = "latest_version"
private const val UPDATE_URL_KEY = "update_url"
private val VERSION_PATTERN = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
