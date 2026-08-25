package com.resdownload.android.data.file

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.ArrayDeque
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import com.resdownload.android.domain.model.FileNode
import com.resdownload.android.domain.model.FilePreviewContent
import com.resdownload.android.domain.model.FilePreviewFormat
import com.resdownload.android.domain.model.previewFormat
import com.resdownload.android.domain.webdav.WebDavByteRange
import com.resdownload.android.domain.webdav.WebDavClient
import com.resdownload.android.domain.webdav.WebDavException
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.domain.webdav.WebDavReadResponse
import com.resdownload.android.domain.webdav.strongEntityTagOrNull
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

internal suspend fun loadWebDavFilePreview(
    webDavClient: WebDavClient,
    file: FileNode,
): FilePreviewContent = withTimeout(PREVIEW_TIMEOUT_MILLIS) {
    val format = file.previewFormat()
        ?: throw IllegalArgumentException("File type is not supported for preview")
    val path = WebDavPath.parseDecoded(file.path)
    when (format) {
        FilePreviewFormat.PLAIN_TEXT -> {
            val fetched = fetchPreviewBytes(webDavClient, path, TEXT_FETCH_BYTES, allowPrefix = true)
            val effectiveContentType = fetched.mimeType?.trim()?.takeIf(String::isNotEmpty)
                ?: file.mimeType?.trim()?.takeIf(String::isNotEmpty)
            if (!isPlainTextRepresentation(effectiveContentType)) {
                throw WebDavException.InvalidResponse("GET response is not a plain-text representation")
            }
            val decoded = parseInterruptibly {
                decodePlainText(fetched.bytes, effectiveContentType, fetched.truncated)
                    ?: throw WebDavException.InvalidResponse("Text preview has an unsupported encoding")
            }
            FilePreviewContent.Text(
                text = decoded.text,
                truncated = decoded.truncated,
                monospace = true,
                charsetName = decoded.charsetName,
                hasBom = decoded.hasBom,
                contentType = effectiveContentType,
                entityTag = fetched.entityTag,
                encodingEditable = decoded.encodingEditable,
            )
        }
        FilePreviewFormat.RTF -> {
            val fetched = fetchPreviewBytes(
                webDavClient,
                path,
                requireNotNull(format.maximumBytes),
                allowPrefix = false,
            )
            val decoded = parseInterruptibly {
                parseRtfText(fetched.bytes)
                    ?: throw WebDavException.InvalidResponse("RTF preview is invalid")
            }
            FilePreviewContent.Text(decoded.text, decoded.truncated, monospace = false)
        }
        FilePreviewFormat.DOCX -> {
            val fetched = fetchPreviewBytes(
                webDavClient,
                path,
                requireNotNull(format.maximumBytes),
                allowPrefix = false,
            )
            val decoded = parseInterruptibly {
                extractOfficeText(fetched.bytes, OfficeDocumentKind.DOCX)
            }
            FilePreviewContent.Text(decoded.text, decoded.truncated, monospace = false)
        }
        FilePreviewFormat.ODT -> {
            val fetched = fetchPreviewBytes(
                webDavClient,
                path,
                requireNotNull(format.maximumBytes),
                allowPrefix = false,
            )
            val decoded = parseInterruptibly {
                extractOfficeText(fetched.bytes, OfficeDocumentKind.ODT)
            }
            FilePreviewContent.Text(decoded.text, decoded.truncated, monospace = false)
        }
        FilePreviewFormat.IMAGE -> {
            val fetched = fetchPreviewBytes(
                webDavClient,
                path,
                requireNotNull(format.maximumBytes),
                allowPrefix = false,
            )
            if (!parseInterruptibly { hasSupportedImageSignature(fetched.bytes) }) {
                throw WebDavException.InvalidResponse("Image preview format does not match its contents")
            }
            FilePreviewContent.Image(fetched.bytes, fetched.mimeType ?: file.mimeType)
        }
    }
}

private suspend fun <T> parseInterruptibly(block: () -> T): T =
    runInterruptible(Dispatchers.Default, block)

