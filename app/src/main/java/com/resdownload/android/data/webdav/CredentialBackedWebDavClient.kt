package com.resdownload.android.data.webdav

import com.resdownload.android.domain.webdav.CredentialLease
import com.resdownload.android.domain.webdav.WebDavByteRange
import com.resdownload.android.domain.webdav.WebDavClient
import com.resdownload.android.domain.webdav.WebDavCredentialProvider
import com.resdownload.android.domain.webdav.WebDavDepth
import com.resdownload.android.domain.webdav.WebDavException
import com.resdownload.android.domain.webdav.WebDavMetadata
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.domain.webdav.WebDavReadResponse
import com.resdownload.android.domain.webdav.WebDavResource
import com.resdownload.android.domain.webdav.WebDavUpload
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

    override suspend fun propFindResource(path: WebDavPath): WebDavResource? =
        execute { it.propFindResource(path) }

    override suspend fun head(path: WebDavPath): WebDavMetadata = execute { it.head(path) }

    override suspend fun get(
        path: WebDavPath,
        range: WebDavByteRange?,
        ifRange: String?,
    ): WebDavReadResponse = execute { it.get(path, range, ifRange) }

    override suspend fun put(
        path: WebDavPath,
        upload: WebDavUpload,
        overwrite: Boolean,
        ifMatch: String?,
    ) = execute { it.put(path, upload, overwrite, ifMatch) }

    override suspend fun makeCollection(path: WebDavPath) = execute { it.makeCollection(path) }

    override suspend fun delete(path: WebDavPath, isCollection: Boolean, ifMatch: String?) =
        execute { it.delete(path, isCollection, ifMatch) }

    override suspend fun move(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean,
        sourceIsCollection: Boolean,
        sourceEtag: String?,
    ) = execute { it.move(source, destination, overwrite, sourceIsCollection, sourceEtag) }

    override suspend fun copy(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean,
        sourceIsCollection: Boolean,
        sourceEtag: String?,
    ) = execute { it.copy(source, destination, overwrite, sourceIsCollection, sourceEtag) }

    private class FixedCredentialProvider(
        private val lease: CredentialLease,
    ) : WebDavCredentialProvider {
        override suspend fun acquire(): CredentialLease = lease

        override suspend fun invalidate(generation: Long) = Unit

        override suspend fun clear() = Unit
    }
}
