package link.mczihan.androidResourceDownload.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long,
    val user: User,
) {
    fun hasUsableAccessToken(
        nowEpochMillis: Long,
        minimumValidityMillis: Long = 0L,
    ): Boolean = accessToken.isNotBlank() &&
        expiresAtEpochMillis > nowEpochMillis + minimumValidityMillis.coerceAtLeast(0L)

    override fun toString(): String =
        "AuthSession(accessToken=<redacted>, refreshToken=<redacted>, " +
            "expiresAtEpochMillis=$expiresAtEpochMillis, user=<redacted>)"
}
