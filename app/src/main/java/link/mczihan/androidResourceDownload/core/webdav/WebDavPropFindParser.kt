package link.mczihan.androidResourceDownload.core.webdav

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavResource
import okhttp3.HttpUrl
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

class WebDavPropFindParser(
    private val maximumResponseBytes: Long = DEFAULT_MAXIMUM_RESPONSE_BYTES,
    private val parserFactory: () -> XmlPullParser = ::KXmlParser,
) {
    init {
        require(maximumResponseBytes > 0L) { "Maximum response size must be positive" }
    }

    fun parse(
        input: InputStream,
        endpoint: WebDavEndpoint,
        requestUrl: HttpUrl,
    ): List<WebDavResource> = try {
        val parser = parserFactory().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            if (this is KXmlParser) {
                // Some WebDAV servers use undeclared prefixes on metadata
                // attributes. They are irrelevant to the DAV properties below.
                setFeature(KXML_RELAXED_FEATURE, true)
            }
            // Let the XML declaration or BOM select UTF-8/UTF-16 instead of
            // corrupting non-UTF-8 WebDAV responses.
            setInput(BoundedInputStream(input, maximumResponseBytes), null)
        }
        parseDocument(parser, endpoint, requestUrl)
    } catch (error: WebDavException) {
        throw error
    } catch (error: XmlPullParserException) {
        val oversized = findCause<ResponseLimitExceeded>(error)
        if (oversized != null) throw WebDavException.ResponseTooLarge(maximumResponseBytes)
        val detail = error.message
            ?.substringBefore(" (position:")
            ?.take(160)
            ?.takeIf { it.isNotBlank() }
        throw WebDavException.InvalidResponse(
            if (detail == null) {
                "Malformed WebDAV PROPFIND response"
            } else {
                "Malformed WebDAV PROPFIND response: $detail"
            },
            error,
        )
    } catch (error: ResponseLimitExceeded) {
        throw WebDavException.ResponseTooLarge(maximumResponseBytes)
    } catch (error: IOException) {
        throw error
    } catch (error: RuntimeException) {
        val type = error.javaClass.simpleName.ifBlank { "RuntimeException" }
        val message = error.message
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.take(160)
            ?.takeIf { it.isNotEmpty() }
        val detail = if (message == null) type else "$type: $message"
        throw WebDavException.InvalidResponse("Invalid WebDAV PROPFIND response: $detail", error)
    }

    private fun parseDocument(
        parser: XmlPullParser,
        endpoint: WebDavEndpoint,
        requestUrl: HttpUrl,
    ): List<WebDavResource> {
        val resources = mutableListOf<WebDavResource>()
        var rootSeen = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            rejectUnsafeXmlEvent(event)
            if (event == XmlPullParser.START_TAG) {
                if (!rootSeen) {
                    if (!parser.isDavElement("multistatus")) {
                        throw WebDavException.InvalidResponse("WebDAV response is not a DAV multistatus document")
                    }
                    rootSeen = true
                } else if (parser.isDavElement("response")) {
                    resources += parseResponse(parser, endpoint, requestUrl)
                }
            }
            event = parser.next()
        }
        if (!rootSeen) throw WebDavException.InvalidResponse("WebDAV response contains no XML document")
        return resources
    }

    private fun parseResponse(
        parser: XmlPullParser,
        endpoint: WebDavEndpoint,
        requestUrl: HttpUrl,
    ): WebDavResource {
        val responseDepth = parser.depth
        var href: String? = null
        val properties = MutableProperties()
        while (true) {
            val event = parser.next()
            rejectUnsafeXmlEvent(event)
            if (event == XmlPullParser.END_TAG && parser.depth == responseDepth) break
            if (event != XmlPullParser.START_TAG || parser.namespace != DAV_NAMESPACE) continue
            when (parser.name) {
                "href" -> href = readText(parser).trim()
                "propstat" -> parsePropStat(parser)?.let(properties::merge)
                else -> skipSubtree(parser)
            }
        }

        val resolvedPath = endpoint.resolveHref(
            href ?: throw WebDavException.InvalidResponse("WebDAV response is missing href"),
            requestUrl,
        )
        return WebDavResource(
            path = resolvedPath,
            displayName = properties.displayName?.takeIf { it.isNotBlank() }
                ?: resolvedPath.name.orEmpty(),
            isCollection = properties.isCollection,
            resourceTypeKnown = properties.resourceTypeKnown,
            contentLength = properties.contentLength,
            lastModifiedEpochMillis = properties.lastModifiedEpochMillis,
            contentType = properties.contentType,
            etag = properties.etag,
        )
    }

    private fun parsePropStat(parser: XmlPullParser): MutableProperties? {
        val propStatDepth = parser.depth
        var candidate: MutableProperties? = null
        var statusCode: Int? = null
        while (true) {
            val event = parser.next()
            rejectUnsafeXmlEvent(event)
            if (event == XmlPullParser.END_TAG && parser.depth == propStatDepth) break
            if (event != XmlPullParser.START_TAG || parser.namespace != DAV_NAMESPACE) continue
            when (parser.name) {
                "prop" -> candidate = parseProperties(parser)
                "status" -> statusCode = parseStatusCode(readText(parser))
                else -> skipSubtree(parser)
            }
        }
        return candidate?.takeIf { statusCode?.let { code -> code in 200..299 } == true }
    }

    private fun parseProperties(parser: XmlPullParser): MutableProperties {
        val propertiesDepth = parser.depth
        val result = MutableProperties()
        while (true) {
            val event = parser.next()
            rejectUnsafeXmlEvent(event)
            if (event == XmlPullParser.END_TAG && parser.depth == propertiesDepth) break
            if (event != XmlPullParser.START_TAG || parser.namespace != DAV_NAMESPACE) continue
            when (parser.name) {
                "displayname" -> result.displayName = readText(parser).trim()
                "getcontentlength" -> {
                    result.contentLength = readText(parser).trim().toLongOrNull()?.takeIf { it >= 0L }
                }
                "getlastmodified" -> result.lastModifiedEpochMillis = parseHttpDate(readText(parser).trim())
                "resourcetype" -> {
                    result.resourceTypeKnown = true
                    result.isCollection = parseResourceType(parser)
                }
                "getcontenttype" -> result.contentType = readText(parser).trim().ifEmpty { null }
                "getetag" -> result.etag = readText(parser).trim().ifEmpty { null }
                else -> skipSubtree(parser)
            }
        }
        return result
    }

    private fun parseResourceType(parser: XmlPullParser): Boolean {
        val resourceTypeDepth = parser.depth
        var collection = false
        while (true) {
            val event = parser.next()
            rejectUnsafeXmlEvent(event)
            if (event == XmlPullParser.END_TAG && parser.depth == resourceTypeDepth) break
            if (event == XmlPullParser.START_TAG && parser.isDavElement("collection")) {
                collection = true
                skipSubtree(parser)
            }
        }
        return collection
    }

    private fun readText(parser: XmlPullParser): String {
        val startDepth = parser.depth
        val result = StringBuilder()
        while (true) {
            val event = parser.next()
            rejectUnsafeXmlEvent(event)
            if (event == XmlPullParser.END_TAG && parser.depth == startDepth) break
            when (event) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> {
                    result.append(parser.text)
                }
                XmlPullParser.START_TAG -> skipSubtree(parser)
            }
        }
        return result.toString()
    }

    private fun skipSubtree(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        val startDepth = parser.depth
        while (true) {
            val event = parser.next()
            rejectUnsafeXmlEvent(event)
            if (event == XmlPullParser.END_TAG && parser.depth == startDepth) return
        }
    }

    private fun rejectUnsafeXmlEvent(event: Int) {
        if (event == XmlPullParser.END_DOCUMENT) {
            throw WebDavException.InvalidResponse("WebDAV XML ended before the current element closed")
        }
        if (event == XmlPullParser.DOCDECL || event == XmlPullParser.ENTITY_REF) {
            throw WebDavException.InvalidResponse("DTD and entity content is not allowed in WebDAV XML")
        }
    }

    private fun XmlPullParser.isDavElement(localName: String): Boolean =
        namespace == DAV_NAMESPACE && name == localName

    private fun parseStatusCode(statusLine: String): Int? =
        STATUS_CODE.find(statusLine)?.groupValues?.get(1)?.toIntOrNull()

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

    private inline fun <reified T : Throwable> findCause(error: Throwable): T? {
        var current: Throwable? = error
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private class MutableProperties(
        var displayName: String? = null,
        var isCollection: Boolean = false,
        var resourceTypeKnown: Boolean = false,
        var contentLength: Long? = null,
        var lastModifiedEpochMillis: Long? = null,
        var contentType: String? = null,
        var etag: String? = null,
    ) {
        fun merge(other: MutableProperties) {
            displayName = other.displayName ?: displayName
            isCollection = isCollection || other.isCollection
            resourceTypeKnown = resourceTypeKnown || other.resourceTypeKnown
            contentLength = other.contentLength ?: contentLength
            lastModifiedEpochMillis = other.lastModifiedEpochMillis ?: lastModifiedEpochMillis
            contentType = other.contentType ?: contentType
            etag = other.etag ?: etag
        }
    }

    private class BoundedInputStream(
        input: InputStream,
        private val maximumBytes: Long,
    ) : FilterInputStream(input) {
        private var bytesRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value != -1) account(1L)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) account(count.toLong())
            return count
        }

        private fun account(count: Long) {
            bytesRead += count
            if (bytesRead > maximumBytes) throw ResponseLimitExceeded()
        }
    }

    private class ResponseLimitExceeded : IOException()

    companion object {
        const val DEFAULT_MAXIMUM_RESPONSE_BYTES: Long = 8L * 1024L * 1024L
        private const val DAV_NAMESPACE = "DAV:"
        private const val KXML_RELAXED_FEATURE =
            "http://xmlpull.org/v1/doc/features.html#relaxed"
        private val STATUS_CODE = Regex("(?:^|\\s)([1-5]\\d{2})(?:\\s|$)")
        private val WHITESPACE = Regex("\\s+")
        private val GMT: TimeZone = TimeZone.getTimeZone("GMT")
        private val HTTP_DATE_PATTERNS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy",
        )
    }
}
