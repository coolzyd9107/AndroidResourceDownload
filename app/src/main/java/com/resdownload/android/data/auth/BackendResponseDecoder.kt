package com.resdownload.android.data.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.Response

private val backendJson = Json { ignoreUnknownKeys = true }

internal suspend fun <T : Any> executeBackendCall(
    call: suspend () -> Response<BackendEnvelope<T>>,
): T {
    val response = call()
    if (response.isSuccessful) {
        return response.body()
            ?.dataOrThrow(response.code())
            ?: throw BackendProtocolException(
                "Backend returned HTTP ${response.code()} without an envelope",
            )
    }

    val errorBody = response.errorBody()?.string()
        ?: throw BackendProtocolException(
            "Backend returned HTTP ${response.code()} without an error envelope",
        )
    val envelope = try {
        backendJson.decodeFromString<BackendErrorEnvelope>(errorBody)
    } catch (error: SerializationException) {
        throw BackendProtocolException(
            "Backend returned HTTP ${response.code()} with an invalid envelope",
            error,
        )
    }
    if (envelope.code == 0) {
        throw BackendProtocolException(
            "Backend returned an unsuccessful HTTP status with a success envelope",
        )
    }
    throw BackendApiException(envelope.code, envelope.message, response.code())
}

@Serializable
private data class BackendErrorEnvelope(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)
