package link.mczihan.androidResourceDownload.domain.webdav

interface WebDavClient {
    suspend fun propFind(
        path: WebDavPath,
        depth: WebDavDepth = WebDavDepth.ONE,
    ): List<WebDavResource>

    suspend fun head(path: WebDavPath): WebDavMetadata

    suspend fun get(
        path: WebDavPath,
        range: WebDavByteRange? = null,
        ifRange: String? = null,
    ): WebDavReadResponse

    suspend fun put(path: WebDavPath, upload: WebDavUpload)

    suspend fun makeCollection(path: WebDavPath)

    suspend fun delete(path: WebDavPath)

    suspend fun move(
        source: WebDavPath,
        destination: WebDavPath,
        overwrite: Boolean = false,
    )
}