private suspend fun fetchPreviewBytes(
    client: WebDavClient,
    path: WebDavPath,
    maximumBytes: Int,
    allowPrefix: Boolean,
): FetchedPreviewBytes {
    val response = try {
        client.get(path, WebDavByteRange(0L, maximumBytes.toLong()))
    } catch (_: WebDavException.RangeNotSatisfiable) {
        client.get(path)
    }
    return try {
        runInterruptible(Dispatchers.IO) {
            val current = response
            val totalLength = current.contentRange?.totalLength
                ?: current.metadata.contentLength.takeIf { current.statusCode == 200 }
            if (!allowPrefix && totalLength?.let { it > maximumBytes.toLong() } == true) {
                throw WebDavException.ResponseTooLarge(maximumBytes.toLong())
            }

            val readBytes = current.stream.readAtMost(maximumBytes + 1)
            val exceededLimit = readBytes.size > maximumBytes
            if (!allowPrefix && exceededLimit) {
                throw WebDavException.ResponseTooLarge(maximumBytes.toLong())
            }
            if (!allowPrefix) validateCompleteResponse(current, readBytes.size, totalLength)

            val truncated = allowPrefix && (
                exceededLimit ||
                    totalLength?.let { it > maximumBytes.toLong() } == true ||
                    current.statusCode == 206 && (
                        totalLength == null ||
                            current.contentRange?.endInclusive?.let { end ->
                                totalLength > end + 1L
                            } == true
                        )
                )
            FetchedPreviewBytes(
                bytes = if (exceededLimit) readBytes.copyOf(maximumBytes) else readBytes,
                mimeType = current.metadata.contentType,
                entityTag = current.metadata.etag.strongEntityTagOrNull(),
                truncated = truncated,
            )
        }
    } finally {
        response.close()
    }
}

private fun validateCompleteResponse(
    response: WebDavReadResponse,
    actualBytes: Int,
    totalLength: Long?,
) {
    if (response.statusCode == 206) {
        val range = response.contentRange
            ?: throw WebDavException.InvalidResponse("Preview response omitted Content-Range")
        if (range.start != 0L || totalLength == null ||
            range.endInclusive + 1L != totalLength || actualBytes.toLong() != totalLength
        ) {
            throw WebDavException.InvalidResponse("Preview response did not contain the complete file")
        }
    } else if (totalLength != null && actualBytes.toLong() != totalLength) {
        throw WebDavException.InvalidResponse("Preview response length did not match Content-Length")
    }
}

private fun InputStream.readAtMost(maximumBytes: Int): ByteArray {
    val output = ByteArray(maximumBytes)
    var offset = 0
    while (offset < maximumBytes) {
        val read = read(output, offset, maximumBytes - offset)
        if (read < 0) break
        if (read == 0) continue
        offset += read
    }
    return if (offset == output.size) output else output.copyOf(offset)
}

private data class FetchedPreviewBytes(
    val bytes: ByteArray,
    val mimeType: String?,
    val entityTag: String?,
    val truncated: Boolean,
)

internal data class DecodedPreviewText(
    val text: String,
    val truncated: Boolean,
    val charsetName: String = "UTF-8",
    val hasBom: Boolean = false,
    val encodingEditable: Boolean = true,
)

internal fun decodePlainText(
    bytes: ByteArray,
    mimeType: String?,
    sourceTruncated: Boolean,
): DecodedPreviewText? {
    val bom = detectBom(bytes)
    val bomCharset = bom?.charset ?: if (bom != null) return null else null
    val payload = bytes.copyOfRange(bom?.length ?: 0, bytes.size)
    val declaration = parseCharsetDeclaration(mimeType)
    val declaredCharset = declaration.charset
    val charset = bomCharset ?: declaredCharset ?: Charsets.UTF_8
    val decoded = decodeStrict(payload, charset, allowTrailingTrim = sourceTruncated)
        ?.removePrefix("\uFEFF")
        ?.takeIf(::isPlausibleText)
        ?: return null
    return limitPreviewText(
        value = decoded,
        alreadyTruncated = sourceTruncated,
        charsetName = charset.name(),
        hasBom = bom != null,
        encodingEditable = bom?.editable != false &&
            (!declaration.present || declaration.valid && declaredCharset != null) &&
            (bomCharset == null || declaredCharset == null || charsetsCompatible(declaredCharset, bomCharset)) &&
            isStrictlyEditableText(decoded),
    )
}

