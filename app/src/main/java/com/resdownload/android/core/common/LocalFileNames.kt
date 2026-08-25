package com.resdownload.android.core.common

fun collisionFileName(requestedName: String, index: Int): String {
    require(index >= 0) { "Collision index must not be negative" }
    if (index == 0) return requestedName
    val extensionStart = requestedName.lastIndexOf('.').takeIf { it > 0 } ?: requestedName.length
    val base = requestedName.substring(0, extensionStart)
    val extension = requestedName.substring(extensionStart)
    return "$base($index)$extension"
}
