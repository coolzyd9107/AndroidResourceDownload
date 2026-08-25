package com.resdownload.android.domain.webdav

enum class WebDavPermission {
    READ_ONLY,
    READ_WRITE,
}

class WebDavCredential(
    val username: String,
    val password: String,
    val permission: WebDavPermission,
    val baseUrl: String = "",
    val rootPath: WebDavPath = WebDavPath.root(),
    val expiresAtEpochMillis: Long? = null,
) {
    init {
        require(username.isNotBlank()) { "WebDAV username must not be blank" }
        require(':' !in username) { "WebDAV username must not contain a colon" }
        require(username.none { Character.isISOControl(it) }) {
            "WebDAV username must not contain control characters"
        }
    }

    fun isExpired(nowEpochMillis: Long, skewMillis: Long = 0L): Boolean {
        require(skewMillis >= 0L) { "Expiry skew must not be negative" }
        val expiresAt = expiresAtEpochMillis ?: return false
        val effectiveNow = if (nowEpochMillis > Long.MAX_VALUE - skewMillis) {
            Long.MAX_VALUE
        } else {
            nowEpochMillis + skewMillis
        }
        return expiresAt <= effectiveNow
    }

    override fun toString(): String =
        "WebDavCredential(username=$username, password=<redacted>, permission=$permission, " +
            "expiresAtEpochMillis=$expiresAtEpochMillis)"
}

data class CredentialLease(
    val credential: WebDavCredential,
    val generation: Long,
)

interface WebDavCredentialProvider {
    suspend fun acquire(): CredentialLease

    /** Invalidates only the matching generation, preserving a newer concurrent refresh. */
    suspend fun invalidate(generation: Long)

    /** Removes any credential currently held in memory. */
    suspend fun clear()
}

fun interface WebDavCredentialLoader {
    suspend fun load(): WebDavCredential
}
