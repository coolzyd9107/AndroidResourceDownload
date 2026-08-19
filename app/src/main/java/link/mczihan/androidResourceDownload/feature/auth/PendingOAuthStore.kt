package link.mczihan.androidResourceDownload.feature.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PendingOAuth(
    val state: String,
    val verifier: String,
    val redirectUri: String,
    val createdAtMillis: Long,
)

@Singleton
class PendingOAuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("oauth_pending", Context.MODE_PRIVATE)

    fun save(transaction: PendingOAuth) {
        preferences.edit()
            .putString(KEY_STATE, transaction.state)
            .putString(KEY_VERIFIER, transaction.verifier)
            .putString(KEY_REDIRECT, transaction.redirectUri)
            .putLong(KEY_CREATED, transaction.createdAtMillis)
            .apply()
    }

    fun read(): PendingOAuth? {
        val state = preferences.getString(KEY_STATE, null) ?: return null
        val verifier = preferences.getString(KEY_VERIFIER, null) ?: return null
        val redirect = preferences.getString(KEY_REDIRECT, null) ?: return null
        return PendingOAuth(state, verifier, redirect, preferences.getLong(KEY_CREATED, 0L))
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun consumeIfValid(state: String, nowMillis: Long, maxAgeMillis: Long): PendingOAuth? {
        val pending = read() ?: return null
        if (pending.state != state || nowMillis - pending.createdAtMillis !in 0..maxAgeMillis) {
            return null
        }
        clear()
        return pending
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_VERIFIER = "verifier"
        const val KEY_REDIRECT = "redirect"
        const val KEY_CREATED = "created"
    }
}
