package link.mczihan.androidResourceDownload.feature.auth

import java.util.Locale
import link.mczihan.androidResourceDownload.domain.model.Role

fun roleForAllowedEmail(email: String): Role? {
    val normalized = email.trim().lowercase(Locale.ROOT)
    val separator = normalized.indexOf('@')
    if (separator <= 0 || separator != normalized.lastIndexOf('@')) return null

    val localPart = normalized.substring(0, separator)
    if (localPart.any(Char::isWhitespace)) return null

    return when (normalized.substring(separator + 1)) {
        "qq.com" -> Role.USER
        "mczihan.link" -> Role.ADMIN
        else -> null
    }
}
