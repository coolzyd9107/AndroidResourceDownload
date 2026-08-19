package link.mczihan.androidResourceDownload.data.auth

import link.mczihan.androidResourceDownload.domain.model.AuthSession

data class EmailCodeChallenge(
    val expiresInSeconds: Int,
)

interface AuthRepository {
    suspend fun restoreSession(): AuthSession?

    suspend fun currentSession(): AuthSession?

    suspend fun requestEmailCode(email: String): EmailCodeChallenge

    suspend fun loginWithEmail(
        email: String,
        code: String,
        deviceId: String = "",
    ): AuthSession

    suspend fun completeGitHubLogin(
        code: String,
        codeVerifier: String,
        deviceId: String = "",
    ): AuthSession

    suspend fun refreshSession(): AuthSession?

    suspend fun synchronizeUser(): AuthSession?

    suspend fun logout(session: AuthSession? = null)
}
