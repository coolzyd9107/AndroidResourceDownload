package link.mczihan.androidResourceDownload.auth

import kotlinx.serialization.json.Json
import link.mczihan.androidResourceDownload.data.auth.BackendApiException
import link.mczihan.androidResourceDownload.data.auth.BackendEnvelope
import link.mczihan.androidResourceDownload.data.auth.LoginResponseDto
import link.mczihan.androidResourceDownload.data.auth.dataOrThrow
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.data.auth.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendEnvelopeTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loginEnvelopeDecodesNullableUserFields() {
        val encoded = """
            {
              "code": 0,
              "message": "ok",
              "data": {
                "accessToken": "access",
                "refreshToken": "refresh",
                "expiresIn": 900,
                "user": {
                  "id": "user-id",
                  "name": null,
                  "email": null,
                  "role": "ADMIN",
                  "avatarUrl": null,
                  "loginType": "GITHUB"
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<BackendEnvelope<LoginResponseDto>>(encoded)
            .dataOrThrow()
        val user = response.user.toDomain()

        assertEquals("access", response.accessToken)
        assertEquals(900, response.expiresIn)
        assertNull(user.name)
        assertNull(user.email)
        assertEquals(Role.ADMIN, user.role)
        assertEquals(LoginType.GITHUB, user.loginType)
    }

    @Test
    fun errorEnvelopeProducesTypedBackendFailure() {
        val encoded = """
            {"code":10002,"message":"token_expired","data":null}
        """.trimIndent()
        val envelope = json.decodeFromString<BackendEnvelope<LoginResponseDto>>(encoded)

        val error = assertThrows(BackendApiException::class.java) {
            envelope.dataOrThrow(httpStatus = 401)
        }

        assertEquals(10002, error.backendCode)
        assertEquals(401, error.httpStatus)
        assertEquals(true, error.isAuthenticationFailure)
    }
}
