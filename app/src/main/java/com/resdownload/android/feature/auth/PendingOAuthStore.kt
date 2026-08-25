package com.resdownload.android.feature.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PendingOAuth(
    val appState: String,
    val verifier: String,
    val createdAtMillis: Long,
)

@Singleton
class PendingOAuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            "oauth_pending_secure_v1",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(transaction: PendingOAuth) {
        preferences.edit()
            .putString(KEY_STATE, transaction.appState)
            .putString(KEY_VERIFIER, transaction.verifier)
            .putLong(KEY_CREATED, transaction.createdAtMillis)
            .apply()
    }

    fun read(): PendingOAuth? {
        val state = preferences.getString(KEY_STATE, null) ?: return null
        val verifier = preferences.getString(KEY_VERIFIER, null) ?: return null
        return PendingOAuth(state, verifier, preferences.getLong(KEY_CREATED, 0L))
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_VERIFIER = "verifier"
        const val KEY_CREATED = "created"
    }
}
