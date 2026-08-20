package link.mczihan.androidResourceDownload.data.webdav

import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.core.webdav.WebDavEndpoint
import link.mczihan.androidResourceDownload.core.webdav.WebDavPropFindParser
import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavByteRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavContentRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavDepth
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavMetadata
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPermission
import link.mczihan.androidResourceDownload.domain.webdav.WebDavReadResponse
import link.mczihan.androidResourceDownload.domain.webdav.WebDavResource
import link.mczihan.androidResourceDownload.domain.webdav.WebDavStatusMapper
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.Buffer
import okio.source
import timber.log.Timber

class OkHttpWebDavClient(
    endpoint: HttpUrl,
    private val credentialProvider: WebDavCredentialProvider,
    okHttpClient: OkHttpClient = OkHttpClient(),
    private val propFindParser: WebDavPropFindParser = WebDavPropFindParser(),
    private val retryAuthentication: Boolean = true,
) : WebDavClient {
    private val endpoint = WebDavEndpoint.create(endpoint)
    private val httpClient = okHttpClient.newBuilder()
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val mutationHttpClient = httpClient.newBuilder()
        .readTimeout(MUTATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .writeTimeout(MUTATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build()

    override suspend fun propFind(
        path: WebDavPath,
        depth: WebDavDepth,
    ): List<WebDavResource> = propFindAt(endpoint.collectionUrlFor(path), depth)

    override suspend fun propFindResource(path: WebDavPath): WebDavResource? {
        try {
            return propFindAt(endpoint.urlFor(path), WebDavDepth.ZERO).resourceAt(path)
        } catch (_: WebDavException.NotFound) {
            // Some servers distinguish a collection URI only by its trailing slash.
        } catch (_: WebDavException.RedirectRejected) {
            // Retry the canonical collection URI without following a write-adjacent redirect.
        }
        return try {
            propFindAt(endpoint.collectionUrlFor(path), WebDavDepth.ZERO).resourceAt(path)
        } catch (_: WebDavException.NotFound) {
            null
        }
    }

    private suspend fun propFindAt(
        url: HttpUrl,
        depth: WebDavDepth,
    ): List<WebDavResource> = withContext(Dispatchers.IO) {
        val response = executeAuthenticated { lease ->
            Request.Builder()
                .url(url)
                .header("Authorization", lease.basicAuthorization())
                .header("Depth", depth.headerValue)
                .method("PROPFIND", PROPFIND_BODY)
                .build()
        }
        response.use { currentResponse ->
            requireStatus(currentResponse, setOf(200, 207))
            val body = currentResponse.body ?: throw WebDavException.InvalidResponse(
                "WebDAV PROPFIND response has no body",
            )
            try {
                propFindParser.parse(body.byteStream(), endpoint, url)
            } catch (error: WebDavException.InvalidResponse) {
                val mediaType = body.contentType()?.toString() ?: "missing"
                val contentEncoding = currentResponse.header("Content-Encoding") ?: "identity"
                Timber.e(
                    error,
                    "Unable to parse WebDAV PROPFIND response; Content-Type=%s; Content-Encoding=%s",
                    mediaType,
                    contentEncoding,
                )
                throw WebDavException.InvalidResponse(
                    "${error.message}; Content-Type=$mediaType; Content-Encoding=$contentEncoding",
                    error,
                )
            }
        }
    }

    private fun List<WebDavResource>.resourceAt(path: WebDavPath): WebDavResource =
        firstOrNull { it.path == path }
            ?: throw WebDavException.InvalidResponse("WebDAV PROPFIND response omitted the requested resource")

    override suspend fun head(path: WebDavPath): WebDavMetadata {
        val response = executeAuthenticated { lease ->
            Request.Builder()
                .url(endpoint.urlFor(path))
                .header("Authorization", lease.basicAuthorization())
                .head()
                .build()
        }
        if (response.code == 405 || response.code == 501 || response.code in 300..399) {
            response.close()
            return probeMetadataWithGet(path)
        }
        response.use {
            requireSuccess(it)
            return it.toMetadata()
        }
    }

    private suspend fun probeMetadataWithGet(path: WebDavPath): WebDavMetadata = try {
        get(path, WebDavByteRange(start = 0L, endInclusive = 0L)).use { response ->
            response.metadata.copy(
                contentLength = if (response.statusCode == 206) {
                    response.contentRange?.totalLength
                } else {
                    response.metadata.contentLength
                },
                acceptsByteRanges = response.statusCode == 206 || response.metadata.acceptsByteRanges,
            )
        }
    } catch (_: WebDavException.RangeNotSatisfiable) {
        get(path).use(WebDavReadResponse::metadata)
    }

    override suspend fun get(
        path: WebDavPath,
        range: WebDavByteRange?,
        ifRange: String?,
    ): WebDavReadResponse {
        validateOptionalHeader(ifRange, "If-Range")
        val response = executeAuthenticated(followSameOriginRedirects = true) { lease ->
            Request.Builder()
                .url(endpoint.urlFor(path))
                .header("Authorization", lease.basicAuthorization())
                .get()
                .apply {
                    if (range != null) header("Range", range.toHeaderValue())
                    if (ifRange != null) header("If-Range", ifRange)
                }
                .build()
        }
        if (response.code != 200 && response.code != 206) {
            response.close()
            throw WebDavStatusMapper.exceptionFor(response.code)
        }
        val body = response.body
        if (body == null) {
            response.close()
            throw WebDavException.InvalidResponse("WebDAV GET response has no body")
        }
        return try {
            val metadata = response.toMetadata()
            val contentRange = if (response.code == 206) {
                val requestedRange = range ?: throw WebDavException.InvalidResponse(
                    "WebDAV returned a partial response without a Range request",
                )
                parseContentRange(response.header("Content-Range"), requestedRange, metadata.contentLength)
            } else {
                null
            }
            WebDavReadResponse(
                statusCode = response.code,
                metadata = metadata,
                contentRange = contentRange,
                stream = body.byteStream(),
                closeAction = response::close,
            )
        } catch (error: Exception) {
            response.close()
            throw error
        }
    }

    override suspend fun put(path: WebDavPath, upload: WebDavUpload, overwrite: Boolean) {
        executeWrite { lease ->
            Request.Builder()
                .url(endpoint.urlFor(path))
                .header("Authorization", lease.basicAuthorization())
                .apply { if (!overwrite) header("If-None-Match", "*") }
                .put(StreamingUploadRequestBody(upload))
                .build()
        }.use(::requireSuccess)
    }

    override suspend fun makeCollection(path: WebDavPath) {
        executeWrite { lease ->
            Request.Builder()
                .url(endpoint.urlFor(path))
                .header("Authorization", lease.basicAuthorization())
                .method("MKCOL", null)
                .build()
        }.use { requireStatus(it, setOf(200, 201)) }
    }

    override suspend fun delete(path: WebDavPath, isCollection: Boolean, ifMatch: String?) {
        validateOptionalHeader(ifMatch, "If-Match")
        val strongEtag = ifMatch.strongEntityTagOrNull()
        executeWrite { lease ->
            Request.Builder()
                .url(if (isCollection) endpoint.collectionUrlFor(path) else endpoint.urlFor(path))
                .header("Authorization", lease.basicAuthorization())
                .apply { if (strongEtag != null) header("If-Match", strongEtag) }
                .delete()
                .build()
        }.use { requireStatus(it, setOf(200, 202, 204)) }
    }

    override suspend fun move(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean,
        sourceIsCollection: Boolean,
        sourceEtag: String?,
    ) {
        validateOptionalHeader(sourceEtag, "If-Match")
        val strongEtag = sourceEtag.strongEntityTagOrNull()
        val sourceUrl = if (sourceIsCollection) endpoint.collectionUrlFor(source) else endpoint.urlFor(source)
        val destinationUrl = if (sourceIsCollection) {
            endpoint.collectionUrlFor(destination)
        } else {
            endpoint.urlFor(destination)
        }
        executeWrite { lease ->
            Request.Builder()
                .url(sourceUrl)
                .header("Authorization", lease.basicAuthorization())
                .header("Destination", destinationUrl.toString())
                .header("Overwrite", if (overwrite) "T" else "F")
                .apply { if (strongEtag != null) header("If-Match", strongEtag) }
                .method("MOVE", null)
                .build()
        }.use { requireStatus(it, setOf(200, 201, 204)) }
    }

    override suspend fun copy(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean,
        sourceIsCollection: Boolean,
        sourceEtag: String?,
    ) {
        validateOptionalHeader(sourceEtag, "If-Match")
        val strongEtag = sourceEtag.strongEntityTagOrNull()
        val sourceUrl = if (sourceIsCollection) endpoint.collectionUrlFor(source) else endpoint.urlFor(source)
        val destinationUrl = if (sourceIsCollection) {
            endpoint.collectionUrlFor(destination)
        } else {
            endpoint.urlFor(destination)
        }
        executeWrite { lease ->
            Request.Builder()
                .url(sourceUrl)
                .header("Authorization", lease.basicAuthorization())
                .header("Destination", destinationUrl.toString())
                .header("Overwrite", if (overwrite) "T" else "F")
                .header("Depth", "infinity")
                .apply { if (strongEtag != null) header("If-Match", strongEtag) }
                .method("COPY", null)
                .build()
        }.use { requireStatus(it, setOf(200, 201, 204)) }
    }

    private suspend fun executeWrite(factory: (CredentialLease) -> Request): Response =
        executeAuthenticated(requiredPermission = WebDavPermission.READ_WRITE, factory = factory)

    private suspend fun executeAuthenticated(
        requiredPermission: WebDavPermission = WebDavPermission.READ_ONLY,
        followSameOriginRedirects: Boolean = false,
        factory: (CredentialLease) -> Request,
    ): Response = withContext(Dispatchers.IO) {
        val firstLease = credentialProvider.acquire()
        requirePermission(firstLease, requiredPermission)
        val firstResponse = executeRequest(factory(firstLease), followSameOriginRedirects)
        if (firstResponse.code != 401 || !retryAuthentication) return@withContext firstResponse

        firstResponse.close()
        credentialProvider.invalidate(firstLease.generation)
        val refreshedLease = credentialProvider.acquire()
        requirePermission(refreshedLease, requiredPermission)
        executeRequest(factory(refreshedLease), followSameOriginRedirects).also { response ->
            if (response.code == 401) credentialProvider.invalidate(refreshedLease.generation)
        }
    }

    private suspend fun executeRequest(request: Request, followSameOriginRedirects: Boolean): Response {
        if (!followSameOriginRedirects) return executeOnce(request)

        var currentRequest = request
        // Once we follow a cross-origin redirect (e.g. WebDAV gateway → CDN/object storage),
        // we strip Authorization/Cookie from every subsequent hop so credentials never leak
        // to third-party domains.
        var crossedOrigin = false
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = executeOnce(currentRequest, requireSameOrigin = !crossedOrigin)
            if (response.code !in REDIRECT_STATUS_CODES) return response

            val target = response.header("Location")?.let(response.request.url::resolve)
            if (target == null || target.username.isNotEmpty() || target.password.isNotEmpty()) {
                response.close()
                throw WebDavException.RedirectRejected(response.code)
            }
            val isCrossOrigin = !endpoint.isSameOrigin(target)
            if (isCrossOrigin) {
                crossedOrigin = true
            }
            if (redirectCount == MAX_REDIRECTS) {
                response.close()
                throw WebDavException.RedirectRejected(response.code)
            }

            response.close()
            val builder = currentRequest.newBuilder().url(target)
            if (crossedOrigin) {
                // Strip sensitive headers before sending to a third-party origin.
                builder.removeHeader("Authorization")
                builder.removeHeader("Cookie")
            }
            currentRequest = builder.build()
        }
        error("Redirect loop terminated unexpectedly")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun executeOnce(request: Request, requireSameOrigin: Boolean = true): Response {
        if (requireSameOrigin && !endpoint.isSameOrigin(request.url)) {
            throw WebDavException.UnsafePath("WebDAV request escaped the configured origin")
        }
        return suspendCancellableCoroutine { continuation ->
            val client = if (request.method in WRITE_METHODS) mutationHttpClient else httpClient
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (!continuation.isCancelled) {
                            continuation.resumeWith(Result.failure(WebDavException.Network(e)))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response) { response.close() }
                    }
                },
            )
        }
    }

    private fun requirePermission(lease: CredentialLease, required: WebDavPermission) {
        if (required == WebDavPermission.READ_WRITE &&
            lease.credential.permission != WebDavPermission.READ_WRITE
        ) {
            throw WebDavException.ReadWriteCredentialRequired()
        }
    }

    private fun requireSuccess(response: Response) {
        if (!response.isSuccessful) throw WebDavStatusMapper.exceptionFor(response.code)
    }

    private fun requireStatus(response: Response, allowed: Set<Int>) {
        if (response.code !in allowed) throw WebDavStatusMapper.exceptionFor(response.code)
    }

    private fun Response.toMetadata(): WebDavMetadata = WebDavMetadata(
        contentLength = header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0L },
        lastModifiedEpochMillis = header("Last-Modified")?.let(::parseHttpDate),
        lastModified = header("Last-Modified"),
        contentType = header("Content-Type"),
        etag = header("ETag"),
        acceptsByteRanges = header("Accept-Ranges")
            ?.split(',')
            ?.any { it.trim().equals("bytes", ignoreCase = true) }
            ?: false,
    )

    private fun parseContentRange(
        value: String?,
        requestedRange: WebDavByteRange,
        responseLength: Long?,
    ): WebDavContentRange {
        val match = value?.let(CONTENT_RANGE_PATTERN::matchEntire)
            ?: throw WebDavException.InvalidResponse("WebDAV 206 response has an invalid Content-Range")
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
        if (start == null || end == null || start != requestedRange.start ||
            end < start || requestedRange.endInclusive?.let { end > it } == true ||
            total?.let { it <= end } == true
        ) {
            throw WebDavException.InvalidResponse("WebDAV 206 response does not match the requested range")
        }
        val rangeLength = try {
            Math.addExact(Math.subtractExact(end, start), 1L)
        } catch (error: ArithmeticException) {
            throw WebDavException.InvalidResponse("WebDAV Content-Range overflowed", error)
        }
        if (responseLength != null && responseLength != rangeLength) {
            throw WebDavException.InvalidResponse("WebDAV 206 Content-Length does not match Content-Range")
        }
        return WebDavContentRange(start, end, total)
    }

    private fun CredentialLease.basicAuthorization(): String =
        Credentials.basic(credential.username, credential.password, Charsets.UTF_8)

    private fun validateOptionalHeader(value: String?, name: String) {
        if (value != null && (value.isBlank() || value.any { Character.isISOControl(it) })) {
            throw IllegalArgumentException("$name must not be blank or contain control characters")
        }
    }

    private fun String?.strongEntityTagOrNull(): String? {
        val value = this ?: return null
        if (value.startsWith("W/", ignoreCase = true) ||
            value.length < 2 || value.first() != '"' || value.last() != '"'
        ) {
            return null
        }
        return value.takeIf { candidate ->
            candidate.substring(1, candidate.lastIndex).all { character ->
                character == '\u0021' || character in '\u0023'..'\u007e'
            }
        }
    }

    private fun parseHttpDate(value: String): Long? {
        for (pattern in HTTP_DATE_PATTERNS) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = GMT
            }
            val position = ParsePosition(0)
            val parsed = formatter.parse(value, position)
            if (parsed != null && position.index == value.length) return parsed.time
        }
        return null
    }

    private class StreamingUploadRequestBody(
        private val upload: WebDavUpload,
    ) : RequestBody() {
        override fun contentType(): MediaType? = upload.contentType?.toMediaTypeOrNull()

        override fun contentLength(): Long = upload.contentLength ?: -1L

        override fun writeTo(sink: BufferedSink) {
            upload.openStream().use { input ->
                input.source().use { source ->
                    val buffer = Buffer()
                    var uploadedBytes = 0L
                    while (true) {
                        val read = source.read(buffer, UPLOAD_BUFFER_SIZE)
                        if (read < 0L) break
                        if (read == 0L) continue
                        sink.write(buffer, read)
                        uploadedBytes += read
                        upload.onProgress(uploadedBytes)
                    }
                }
            }
        }
    }

    companion object {
        private val GMT: TimeZone = TimeZone.getTimeZone("GMT")
        private val CONTENT_RANGE_PATTERN = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private val WRITE_METHODS = setOf("PUT", "MKCOL", "DELETE", "MOVE", "COPY")
        private const val MAX_REDIRECTS = 5
        private const val MUTATION_TIMEOUT_MINUTES = 5L
        private const val UPLOAD_BUFFER_SIZE = 64 * 1024L
        private val HTTP_DATE_PATTERNS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy",
        )
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:displayname/>
                <d:getcontentlength/>
                <d:getlastmodified/>
                <d:resourcetype/>
                <d:getcontenttype/>
                <d:getetag/>
              </d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody(XML_MEDIA_TYPE)
    }
}