internal fun encodeEditedText(
    text: String,
    charsetName: String,
    includeBom: Boolean,
): ByteArray {
    val requestedCharset = runCatching { Charset.forName(charsetName) }
        .getOrElse { throw IllegalArgumentException("Unsupported text encoding", it) }
    val charset = if (!includeBom && requestedCharset.name().equals("UTF-16", ignoreCase = true)) {
        Charsets.UTF_16BE
    } else {
        requestedCharset
    }
    val content = try {
        val encoded = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text))
        ByteArray(encoded.remaining()).also(encoded::get)
    } catch (error: Exception) {
        throw TextEncodingException(error)
    }
    if (!includeBom || requestedCharset.name().equals("UTF-16", ignoreCase = true)) return content
    val bom = when {
        charset == Charsets.UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        charset == Charsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        charset == Charsets.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        else -> return content
    }
    return bom + content
}

private fun detectBom(bytes: ByteArray): DetectedBom? = when {
    bytes.startsWith(0xFF, 0xFE, 0x00, 0x00) -> DetectedBom(
        charset = runCatching { Charset.forName("UTF-32LE") }.getOrNull(),
        length = 4,
        editable = false,
    )
    bytes.startsWith(0x00, 0x00, 0xFE, 0xFF) -> DetectedBom(
        charset = runCatching { Charset.forName("UTF-32BE") }.getOrNull(),
        length = 4,
        editable = false,
    )
    bytes.startsWith(0xEF, 0xBB, 0xBF) -> DetectedBom(Charsets.UTF_8, 3)
    bytes.startsWith(0xFF, 0xFE) -> DetectedBom(Charsets.UTF_16LE, 2)
    bytes.startsWith(0xFE, 0xFF) -> DetectedBom(Charsets.UTF_16BE, 2)
    else -> null
}

private data class DetectedBom(
    val charset: Charset?,
    val length: Int,
    val editable: Boolean = true,
)

private fun ByteArray.startsWith(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index -> this[index].toInt() and 0xFF == expected[index] }

private fun allowedCharset(name: String): Charset? {
    val canonicalName = ALLOWED_CHARSETS[name] ?: return null
    return runCatching { Charset.forName(canonicalName) }.getOrNull()
}

private data class CharsetDeclaration(
    val present: Boolean,
    val valid: Boolean,
    val charset: Charset?,
)

private fun parseCharsetDeclaration(contentType: String?): CharsetDeclaration {
    if (contentType == null) return CharsetDeclaration(false, true, null)
    val parameters = splitContentTypeParameters(contentType)
        ?: return CharsetDeclaration(true, false, null)
    val charsetValues = mutableListOf<String>()
    var malformed = false
    for (parameter in parameters.drop(1)) {
        val separator = parameter.indexOf('=')
        val key = if (separator >= 0) parameter.substring(0, separator).trim() else parameter.trim()
        if (!key.equals("charset", ignoreCase = true)) continue
        if (separator < 0) {
            malformed = true
            continue
        }
        val rawValue = parameter.substring(separator + 1).trim()
        val value = when {
            rawValue.startsWith('"') && rawValue.endsWith('"') && rawValue.length >= 2 ->
                rawValue.substring(1, rawValue.lastIndex)
            '"' in rawValue || '\\' in rawValue -> {
                malformed = true
                ""
            }
            else -> rawValue
        }.trim().lowercase()
        if (value.isEmpty()) malformed = true else charsetValues += value
    }
    if (charsetValues.isEmpty()) return CharsetDeclaration(malformed, !malformed, null)
    val resolved = charsetValues.map(::allowedCharset)
    val valid = !malformed && resolved.all { it != null } &&
        resolved.mapNotNull { it?.name()?.lowercase() }.distinct().size == 1
    return CharsetDeclaration(true, valid, resolved.firstOrNull())
}

private fun splitContentTypeParameters(value: String): List<String>? {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    for (character in value) {
        when {
            character == '"' -> {
                quoted = !quoted
                current.append(character)
            }
            character == ';' && !quoted -> {
                result += current.toString()
                current.clear()
            }
            else -> current.append(character)
        }
    }
    if (quoted) return null
    result += current.toString()
    return result
}

