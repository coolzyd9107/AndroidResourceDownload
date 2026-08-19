package link.mczihan.androidResourceDownload.data.webdav

import link.mczihan.androidResourceDownload.data.auth.AuthRepository
import link.mczihan.androidResourceDownload.data.auth.WebDavCredentialApi
import link.mczihan.androidResourceDownload.data.auth.WebDavCredentialRequestDto
import link.mczihan.androidResourceDownload.data.auth.dataOrThrow
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredential
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialLoader
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPermission
import okhttp3.HttpUrl.Companion.toHttpUrl

class BackendWebDavCredentialLoader(
    private val api: WebDavCredentialApi,
    private val authRepository: AuthRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : WebDavCredentialLoader {
    override suspend fun load(): WebDavCredential {
        var session = authRepository.restoreSession()
            ?: throw WebDavException.CredentialUnavailable()
        val dto = try {
            var response = api.issueCredential(
                authorization = "Bearer ${session.accessToken}",
                request = WebDavCredentialRequestDto(),
            )
            if (response.code() == 401) {
                session = authRepository.refreshSession()
                    ?: throw WebDavException.CredentialUnavailable()
                response = api.issueCredential(
                    authorization = "Bearer ${session.accessToken}",
                    request = WebDavCredentialRequestDto(),
                )
            }
            if (!response.isSuccessful) throw WebDavException.CredentialUnavailable()
            response.body()?.dataOrThrow(response.code())
                ?: throw WebDavException.CredentialUnavailable()
        } catch (error: WebDavException) {
            throw error
        } catch (error: Exception) {
            throw WebDavException.CredentialUnavailable(error)
        }

        val permission = when (dto.permission.uppercase()) {
            "READ_ONLY" -> WebDavPermission.READ_ONLY
            "READ_WRITE" -> WebDavPermission.READ_WRITE
            else -> throw WebDavException.CredentialUnavailable()
        }
        val expiresAtMillis = try {
            Math.multiplyExact(dto.expiresAt, 1_000L)
        } catch (error: ArithmeticException) {
            throw WebDavException.CredentialUnavailable(error)
        }
        if (expiresAtMillis <= nowEpochMillis()) {
            throw WebDavException.CredentialUnavailable()
        }
        val rootPath = try {
            WebDavPath.parseDecoded(dto.rootPath)
        } catch (error: RuntimeException) {
            throw WebDavException.CredentialUnavailable(error)
        }
        try {
            dto.baseUrl.toHttpUrl().also { url ->
                require(url.isHttps && url.username.isEmpty() && url.password.isEmpty())
                require(url.query == null && url.fragment == null)
            }
        } catch (error: RuntimeException) {
            throw WebDavException.CredentialUnavailable(error)
        }
        return WebDavCredential(
            username = dto.username,
            password = dto.password,
            permission = permission,
            baseUrl = dto.baseUrl,
            rootPath = rootPath,
            expiresAtEpochMillis = expiresAtMillis,
        )
    }
}
