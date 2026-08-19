package link.mczihan.androidResourceDownload.data.webdav

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import link.mczihan.androidResourceDownload.domain.webdav.WebDavByteRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavDepth
import link.mczihan.androidResourceDownload.domain.webdav.WebDavMetadata
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavReadResponse
import link.mczihan.androidResourceDownload.domain.webdav.WebDavResource
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class CredentialBackedWebDavClient(
    private val credentialProvider: WebDavCredentialProvider,
    private val okHttpClient: OkHttpClient,
) : WebDavClient {
    private val operationMutex = Mutex()

    private suspend fun client(): WebDavClient = operationMutex.withLock {
        val credential = credentialProvider.acquire().credential
        require(credential.baseUrl.isNotBlank()) { "WebDAV credential has no base URL" }
        OkHttpWebDavClient(
            endpoint = credential.baseUrl.toHttpUrl().let { baseUrl ->
                val root = credential.rootPath.toString().trim('/').takeIf { it.isNotEmpty() }
                if (root == null) baseUrl else baseUrl.newBuilder()
                    .addPathSegments(root)
                    .build()
            },
            credentialProvider = credentialProvider,
            okHttpClient = okHttpClient,
        )
    }

    override suspend fun propFind(path: WebDavPath, depth: WebDavDepth): List<WebDavResource> =
        client().propFind(path, depth)

    override suspend fun head(path: WebDavPath): WebDavMetadata = client().head(path)

    override suspend fun get(
        path: WebDavPath,
        range: WebDavByteRange?,
        ifRange: String?,
    ): WebDavReadResponse = client().get(path, range, ifRange)

    override suspend fun put(path: WebDavPath, upload: WebDavUpload) = client().put(path, upload)

    override suspend fun makeCollection(path: WebDavPath) = client().makeCollection(path)

    override suspend fun delete(path: WebDavPath) = client().delete(path)

    override suspend fun move(path: WebDavPath, destination: WebDavPath, overwrite: Boolean) =
        client().move(path, destination, overwrite)
}
