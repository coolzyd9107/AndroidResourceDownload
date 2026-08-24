package link.mczihan.androidResourceDownload.data.auth

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AuthApiWireTest {
    @Test
    fun qqLoginUsesDocumentedPathAndCamelCaseBody() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "code": 0,
                      "message": "ok",
                      "data": {
                        "accessToken": "app-access",
                        "refreshToken": "app-refresh",
                        "expiresIn": 900,
                        "user": {
                          "id": "qq-user",
                          "name": "QQ User",
                          "email": null,
                          "role": "USER",
                          "avatarUrl": null,
                          "loginType": "QQ"
                        }
                      }
                    }
                    """.trimIndent(),
                ),
        )
        server.start()
        try {
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(
                    Json { ignoreUnknownKeys = true }
                        .asConverterFactory("application/json".toMediaType()),
                )
                .build()
                .create(AuthApi::class.java)

            val response = runBlocking {
                api.loginWithQq(
                    QqLoginRequestDto(
                        accessToken = "provider-access",
                        openId = "provider-open-id",
                    ),
                )
            }
            val recorded = server.takeRequest()
            val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject

            assertEquals(200, response.code())
            assertEquals("/api/v1/auth/qq/login", recorded.path)
            assertEquals("provider-access", body.getValue("accessToken").jsonPrimitive.content)
            assertEquals("provider-open-id", body.getValue("openId").jsonPrimitive.content)
            assertEquals(setOf("accessToken", "openId"), body.keys)
            assertFalse(
                QqLoginRequestDto("secret", "openid", "device").toString().contains("secret"),
            )
        } finally {
            server.shutdown()
        }
    }
}
