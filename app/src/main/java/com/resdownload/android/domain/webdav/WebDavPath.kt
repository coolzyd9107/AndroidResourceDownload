package com.resdownload.android.domain.webdav

/** A root-relative WebDAV path represented only by validated, decoded segments. */
class WebDavPath private constructor(
    segments: List<String>,
) {
    val decodedSegments: List<String> = segments.toList()

    val isRoot: Boolean
        get() = decodedSegments.isEmpty()

    val name: String?
        get() = decodedSegments.lastOrNull()

    fun child(decodedSegment: String): WebDavPath =
        fromDecodedSegments(decodedSegments + decodedSegment)

    override fun equals(other: Any?): Boolean =
        other is WebDavPath && decodedSegments == other.decodedSegments

    override fun hashCode(): Int = decodedSegments.hashCode()

    override fun toString(): String = decodedSegments.joinToString(separator = "/", prefix = "/")

    companion object {
        private val encodedSeparator = Regex("%2f|%5c", RegexOption.IGNORE_CASE)
        private val traversalSyntax = Regex(
            "(?:^|/)(?:(?:%2e)|\\.){1,2}(?=/|$|[?#])",
            RegexOption.IGNORE_CASE,
        )

        fun root(): WebDavPath = WebDavPath(emptyList())

        fun parseDecoded(path: String): WebDavPath {
            rejectControls(path, "path")
            rejectEncodedSeparators(path)
            if (path.isEmpty() || path == "/") return root()

            val withoutLeadingSlash = if (path.startsWith('/')) path.substring(1) else path
            val withoutTrailingSlash = if (withoutLeadingSlash.endsWith('/')) {
                withoutLeadingSlash.dropLast(1)
            } else {
                withoutLeadingSlash
            }
            return fromDecodedSegments(withoutTrailingSlash.split('/'))
        }

        fun fromDecodedSegments(segments: List<String>): WebDavPath {
            segments.forEach(::validateSegment)
            return WebDavPath(segments)
        }

        fun rejectEncodedSeparators(value: String) {
            if (encodedSeparator.containsMatchIn(value)) {
                throw WebDavException.UnsafePath("Encoded path separators are not allowed")
            }
        }

        fun rejectTraversalSyntax(value: String) {
            if (traversalSyntax.containsMatchIn(value)) {
                throw WebDavException.UnsafePath("Encoded or decoded traversal syntax is not allowed")
            }
        }

        private fun validateSegment(segment: String) {
            if (segment.isEmpty()) {
                throw WebDavException.UnsafePath("Empty path segments are not allowed")
            }
            if (segment == "." || segment == "..") {
                throw WebDavException.UnsafePath("Path traversal segments are not allowed")
            }
            if ('/' in segment || '\\' in segment) {
                throw WebDavException.UnsafePath("Path separators are not allowed inside a segment")
            }
            rejectControls(segment, "path segment")
            rejectEncodedSeparators(segment)
            rejectTraversalSyntax(segment)
        }

        private fun rejectControls(value: String, label: String) {
            if (value.any { Character.isISOControl(it) }) {
                throw WebDavException.UnsafePath("Control characters are not allowed in a $label")
            }
        }
    }
}
