package com.resdownload.android.core.security

import com.resdownload.android.domain.model.AuthSession

interface SessionStore {
    suspend fun read(): AuthSession?

    suspend fun write(session: AuthSession)

    suspend fun clear()
}