private fun charsetsCompatible(declared: Charset, bom: Charset): Boolean =
    declared.name().equals(bom.name(), ignoreCase = true) ||
        declared.name().equals("UTF-16", ignoreCase = true) &&
        (bom == Charsets.UTF_16LE || bom == Charsets.UTF_16BE)

private fun decodeStrict(bytes: ByteArray, charset: Charset, allowTrailingTrim: Boolean): String? {
    val maximumTrim = if (allowTrailingTrim) minOf(4, bytes.size) else 0
    for (trim in 0..maximumTrim) {
        val length = bytes.size - trim
        val decoded = runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, length))
                .toString()
        }.getOrNull()
        if (decoded != null) return decoded
    }
    return null
}

private fun isPlausibleText(value: String): Boolean {
    val disallowedControls = value.count { character ->
        Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t'
    }
    return disallowedControls <= maxOf(4, value.length / 100)
}

private fun isStrictlyEditableText(value: String): Boolean = value.none { character ->
    Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t'
}

internal fun isPlainTextRepresentation(contentType: String?): Boolean {
    val baseType = contentType?.substringBefore(';')?.trim()?.lowercase()
    if (baseType.isNullOrEmpty()) return true
    if (!MEDIA_TYPE_PATTERN.matches(baseType)) return false
    if (baseType in GENERIC_TEXT_CONTENT_TYPES) return true
    if (baseType == "text/rtf") return false
    return baseType.startsWith("text/") || baseType in PLAIN_TEXT_CONTENT_TYPES
}

private fun limitPreviewText(
    value: String,
    alreadyTruncated: Boolean = false,
    charsetName: String = "UTF-8",
    hasBom: Boolean = false,
    encodingEditable: Boolean = true,
): DecodedPreviewText {
    var end = minOf(value.length, MAX_TEXT_CHARACTERS)
    var lines = 1
    var index = 0
    while (index < end) {
        if (value[index] == '\n' && ++lines > MAX_TEXT_LINES) {
            end = index
            break
        }
        index++
    }
    return DecodedPreviewText(
        text = value.substring(0, end),
        truncated = alreadyTruncated || end < value.length,
        charsetName = charsetName,
        hasBom = hasBom,
        encodingEditable = encodingEditable,
    )
}

