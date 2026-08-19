package link.mczihan.androidResourceDownload.core.security

import link.mczihan.androidResourceDownload.domain.model.AuthSession

interface SessionStore {
    suspend fun read(): AuthSession?

    suspend fun write(session: AuthSession)

    suspend fun clear()
}
