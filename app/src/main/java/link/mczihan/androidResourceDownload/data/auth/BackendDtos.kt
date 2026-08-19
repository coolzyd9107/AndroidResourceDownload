package link.mczihan.androidResourceDownload.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class WebDavCredentialRequestDto(
    val clientPublicKey: String = "",
    val keyType: String = "",
)

@Serializable
data class WebDavCredentialDto(
    val baseUrl: String,
    val username: String,
    val password: String,
    val rootPath: String,
    val permission: String,
    val expiresAt: Long,
) {
    override fun toString(): String =
        "WebDavCredentialDto(baseUrl=$baseUrl, username=<redacted>, password=<redacted>, " +
            "rootPath=$rootPath, permission=$permission, expiresAt=$expiresAt)"
}

@Serializable
data class UpdateInfoDto(
    val versionCode: Long,
    val versionName: String,
    val forceUpdate: Boolean,
    val changelog: String,
    val encryptedUrl: String,
    val expiresAt: Long,
    val signature: String,
) {
    override fun toString(): String =
        "UpdateInfoDto(versionCode=$versionCode, versionName=$versionName, " +
            "forceUpdate=$forceUpdate, changelog=<redacted>, encryptedUrl=<redacted>, " +
            "expiresAt=$expiresAt, signature=<redacted>)"
}

@Serializable
data class UpdateResolveRequestDto(
    val encryptedUrl: String,
) {
    override fun toString(): String = "UpdateResolveRequestDto(encryptedUrl=<redacted>)"
}

@Serializable
data class UpdateResolveResponseDto(
    val url: String,
    val expiresIn: Int,
)