internal fun parseRtfText(bytes: ByteArray): DecodedPreviewText? {
    val source = bytes.toString(Charsets.ISO_8859_1)
    val rootStart = source.indexOfFirst { !it.isWhitespace() }
    if (rootStart < 0 || !source.startsWith("{\\rtf", rootStart)) return null
    val versionStart = rootStart + "{\\rtf".length
    var versionEnd = versionStart
    while (versionEnd < source.length && source[versionEnd].isDigit()) versionEnd++
    if (source.substring(versionStart, versionEnd) != "1" ||
        source.getOrNull(versionEnd)?.let {
            !it.isWhitespace() && it != '\\' && it != '}' && it != '{'
        } == true
    ) {
        return null
    }
    val output = LimitedTextBuilder()
    val states = ArrayDeque<RtfState>()
    var state = RtfState()
    var fallbackCharacters = 0
    var index = rootStart
    var rootClosed = false
    var events = 0
    while (index < source.length) {
        if (++events % PARSER_CANCELLATION_INTERVAL == 0) checkParserCancellation()
        if (rootClosed) {
            while (index < source.length) {
                if (!source[index].isWhitespace()) return null
                index++
            }
            break
        }
        when (val character = source[index]) {
            '{' -> {
                if (states.size >= MAX_RTF_GROUP_DEPTH) return null
                states.addLast(state.copy())
                index++
            }
            '}' -> {
                if (states.isEmpty()) return null
                state = states.removeLast()
                index++
                if (states.isEmpty()) rootClosed = true
            }
            '\\' -> {
                index++
                if (index >= source.length) break
                val escaped = source[index]
                if (escaped == '\\' || escaped == '{' || escaped == '}') {
                    if (!state.skip && fallbackCharacters-- <= 0) output.append(escaped)
                    fallbackCharacters = fallbackCharacters.coerceAtLeast(0)
                    index++
                } else if (escaped == '\'') {
                    val hex = source.substring(index + 1, minOf(index + 3, source.length))
                    val value = hex.toIntOrNull(16)
                    if (value == null || hex.length != 2) return null
                    if (!state.skip && fallbackCharacters-- <= 0) {
                        output.append(byteArrayOf(value.toByte()).toString(state.charset))
                    }
                    fallbackCharacters = fallbackCharacters.coerceAtLeast(0)
                    index += 3
                } else if (!escaped.isLetter()) {
                    when (escaped) {
                        '*' -> state.skip = true
                        '~' -> if (!state.skip) output.append(' ')
                        '_' -> if (!state.skip) output.append('-')
                    }
                    index++
                } else {
                    val wordStart = index
                    while (index < source.length && source[index].isLetter()) index++
                    val word = source.substring(wordStart, index)
                    val numberStart = index
                    if (index < source.length && (source[index] == '-' || source[index] == '+')) index++
                    while (index < source.length && source[index].isDigit()) index++
                    val parameter = source.substring(numberStart, index).toIntOrNull()
                    if (index < source.length && source[index] == ' ') index++
                    if (word == "bin") {
                        val length = parameter ?: return null
                        if (length < 0 || length > source.length - index) return null
                        index += length
                        continue
                    }
                    if (word in RTF_DESTINATIONS) state.skip = true
                    if (!state.skip) {
                        when (word) {
                            "par", "line" -> output.append('\n')
                            "tab" -> output.append('\t')
                            "uc" -> state.unicodeFallbackLength = parameter?.coerceIn(0, 16) ?: 1
                            "ansicpg" -> state.charset = RTF_CODE_PAGES[parameter] ?: return null
                            "u" -> if (parameter != null) {
                                val codePoint = if (parameter < 0) parameter + 65_536 else parameter
                                if (codePoint !in 0..65_535) return null
                                output.append(codePoint.toChar())
                                fallbackCharacters = state.unicodeFallbackLength
                            }
                            "emdash" -> output.append('\u2014')
                            "endash" -> output.append('\u2013')
                            "bullet" -> output.append('\u2022')
                            "lquote", "rquote" -> output.append('\'')
                            "ldblquote", "rdblquote" -> output.append('"')
                        }
                    }
                }
            }
            '\r', '\n' -> index++
            else -> {
                if (!state.skip && fallbackCharacters-- <= 0) {
                    if (character.code < 128) {
                        output.append(character)
                    } else {
                        output.append(byteArrayOf(character.code.toByte()).toString(state.charset))
                    }
                }
                fallbackCharacters = fallbackCharacters.coerceAtLeast(0)
                index++
            }
        }
    }
    if (!rootClosed || states.isNotEmpty()) return null
    return DecodedPreviewText(output.toString().trim(), output.full)
}

private data class RtfState(
    var skip: Boolean = false,
    var unicodeFallbackLength: Int = 1,
    var charset: Charset = WINDOWS_1252,
)

private enum class OfficeDocumentKind(val entryName: String) {
    DOCX("word/document.xml"),
    ODT("content.xml"),
}

private fun extractOfficeText(bytes: ByteArray, kind: OfficeDocumentKind): DecodedPreviewText {
    val xml = try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            var inflatedBytes = 0
            var documentXml: ByteArray? = null
            while (true) {
                checkParserCancellation()
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw WebDavException.InvalidResponse("Document preview has too many ZIP entries")
                }
                val remainingBudget = MAX_ZIP_INFLATED_BYTES - inflatedBytes
                if (remainingBudget <= 0 || entry.size > remainingBudget.toLong()) {
                    throw WebDavException.ResponseTooLarge(MAX_ZIP_INFLATED_BYTES.toLong())
                }
                val entryBytes = if (!entry.isDirectory && entry.name == kind.entryName) {
                    if (documentXml != null) {
                        throw WebDavException.InvalidResponse("Document preview has duplicate ${kind.entryName}")
                    }
                    if (entry.size > MAX_DOCUMENT_XML_BYTES.toLong()) {
                        throw WebDavException.ResponseTooLarge(MAX_DOCUMENT_XML_BYTES.toLong())
                    }
                    val content = zip.readAtMost(minOf(MAX_DOCUMENT_XML_BYTES, remainingBudget) + 1)
                    if (content.size > MAX_DOCUMENT_XML_BYTES || content.size > remainingBudget) {
                        throw WebDavException.ResponseTooLarge(MAX_DOCUMENT_XML_BYTES.toLong())
                    }
                    documentXml = content
                    content.size
                } else {
                    val drained = zip.drainAtMost(remainingBudget + 1)
                    if (drained > remainingBudget) {
                        throw WebDavException.ResponseTooLarge(MAX_ZIP_INFLATED_BYTES.toLong())
                    }
                    drained
                }
                inflatedBytes += entryBytes
                zip.closeEntry()
            }
            documentXml
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: WebDavException) {
        throw error
    } catch (error: Exception) {
        throw WebDavException.InvalidResponse("Document preview ZIP is invalid", error)
    } ?: throw WebDavException.InvalidResponse("Document preview is missing ${kind.entryName}")
    return parseOfficeXml(xml, kind)
}

