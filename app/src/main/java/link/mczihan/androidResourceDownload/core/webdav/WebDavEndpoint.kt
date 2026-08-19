package link.mczihan.androidResourceDownload.core.webdav

import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import okhttp3.HttpUrl

class WebDavEndpoint private constructor(
    val rootUrl: HttpUrl,
    private val rootSegments: List<String>,
) {
    fun urlFor(path: WebDavPath): HttpUrl = buildUrl(rootSegments + path.decodedSegments, path.isRoot)

    fun collectionUrlFor(path: WebDavPath): HttpUrl =
        buildUrl(rootSegments + path.decodedSegments, trailingSlash = true)

    fun resolveHref(href: String, requestUrl: HttpUrl): WebDavPath {
        if (href.isBlank() || href.any { Character.isISOControl(it) }) {
            throw WebDavException.UnsafePath("WebDAV href is blank or contains control characters")
        }
        if (!isSameOrigin(requestUrl)) {
            throw WebDavException.UnsafePath("Request URL is outside the configured WebDAV origin")
        }
        WebDavPath.rejectEncodedSeparators(href)
        WebDavPath.rejectTraversalSyntax(href)
        val resolved = requestUrl.resolve(href)
            ?: throw WebDavException.UnsafePath("WebDAV href is not a valid URL")
        if (!isSameOrigin(resolved) || resolved.username.isNotEmpty() || resolved.password.isNotEmpty()) {
            throw WebDavException.UnsafePath("WebDAV href changed origin or included user information")
        }
        if (resolved.query != null || resolved.fragment != null) {
            throw WebDavException.UnsafePath("WebDAV href query and fragment components are not allowed")
        }
        WebDavPath.rejectEncodedSeparators(resolved.encodedPath)

        val decoded = resolved.pathSegments.dropLastWhile { it.isEmpty() }
        if (decoded.size < rootSegments.size || decoded.take(rootSegments.size) != rootSegments) {
            throw WebDavException.UnsafePath("WebDAV href escaped the configured root")
        }
        return WebDavPath.fromDecodedSegments(decoded.drop(rootSegments.size))
    }

    fun isSameOrigin(url: HttpUrl): Boolean =
        url.scheme == rootUrl.scheme && url.host == rootUrl.host && url.port == rootUrl.port

    private fun buildUrl(segments: List<String>, trailingSlash: Boolean): HttpUrl {
        val builder = rootUrl.newBuilder().encodedPath("/").query(null).fragment(null)
        segments.forEach { builder.addPathSegment(it) }
        if (trailingSlash && segments.isNotEmpty()) builder.addPathSegment("")
        return builder.build()
    }

    companion object {
        fun create(rootUrl: HttpUrl): WebDavEndpoint {
            require(rootUrl.scheme == "http" || rootUrl.scheme == "https") {
                "WebDAV endpoint must use HTTP or HTTPS"
            }
            require(rootUrl.username.isEmpty() && rootUrl.password.isEmpty()) {
                "WebDAV endpoint must not contain user information"
            }
            require(rootUrl.query == null && rootUrl.fragment == null) {
                "WebDAV endpoint must not contain a query or fragment"
            }
            WebDavPath.rejectEncodedSeparators(rootUrl.encodedPath)
            WebDavPath.rejectTraversalSyntax(rootUrl.encodedPath)
            val validated = WebDavPath.fromDecodedSegments(
                rootUrl.pathSegments.dropLastWhile { it.isEmpty() },
            )
            val provisional = WebDavEndpoint(rootUrl, validated.decodedSegments)
            return WebDavEndpoint(
                rootUrl = provisional.buildUrl(validated.decodedSegments, trailingSlash = true),
                rootSegments = validated.decodedSegments,
            )
        }
    }
}
