package link.mczihan.androidResourceDownload.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import link.mczihan.androidResourceDownload.domain.model.AuthSession
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User

@Singleton
class EncryptedSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionStore {
    private val json = Json { ignoreUnknownKeys = false }
    private val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            PREFERENCES_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun read(): AuthSession? = withContext(Dispatchers.IO) {
        val encoded = try {
            preferences.getString(SESSION_KEY, null)
        } catch (_: Exception) {
            clearBestEffort()
            return@withContext null
        } ?: return@withContext null

        try {
            json.decodeFromString<PersistedSession>(encoded).toDomain().also(::validate)
        } catch (_: Exception) {
            clearBestEffort()
            null
        }
    }

    override suspend fun write(session: AuthSession) = withContext(Dispatchers.IO) {
        validate(session)
        val encoded = json.encodeToString(PersistedSession.fromDomain(session))
        try {
            if (!preferences.edit().putString(SESSION_KEY, encoded).commit()) {
                throw IOException("Encrypted session commit failed")
            }
        } catch (error: Exception) {
            clearBestEffort()
            throw SessionStorageException("Unable to persist encrypted session", error)
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            if (!preferences.edit().remove(SESSION_KEY).commit()) {
                throw IOException("Encrypted session removal failed")
            }
        } catch (error: Exception) {
            throw SessionStorageException("Unable to clear encrypted session", error)
        }
    }

    private fun clearBestEffort() {
        runCatching { preferences.edit().remove(SESSION_KEY).commit() }
    }

    private fun validate(session: AuthSession) {
        require(session.accessToken.isNotBlank()) { "Access token must not be blank" }
        require(session.refreshToken.isNotBlank()) { "Refresh token must not be blank" }
        require(session.expiresAtEpochMillis > 0L) { "Session expiry must be positive" }
        require(session.user.id.isNotBlank()) { "User id must not be blank" }
    }

    private companion object {
        const val PREFERENCES_NAME = "auth_session"
        const val SESSION_KEY = "session_v1"
    }
}

class SessionStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Serializable
private data class PersistedSession(
    val schemaVersion: Int,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long,
    val user: PersistedUser,
) {
    fun toDomain(): AuthSession {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported session schema" }
        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochMillis = expiresAtEpochMillis,
            user = user.toDomain(),
        )
    }

    companion object {
        private const val CURRENT_SCHEMA_VERSION = 1

        fun fromDomain(session: AuthSession): PersistedSession = PersistedSession(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAtEpochMillis = session.expiresAtEpochMillis,
            user = PersistedUser.fromDomain(session.user),
        )
    }
}

@Serializable
private data class PersistedUser(
    val id: String,
    val name: String?,
    val email: String?,
    val role: Role,
    val loginType: LoginType,
    val avatarUrl: String?,
) {
    fun toDomain(): User = User(
        id = id,
        name = name,
        email = email,
        role = role,
        loginType = loginType,
        avatarUrl = avatarUrl,
    )

    companion object {
        fun fromDomain(user: User): PersistedUser = PersistedUser(
            id = user.id,
            name = user.name,
            email = user.email,
            role = user.role,
            loginType = user.loginType,
            avatarUrl = user.avatarUrl,
        )
    }
}
