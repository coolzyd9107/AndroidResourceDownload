package com.resdownload.android.domain.webdav

interface WebDavClient {
    suspend fun propFind(
        path: WebDavPath,
        depth: WebDavDepth = WebDavDepth.ONE,
    ): List<WebDavResource>

    suspend fun propFindResource(path: WebDavPath): WebDavResource? =
        propFind(path, WebDavDepth.ZERO).firstOrNull { it.path == path }

    suspend fun head(path: WebDavPath): WebDavMetadata

    suspend fun get(
        path: WebDavPath,
        range: WebDavByteRange? = null,
        ifRange: String? = null,
    ): WebDavReadResponse

    suspend fun put(
        path: WebDavPath,
        upload: WebDavUpload,
        overwrite: Boolean = false,
        ifMatch: String? = null,
    )

    suspend fun makeCollection(path: WebDavPath)

    suspend fun delete(
        path: WebDavPath,
        isCollection: Boolean = false,
        ifMatch: String? = null,
    )

    suspend fun move(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean = false,
        sourceIsCollection: Boolean = false,
        sourceEtag: String? = null,
    )

    suspend fun copy(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean = false,
        sourceIsCollection: Boolean = false,
        sourceEtag: String? = null,
    )
}
