package link.mczihan.androidResourceDownload.feature.auth

import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

object Pkce {
    private val random = SecureRandom()

    fun generateVerifier(): String = randomBytes(32)
        .toBase64Url()
        .take(64)

    fun generateState(): String = randomBytes(24).toBase64Url()

    fun challengeFor(verifier: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))
        .toBase64Url()

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun ByteArray.toBase64Url(): String = Base64.encodeToString(
        this,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )
}