private fun InputStream.drainAtMost(maximumBytes: Int): Int {
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (total < maximumBytes) {
        checkParserCancellation()
        val read = read(buffer, 0, minOf(buffer.size, maximumBytes - total))
        if (read < 0) break
        if (read == 0) continue
        total += read
    }
    return total
}

private fun preflightOfficeXml(xml: ByteArray) {
    val bom = detectBom(xml)
    val charset = bom?.charset ?: if (bom != null) {
        throw WebDavException.InvalidResponse("Document preview XML encoding is unsupported")
    } else {
        Charsets.UTF_8
    }
    val payload = xml.copyOfRange(bom?.length ?: 0, xml.size)
    val source = decodeStrict(payload, charset, allowTrailingTrim = false)
        ?: throw WebDavException.InvalidResponse("Document preview XML encoding is invalid")
    var index = 0
    var tokens = 0
    while (index < source.length) {
        if (source[index] != '<') {
            index++
            continue
        }
        if (++tokens % PARSER_CANCELLATION_INTERVAL == 0) checkParserCancellation()
        if (source.startsWith("<![CDATA[", index)) {
            val end = source.indexOf("]]>", index + 9)
            if (end < 0 || end - index > MAX_XML_TEXT_TOKEN) {
                throw WebDavException.InvalidResponse("Document preview XML CDATA is too large")
            }
            index = end + 3
            continue
        }
        if (source.startsWith("<!--", index)) {
            val end = source.indexOf("-->", index + 4)
            if (end < 0 || end - index > MAX_XML_MARKUP_TOKEN) {
                throw WebDavException.InvalidResponse("Document preview XML comment is too large")
            }
            index = end + 3
            continue
        }
        if (source.regionMatches(index, "<!DOCTYPE", 0, "<!DOCTYPE".length, ignoreCase = true) ||
            source.regionMatches(index, "<!ENTITY", 0, "<!ENTITY".length, ignoreCase = true)
        ) {
            throw WebDavException.InvalidResponse("Document preview XML contains a DTD or entity declaration")
        }

        val start = index++
        var quote: Char? = null
        var attributes = 0
        var closed = false
        while (index < source.length) {
            if (index - start > MAX_XML_MARKUP_TOKEN) {
                throw WebDavException.InvalidResponse("Document preview XML markup token is too large")
            }
            val character = source[index++]
            if (quote != null) {
                if (character == quote) quote = null
            } else {
                when (character) {
                    '\'', '"' -> quote = character
                    '=' -> if (++attributes > MAX_XML_ATTRIBUTES) {
                        throw WebDavException.InvalidResponse("Document preview XML has too many attributes")
                    }
                    '>' -> {
                        closed = true
                        break
                    }
                }
            }
        }
        if (!closed || quote != null) {
            throw WebDavException.InvalidResponse("Document preview XML markup is incomplete")
        }
    }
}

