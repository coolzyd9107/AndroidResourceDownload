package com.resdownload.android.domain.webdav

fun String?.strongEntityTagOrNull(): String? {
    val value = this ?: return null
    if (value.startsWith("W/", ignoreCase = true) ||
        value.length < 2 || value.first() != '"' || value.last() != '"'
    ) {
        return null
    }
    return value.takeIf { candidate ->
        candidate.substring(1, candidate.lastIndex).all { character ->
            character == '\u0021' || character in '\u0023'..'\u007e'
        }
    }
}
