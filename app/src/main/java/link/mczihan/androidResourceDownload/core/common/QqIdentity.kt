package link.mczihan.androidResourceDownload.core.common

fun qqNumberFromEmail(email: String?): String? {
    val parts = email?.trim()?.split('@') ?: return null
    if (parts.size != 2 || !parts[1].equals("qq.com", ignoreCase = true)) return null
    return parts[0].takeIf { value -> value.isNotEmpty() && value.all { it in '0'..'9' } }
}
