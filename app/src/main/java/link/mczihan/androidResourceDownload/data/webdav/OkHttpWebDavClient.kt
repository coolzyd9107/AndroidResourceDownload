package link.mczihan.androidResourceDownload.data.webdav

import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.core.webdav.WebDavEndpoint
import link.mczihan.androidResourceDownload.core.webdav.WebDavPropFindParser
import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavByteRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
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
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source

class OkHttpWebDavClient(
    endpoint: HttpUrl,
    private val credentialProvider: WebDavCredentialProvider,
    okHttpClient: OkHttpClient = OkHttpClient(),
    private val propFindParser: WebDavPropFindParser = WebDavPropFindParser(),
) : WebDavClient {
    private val endpoint = WebDavEndpoint.create(endpoint)
    private val httpClient = okHttpClient.newBuilder()
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun propFind(path: WebDavPath, depth: WebDavDepth): List<WebDavResource> {
        val url = endpoint.collectionUrlFor(path)
        val response = executeAuthenticated { lease ->
            Request.Builder()
                .url(url)
                .header("Authorization", lease.basicAuthorization())
                .header("Depth", depth.headerValue)
                .method("PROPFIND", PROPFIND_BODY)
                .build()
        }
        response.use {
            requireStatus(it, setOf(200, 207))
            val body = it.body ?: throw WebDavException.InvalidResponse(
                "WebDAV PROPFIND response has no body",
            )
            return propFindParser.parse(body.byteStream(), endpoint, url)
        }
    }

    override suspend fun head(path: WebDavPath): WebDavMetadata {
        val response = executeAuthenticated { lease ->
            Request.Builder()
                .url(endpoint.urlFor(path))
                .header("Authorization", lease.basicAuthorization())
                .head()
                .build()
        }
        response.use {
            requireSuccess(it)
            return it.toMetadata()
        }
    }

    override suspend fun get(
        path: WebDavPath,
        range: WebDavByteRange?,
        ifRange: String?,
    ): WebDavReadResponse {
        validateOptionalHeader(ifRange, "If-Range")
        val response = executeAuthenticated { lease ->
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
        return WebDavReadResponse(
            statusCode = response.code,
            metadata = response.toMetadata(),
            contentRange = response.header("Content-Range"),
            stream = body.byteStream(),
            closeAction = response::close,
        )
    }

    override suspend fun put(path: WebDavPath, upload: WebDavUpload) {
        executeWrite { lease ->
            Request.Builder()
                .url(endpoint.urlFor(path))
                .header("Authorization", lease.basicAuthorization())
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

    override suspend fun delete(path: WebDavPath) {
        executeWrite { lease ->
            Request.Builder()
                .url(endpoint.urlFor(path))
                .header("Authorization", lease.basicAuthorization())
                .delete()
                .build()
        }.use { requireStatus(it, setOf(200, 202, 204)) }
    }

    override suspend fun move(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean,
    ) {
        val destinationUrl = endpoint.urlFor(destination)
        executeWrite { lease ->
            Request.Builder()
                .url(endpoint.urlFor(source))
                .header("Authorization", lease.basicAuthorization())
                .header("Destination", destinationUrl.toString())
                .header("Overwrite", if (overwrite) "T" else "F")
                .method("MOVE", null)
                .build()
        }.use { requireStatus(it, setOf(200, 201, 204)) }
    }

    private suspend fun executeWrite(factory: (CredentialLease) -> Request): Response =
        executeAuthenticated(WebDavPermission.READ_WRITE, factory)

    private suspend fun executeAuthenticated(
        requiredPermission: WebDavPermission = WebDavPermission.READ_ONLY,
        factory: (CredentialLease) -> Request,
    ): Response = withContext(Dispatchers.IO) {
        val firstLease = credentialProvider.acquire()
        requirePermission(firstLease, requiredPermission)
        val firstResponse = executeOnce(factory(firstLease))
        if (firstResponse.code != 401) return@withContext firstResponse

        firstResponse.close()
        credentialProvider.invalidate(firstLease.generation)
        val refreshedLease = credentialProvider.acquire()
        requirePermission(refreshedLease, requiredPermission)
        executeOnce(factory(refreshedLease))
    }

    private fun executeOnce(request: Request): Response {
        if (!endpoint.isSameOrigin(request.url)) {
            throw WebDavException.UnsafePath("WebDAV request escaped the configured origin")
        }
        return try {
            httpClient.newCall(request).execute()
        } catch (error: IOException) {
            throw WebDavException.Network(error)
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
        contentType = header("Content-Type"),
        etag = header("ETag"),
    )

    private fun CredentialLease.basicAuthorization(): String =
        Credentials.basic(credential.username, credential.password, Charsets.UTF_8)

    private fun validateOptionalHeader(value: String?, name: String) {
        if (value != null && (value.isBlank() || value.any { Character.isISOControl(it) })) {
            throw IllegalArgumentException("$name must not be blank or contain control characters")
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
        override fun contentType(): MediaType? = upload.contentType?.toMediaType()

        override fun contentLength(): Long = upload.contentLength ?: -1L

        override fun writeTo(sink: BufferedSink) {
            upload.openStream().use { input ->
                input.source().use { source -> sink.writeAll(source) }
            }
        }
    }

    companion object {
        private val GMT: TimeZone = TimeZone.getTimeZone("GMT")
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
