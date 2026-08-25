package com.resdownload.android.data.auth

import kotlinx.coroutines.runBlocking
import com.resdownload.android.core.security.SessionStore
import com.resdownload.android.domain.model.AuthSession
import com.resdownload.android.domain.model.LoginType
import com.resdownload.android.domain.model.Role
import com.resdownload.android.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class DefaultAuthRepositoryTest {
    @Test
    fun qqLoginSendsProviderCredentialAndPersistsAppSession() {
        val store = InMemorySessionStore(null)
        var receivedRequest: QqLoginRequestDto? = null
        val api = object : AuthApi by unsupportedAuthApi() {
            override suspend fun loginWithQq(
                request: QqLoginRequestDto,
            ): Response<BackendEnvelope<LoginResponseDto>> {
                receivedRequest = request
                return Response.success(
                    BackendEnvelope(
                        0,
                        "ok",
                        LoginResponseDto(
                            accessToken = "app-access",
                            refreshToken = "app-refresh",
                            expiresIn = 900,
                            user = BackendUserDto(
                                id = "qq-user",
                                name = "QQ User",
                                role = "USER",
                                loginType = "QQ",
                            ),
                        ),
                    ),
                )
            }
        }
        val repository = DefaultAuthRepository(api, store, nowEpochMillis = { 1_000L })

        val session = runBlocking {
            repository.loginWithQq("provider-access", "provider-open-id", "device")
        }

        assertEquals("provider-access", receivedRequest?.accessToken)
        assertEquals("provider-open-id", receivedRequest?.openId)
        assertEquals("device", receivedRequest?.deviceId)
        assertEquals("app-access", session.accessToken)
        assertEquals(901_000L, session.expiresAtEpochMillis)
        assertEquals(session, runBlocking { store.read() })
    }

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
            email = null,
            role = Role.ADMIN,
            loginType = LoginType.QQ,
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