private fun parseOfficeXml(xml: ByteArray, kind: OfficeDocumentKind): DecodedPreviewText = try {
    preflightOfficeXml(xml)
    val parser = KXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(ByteArrayInputStream(xml), null)
    }
    val output = LimitedTextBuilder()
    var textDepth = -1
    var paragraphDepth = -1
    var events = 0
    var rootSeen = false
    while (parser.eventType != XmlPullParser.END_DOCUMENT && !output.full) {
        val event = parser.eventType
        if (++events > MAX_XML_EVENTS) {
            throw WebDavException.InvalidResponse("Document preview XML has too many events")
        }
        if (events % PARSER_CANCELLATION_INTERVAL == 0) checkParserCancellation()
        if (parser.depth > MAX_XML_DEPTH) {
            throw WebDavException.InvalidResponse("Document preview XML is nested too deeply")
        }
        when (event) {
            XmlPullParser.DOCDECL -> throw WebDavException.InvalidResponse(
                "Document preview XML contains a DTD",
            )
            XmlPullParser.START_TAG -> {
                if (parser.attributeCount > MAX_XML_ATTRIBUTES) {
                    throw WebDavException.InvalidResponse("Document preview XML has too many attributes")
                }
                if (!rootSeen) {
                    val validRoot = when (kind) {
                        OfficeDocumentKind.DOCX ->
                            parser.name == "document" && parser.namespace == DOCX_WORD_NAMESPACE
                        OfficeDocumentKind.ODT ->
                            parser.name == "document-content" && parser.namespace == ODT_OFFICE_NAMESPACE
                    }
                    if (!validRoot) throw WebDavException.InvalidResponse("Document preview XML root is invalid")
                    rootSeen = true
                }
                when (kind) {
                    OfficeDocumentKind.DOCX -> if (parser.namespace == DOCX_WORD_NAMESPACE) {
                        when (parser.name) {
                            "t" -> textDepth = parser.depth
                            "tab" -> output.append('\t')
                            "br", "cr" -> output.append('\n')
                        }
                    }
                    OfficeDocumentKind.ODT -> if (parser.namespace == ODT_TEXT_NAMESPACE) {
                        when (parser.name) {
                            "p", "h" -> paragraphDepth = parser.depth
                            "tab" -> if (paragraphDepth >= 0) output.append('\t')
                            "line-break" -> if (paragraphDepth >= 0) output.append('\n')
                            "s" -> if (paragraphDepth >= 0) {
                                val count = (
                                    parser.getAttributeValue(ODT_TEXT_NAMESPACE, "c")
                                        ?: parser.getAttributeValue(null, "c")
                                    )?.toIntOrNull()?.coerceIn(1, 32) ?: 1
                                repeat(count) { output.append(' ') }
                            }
                        }
                    }
                }
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                if (event == XmlPullParser.ENTITY_REF && !isAllowedXmlEntity(parser.name)) {
                    throw WebDavException.InvalidResponse("Document preview XML contains an unknown entity")
                }
                val text = parser.text.orEmpty()
                if (text.length > MAX_XML_TEXT_TOKEN) {
                    throw WebDavException.InvalidResponse("Document preview XML text token is too large")
                }
                when (kind) {
                    OfficeDocumentKind.DOCX -> if (textDepth >= 0) output.append(text)
                    OfficeDocumentKind.ODT -> if (paragraphDepth >= 0) output.append(text)
                }
            }
            XmlPullParser.END_TAG -> when (kind) {
                OfficeDocumentKind.DOCX -> if (parser.namespace == DOCX_WORD_NAMESPACE) {
                    if (parser.name == "t" && parser.depth == textDepth) textDepth = -1
                    if (parser.name == "p") output.append('\n')
                }
                OfficeDocumentKind.ODT -> if (parser.namespace == ODT_TEXT_NAMESPACE &&
                    (parser.name == "p" || parser.name == "h") && parser.depth == paragraphDepth
                ) {
                    output.append('\n')
                    paragraphDepth = -1
                }
            }
        }
        parser.nextToken()
    }
    if (!rootSeen) throw WebDavException.InvalidResponse("Document preview XML has no root element")
    DecodedPreviewText(output.toString().trim(), output.full)
} catch (error: CancellationException) {
    throw error
} catch (error: WebDavException) {
    throw error
} catch (error: Exception) {
    throw WebDavException.InvalidResponse("Document preview XML is invalid", error)
}

private class LimitedTextBuilder {
    private val content = StringBuilder()
    var full: Boolean = false
        private set
    private var lines = 1

