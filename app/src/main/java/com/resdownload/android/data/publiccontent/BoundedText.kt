package com.resdownload.android.data.publiccontent

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import okhttp3.ResponseBody
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

internal suspend fun ResponseBody.readBoundedBytes(limit: Int): ByteArray? = byteStream().use { input ->
    val output = ByteArrayOutputStream(minOf(limit, 8 * 1024))
    val buffer = ByteArray(4 * 1024)
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (output.size() + read > limit) return null
        output.write(buffer, 0, read)
    }
    output.toByteArray()
}

internal fun decodeText(bytes: ByteArray, fallback: Charset? = null): String? =
    decode(bytes, Charsets.UTF_8) ?: fallback?.let { decode(bytes, it) }

private fun decode(bytes: ByteArray, charset: Charset): String? = runCatching {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}.getOrNull()

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { response.close() }
                } else {
                    response.close()
                }
            }
        },
    )
}
