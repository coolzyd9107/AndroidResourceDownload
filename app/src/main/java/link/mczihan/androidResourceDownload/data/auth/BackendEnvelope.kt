package link.mczihan.androidResourceDownload.data.auth

import java.io.IOException
import kotlinx.serialization.Serializable

@Serializable
data class BackendEnvelope<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
)

class BackendApiException(
    val backendCode: Int,
    val backendMessage: String,
    val httpStatus: Int? = null,
) : IOException("Backend request failed (code=$backendCode, message=$backendMessage)") {
    val isAuthenticationFailure: Boolean
        get() = backendCode == UNAUTHORIZED || backendCode == TOKEN_EXPIRED

    private companion object {
        const val UNAUTHORIZED = 10001
        const val TOKEN_EXPIRED = 10002
    }
}

class BackendProtocolException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

fun <T : Any> BackendEnvelope<T>.dataOrThrow(httpStatus: Int? = null): T {
    if (code != SUCCESS_CODE) {
        throw BackendApiException(code, message, httpStatus)
    }
    return data ?: throw BackendProtocolException("Successful backend envelope has no data")
}

private const val SUCCESS_CODE = 0
