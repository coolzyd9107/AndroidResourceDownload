package com.resdownload.android.data.auth

import com.resdownload.android.domain.model.AuthSession

interface AuthRepository {
    suspend fun restoreSession(): AuthSession?

    suspend fun currentSession(): AuthSession?

    suspend fun loginWithQq(
        accessToken: String,
        openId: String,
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
