package link.mczihan.androidResourceDownload.core.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long?): String {
    if (bytes == null) return "--"
    if (bytes < 1_024L) return "$bytes B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_024.0 && unitIndex < units.lastIndex) {
        value /= 1_024.0
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

fun formatDate(epochMillis: Long?): String {
    if (epochMillis == null) return "--"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(epochMillis))
}
