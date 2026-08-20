package link.mczihan.androidResourceDownload.domain.model

enum class FilePreviewFormat(val maximumBytes: Int?) {
    PLAIN_TEXT(null),
    RTF(1 * 1024 * 1024),
    DOCX(8 * 1024 * 1024),
    ODT(8 * 1024 * 1024),
    IMAGE(8 * 1024 * 1024),
}

sealed interface FilePreviewContent {
    data class Text(
        val text: String,
        val truncated: Boolean = false,
        val monospace: Boolean = true,
    ) : FilePreviewContent

    data class Image(
        val bytes: ByteArray,
        val mimeType: String?,
    ) : FilePreviewContent
}

fun FileNode.previewFormat(): FilePreviewFormat? {
    if (isDirectory || isUploadTemporary) return null
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
    val pathName = path.substringAfterLast('/').ifBlank { name }
    val normalizedName = pathName.lowercase()
    val extension = normalizedName.substringAfterLast('.', missingDelimiterValue = "")
    if (extension in BLOCKED_EXTENSIONS || normalizedMime in BLOCKED_MIME_TYPES) return null

    val extensionFormat = when {
        extension == "docx" -> FilePreviewFormat.DOCX
        extension == "odt" -> FilePreviewFormat.ODT
        extension == "rtf" -> FilePreviewFormat.RTF
        extension in IMAGE_EXTENSIONS -> FilePreviewFormat.IMAGE
        extension in TEXT_EXTENSIONS || normalizedName in TEXT_FILE_NAMES -> FilePreviewFormat.PLAIN_TEXT
        else -> null
    }
    val mimeFormat = when {
        normalizedMime in DOCX_MIME_TYPES -> FilePreviewFormat.DOCX
        normalizedMime in ODT_MIME_TYPES -> FilePreviewFormat.ODT
        normalizedMime in RTF_MIME_TYPES -> FilePreviewFormat.RTF
        normalizedMime in IMAGE_MIME_TYPES -> FilePreviewFormat.IMAGE
        normalizedMime?.startsWith("text/") == true ||
            normalizedMime in TEXT_MIME_TYPES -> FilePreviewFormat.PLAIN_TEXT
        else -> null
    }
    if (extensionFormat != null && mimeFormat != null && extensionFormat != mimeFormat &&
        !(extensionFormat == FilePreviewFormat.RTF && mimeFormat == FilePreviewFormat.PLAIN_TEXT)
    ) {
        return null
    }
    if (extensionFormat != null && mimeFormat == null) {
        val genericMimeAllowed = normalizedMime in GENERIC_MIME_TYPES ||
            normalizedMime == "application/zip" &&
            (extensionFormat == FilePreviewFormat.DOCX || extensionFormat == FilePreviewFormat.ODT)
        if (!genericMimeAllowed) return null
    }
    val format = extensionFormat ?: mimeFormat ?: return null
    val maximumBytes = format.maximumBytes
    return format.takeUnless {
        maximumBytes != null && size?.let { it > maximumBytes.toLong() } == true
    }
}

private val IMAGE_MIME_TYPES = setOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/x-png",
    "image/webp",
    "image/bmp",
    "image/x-bmp",
    "image/x-ms-bmp",
    "image/gif",
)
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "jpe", "png", "webp", "bmp", "gif")
private val DOCX_MIME_TYPES = setOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)
private val ODT_MIME_TYPES = setOf("application/vnd.oasis.opendocument.text")
private val RTF_MIME_TYPES = setOf("application/rtf", "text/rtf")
private val TEXT_MIME_TYPES = setOf(
    "application/json",
    "application/ld+json",
    "application/xml",
    "application/xhtml+xml",
    "application/yaml",
    "application/x-yaml",
    "application/toml",
    "application/javascript",
    "application/x-javascript",
    "application/sql",
)
private val TEXT_EXTENSIONS = setOf(
    "txt", "text", "md", "markdown", "log", "csv", "tsv", "json", "jsonl",
    "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "config", "properties",
    "prop", "html", "htm", "css", "scss", "sass", "less", "js", "mjs", "cjs",
    "ts", "tsx", "jsx", "kt", "kts", "java", "groovy", "gradle", "py", "pyw",
    "go", "rs", "c", "h", "hpp", "cc", "cpp", "cxx", "cs", "swift", "php",
    "rb", "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd", "sql", "graphql",
    "gql", "proto", "env", "gitignore", "dockerignore", "editorconfig", "tex", "latex",
    "vtt", "srt", "ass", "ssa", "ics",
)
private val TEXT_FILE_NAMES = setOf(
    "readme", "license", "copying", "changelog", "authors", "makefile", "dockerfile",
)
private val GENERIC_MIME_TYPES = setOf(null, "", "application/octet-stream", "binary/octet-stream")
private val BLOCKED_EXTENSIONS = setOf(
    "doc", "pdf", "apk", "zip", "rar", "7z", "gz", "bz2", "xz", "tar", "jar", "aab",
    "exe", "dll", "so", "dmg", "iso", "svg",
)
private val BLOCKED_MIME_TYPES = setOf(
    "application/msword",
    "application/pdf",
    "application/vnd.android.package-archive",
    "image/svg+xml",
)
