package link.mczihan.androidResourceDownload.data.auth

import kotlinx.serialization.Serializable
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User

@Serializable
data class GitHubCompleteRequestDto(
    val code: String,
    val codeVerifier: String,
    val deviceId: String = "",
)

@Serializable
data class EmailCodeRequestDto(
    val email: String,
)

@Serializable
data class EmailLoginRequestDto(
    val email: String,
    val code: String,
    val deviceId: String = "",
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
) {
    override fun toString(): String = "RefreshTokenRequestDto(refreshToken=<redacted>)"
}

@Serializable
data class BackendUserDto(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val role: String,
    val avatarUrl: String? = null,
    val loginType: String,
)

@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val user: BackendUserDto,
) {
    override fun toString(): String =
        "LoginResponseDto(accessToken=<redacted>, refreshToken=<redacted>, " +
            "expiresIn=$expiresIn, user=<redacted>)"
}

@Serializable
data class RefreshResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
) {
    override fun toString(): String =
        "RefreshResponseDto(accessToken=<redacted>, refreshToken=<redacted>, expiresIn=$expiresIn)"
}

@Serializable
data class EmailCodeResponseDto(
    val expiresIn: Int,
)

@Serializable
data class StatusResponseDto(
    val status: String,
)

fun BackendUserDto.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    role = role.toRole(),
    loginType = loginType.toLoginType(),
    avatarUrl = avatarUrl,
)

private fun String.toRole(): Role = when (this) {
    "USER" -> Role.USER
    "ADMIN" -> Role.ADMIN
    else -> throw BackendProtocolException("Unknown user role: $this")
}

private fun String.toLoginType(): LoginType = when (this) {
    "GITHUB" -> LoginType.GITHUB
    "EMAIL" -> LoginType.EMAIL
    else -> throw BackendProtocolException("Unknown login type: $this")
}
