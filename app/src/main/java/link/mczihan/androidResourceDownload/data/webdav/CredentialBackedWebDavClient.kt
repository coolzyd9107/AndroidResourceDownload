package link.mczihan.androidResourceDownload.data.webdav

import link.mczihan.androidResourceDownload.domain.webdav.CredentialLease
import link.mczihan.androidResourceDownload.domain.webdav.WebDavByteRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavDepth
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
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
    private fun client(lease: CredentialLease): WebDavClient {
        val credential = lease.credential
        require(credential.baseUrl.isNotBlank()) { "WebDAV credential has no base URL" }
        return OkHttpWebDavClient(
            endpoint = credential.baseUrl.toHttpUrl().let { baseUrl ->
                val root = credential.rootPath.toString().trim('/').takeIf { it.isNotEmpty() }
                if (root == null) baseUrl else baseUrl.newBuilder()
                    .addPathSegments(root)
                    .build()
            },
            credentialProvider = FixedCredentialProvider(lease),
            okHttpClient = okHttpClient,
            retryAuthentication = false,
        )
    }

    private suspend fun <T> execute(operation: suspend (WebDavClient) -> T): T {
        val firstLease = credentialProvider.acquire()
        try {
            return operation(client(firstLease))
        } catch (error: WebDavException.AuthenticationRequired) {
            credentialProvider.invalidate(firstLease.generation)
        }

        val refreshedLease = credentialProvider.acquire()
        return try {
            operation(client(refreshedLease))
        } catch (error: WebDavException.AuthenticationRequired) {
            credentialProvider.invalidate(refreshedLease.generation)
            throw error
        }
    }

    override suspend fun propFind(path: WebDavPath, depth: WebDavDepth): List<WebDavResource> =
        execute { it.propFind(path, depth) }

    override suspend fun head(path: WebDavPath): WebDavMetadata = execute { it.head(path) }

    override suspend fun get(
        path: WebDavPath,
        range: WebDavByteRange?,
        ifRange: String?,
    ): WebDavReadResponse = execute { it.get(path, range, ifRange) }

    override suspend fun put(path: WebDavPath, upload: WebDavUpload) = execute { it.put(path, upload) }

    override suspend fun makeCollection(path: WebDavPath) = execute { it.makeCollection(path) }

    override suspend fun delete(path: WebDavPath) = execute { it.delete(path) }

    override suspend fun move(source: WebDavPath, destination: WebDavPath, overwrite: Boolean) =
        execute { it.move(source, destination, overwrite) }

    private class FixedCredentialProvider(
        private val lease: CredentialLease,
    ) : WebDavCredentialProvider {
        override suspend fun acquire(): CredentialLease = lease

        override suspend fun invalidate(generation: Long) = Unit

        override suspend fun clear() = Unit
    }
}
