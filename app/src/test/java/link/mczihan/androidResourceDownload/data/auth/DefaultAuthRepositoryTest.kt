package link.mczihan.androidResourceDownload.data.auth

import kotlinx.coroutines.runBlocking
import link.mczihan.androidResourceDownload.core.security.SessionStore
import link.mczihan.androidResourceDownload.domain.model.AuthSession
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class DefaultAuthRepositoryTest {
    @Test
    fun logoutWithoutSnapshotClearsAndRevokesLatestSession() {
        val latestSession = session(refreshToken = "rotated-refresh")
        val store = InMemorySessionStore(latestSession)
        var revokedAuthorization: String? = null
        var revokedRefreshToken: String? = null
        val api = object : AuthApi by unsupportedAuthApi() {
            override suspend fun logout(
                authorization: String,
                request: RefreshTokenRequestDto,
            ): Response<BackendEnvelope<StatusResponseDto>> {
                revokedAuthorization = authorization
                revokedRefreshToken = request.refreshToken
                return Response.success(
                    BackendEnvelope(0, "ok", StatusResponseDto("ok")),
                )
            }
        }
        val repository = DefaultAuthRepository(api, store)

        runBlocking { repository.logout() }

        assertNull(runBlocking { store.read() })
        assertEquals("Bearer rotated-access", revokedAuthorization)
        assertEquals("rotated-refresh", revokedRefreshToken)
    }

    private fun session(refreshToken: String) = AuthSession(
        accessToken = "rotated-access",
        refreshToken = refreshToken,
        expiresAtEpochMillis = Long.MAX_VALUE,
        user = User(
            id = "admin-id",
            name = "Admin",
            email = "admin@mczihan.link",
            role = Role.ADMIN,
            loginType = LoginType.EMAIL,
        ),
    )

    private class InMemorySessionStore(initial: AuthSession?) : SessionStore {
        private var session = initial

        override suspend fun read(): AuthSession? = session

        override suspend fun write(session: AuthSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }
}

private fun unsupportedAuthApi(): AuthApi = java.lang.reflect.Proxy.newProxyInstance(
    AuthApi::class.java.classLoader,
    arrayOf(AuthApi::class.java),
) { _, method, _ ->
    throw UnsupportedOperationException("Unexpected AuthApi call: ${method.name}")
} as AuthApi