    fun append(character: Char) {
        if (full) return
        if (character == '\n' && ++lines > MAX_TEXT_LINES) {
            full = true
            return
        }
        if (content.length >= MAX_TEXT_CHARACTERS) {
            full = true
            return
        }
        content.append(character)
    }

    fun append(value: String) {
        for (character in value) {
            append(character)
            if (full) break
        }
    }

    override fun toString(): String = content.toString()
}

private fun checkParserCancellation() {
    if (Thread.currentThread().isInterrupted) throw CancellationException("Preview parsing cancelled")
}

private fun isAllowedXmlEntity(name: String?): Boolean {
    if (name in PREDEFINED_XML_ENTITIES) return true
    val value = when {
        name?.startsWith("#x", ignoreCase = true) == true -> name.substring(2).toIntOrNull(16)
        name?.startsWith('#') == true -> name.substring(1).toIntOrNull()
        else -> null
    } ?: return false
    return value == 0x09 || value == 0x0A || value == 0x0D ||
        value in 0x20..0xD7FF || value in 0xE000..0xFFFD || value in 0x10000..0x10FFFF
}

internal fun hasSupportedImageSignature(bytes: ByteArray): Boolean =
    bytes.startsWith(0xFF, 0xD8, 0xFF) ||
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ||
        bytes.startsWith(0x47, 0x49, 0x46, 0x38) ||
        bytes.startsWith(0x42, 0x4D) ||
        bytes.size >= 12 &&
        bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())

private val ALLOWED_CHARSETS = mapOf(
    "utf-8" to "UTF-8",
    "utf8" to "UTF-8",
    "utf-16" to "UTF-16",
    "utf-16le" to "UTF-16LE",
    "utf-16be" to "UTF-16BE",
    "gbk" to "GBK",
    "gb2312" to "GB2312",
    "gb18030" to "GB18030",
    "windows-1252" to "windows-1252",
    "iso-8859-1" to "ISO-8859-1",
)
private val GENERIC_TEXT_CONTENT_TYPES = setOf("application/octet-stream", "binary/octet-stream")
private val PLAIN_TEXT_CONTENT_TYPES = setOf(
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
private val MEDIA_TYPE_PATTERN = Regex(
    "[a-z0-9!#$&^_.+\\-]+/[a-z0-9!#$&^_.+\\-]+",
)
private val GB18030: Charset = Charset.forName("GB18030")
private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
private val RTF_DESTINATIONS = setOf(
    "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "headerl",
    "headerr", "footer", "footerl", "footerr", "generator", "xmlopen", "xmlattrname",
    "xmlattrvalue", "datastore", "themedata",
)
private val RTF_CODE_PAGES = mapOf(
    1_252 to WINDOWS_1252,
    28_591 to Charsets.ISO_8859_1,
)
private val PREDEFINED_XML_ENTITIES = setOf("lt", "gt", "amp", "apos", "quot")
private const val DOCX_WORD_NAMESPACE =
    "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val ODT_OFFICE_NAMESPACE =
    "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
private const val ODT_TEXT_NAMESPACE =
    "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
private const val PREVIEW_TIMEOUT_MILLIS = 30_000L
private const val TEXT_FETCH_BYTES = 512 * 1024
private const val MAX_TEXT_CHARACTERS = 100_000
private const val MAX_TEXT_LINES = 5_000
private const val MAX_RTF_GROUP_DEPTH = 128
private const val MAX_ZIP_ENTRIES = 256
private const val MAX_ZIP_INFLATED_BYTES = 16 * 1024 * 1024
private const val MAX_DOCUMENT_XML_BYTES = 512 * 1024
private const val MAX_XML_DEPTH = 128
private const val MAX_XML_ATTRIBUTES = 64
private const val MAX_XML_EVENTS = 100_000
private const val MAX_XML_TEXT_TOKEN = 64 * 1024
private const val MAX_XML_MARKUP_TOKEN = 8 * 1024
private const val PARSER_CANCELLATION_INTERVAL = 1_024

internal class TextEncodingException(cause: Throwable) : IllegalArgumentException(
    "Text contains characters that cannot be represented by its original encoding",
    cause,
)
