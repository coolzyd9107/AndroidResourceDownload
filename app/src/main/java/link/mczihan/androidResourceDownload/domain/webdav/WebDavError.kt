package link.mczihan.androidResourceDownload.domain.webdav

import java.io.IOException

sealed class WebDavException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    class UnsafePath(message: String) : WebDavException(message)
    class AuthenticationRequired(val statusCode: Int = 401) :
        WebDavException("WebDAV authentication failed ($statusCode)")

    class PermissionDenied(val statusCode: Int = 403) :
        WebDavException("WebDAV permission denied ($statusCode)")

    class ReadWriteCredentialRequired :
        WebDavException("This WebDAV operation requires READ_WRITE permission")

    class CollectionOverwriteDenied :
        WebDavException("A WebDAV collection cannot be overwritten")

    class NotFound(val statusCode: Int = 404) :
        WebDavException("WebDAV resource was not found ($statusCode)")

    class Conflict(val statusCode: Int = 409) :
        WebDavException("WebDAV resource conflict ($statusCode)")

    class PreconditionFailed(val statusCode: Int = 412) :
        WebDavException("WebDAV precondition failed ($statusCode)")

    class RangeNotSatisfiable(val statusCode: Int = 416) :
        WebDavException("WebDAV byte range is not satisfiable ($statusCode)")

    class Locked(val statusCode: Int = 423) :
        WebDavException("WebDAV resource is locked ($statusCode)")

    class RedirectRejected(val statusCode: Int) :
        WebDavException("WebDAV redirect was rejected ($statusCode)")

    class CrossOriginRedirect(val statusCode: Int) :
        WebDavException("WebDAV cross-origin redirect was rejected ($statusCode)")

    class ServerError(val statusCode: Int) :
        WebDavException("WebDAV server error ($statusCode)")

    class UnexpectedStatus(val statusCode: Int) :
        WebDavException("Unexpected WebDAV response status ($statusCode)")

    class InvalidResponse(message: String, cause: Throwable? = null) :
        WebDavException(message, cause)

    class ResponseTooLarge(val maximumBytes: Long) :
        WebDavException("WebDAV response exceeded $maximumBytes bytes")

    class CredentialUnavailable(cause: Throwable? = null) :
        WebDavException("WebDAV credentials are unavailable", cause)

    class Network(cause: IOException) : WebDavException("WebDAV network request failed", cause)
}

object WebDavStatusMapper {
    fun exceptionFor(statusCode: Int): WebDavException = when (statusCode) {
        401 -> WebDavException.AuthenticationRequired()
        403 -> WebDavException.PermissionDenied()
        404 -> WebDavException.NotFound()
        409 -> WebDavException.Conflict()
        412 -> WebDavException.PreconditionFailed()
        416 -> WebDavException.RangeNotSatisfiable()
        423 -> WebDavException.Locked()
        in 300..399 -> WebDavException.RedirectRejected(statusCode)
        in 500..599 -> WebDavException.ServerError(statusCode)
        else -> WebDavException.UnexpectedStatus(statusCode)
    }
}
