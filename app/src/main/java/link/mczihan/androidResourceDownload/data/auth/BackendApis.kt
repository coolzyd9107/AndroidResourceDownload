package link.mczihan.androidResourceDownload.data.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @POST("/api/v1/auth/github/complete")
    suspend fun loginWithGitHub(
        @Body request: GitHubCompleteRequestDto,
    ): Response<BackendEnvelope<LoginResponseDto>>

    @POST("/api/v1/auth/email/code")
    suspend fun requestEmailCode(
        @Body request: EmailCodeRequestDto,
    ): Response<BackendEnvelope<EmailCodeResponseDto>>

    @POST("/api/v1/auth/email/login")
    suspend fun loginWithEmail(
        @Body request: EmailLoginRequestDto,
    ): Response<BackendEnvelope<LoginResponseDto>>

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(
        @Body request: RefreshTokenRequestDto,
    ): Response<BackendEnvelope<RefreshResponseDto>>

    @POST("/api/v1/auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String,
        @Body request: RefreshTokenRequestDto,
    ): Response<BackendEnvelope<StatusResponseDto>>

    @GET("/api/v1/auth/me")
    suspend fun me(
        @Header("Authorization") authorization: String,
    ): Response<BackendEnvelope<BackendUserDto>>
}

interface WebDavCredentialApi {
    @POST("/api/v1/webdav/credential")
    suspend fun issueCredential(
        @Header("Authorization") authorization: String,
        @Body request: WebDavCredentialRequestDto,
    ): Response<BackendEnvelope<WebDavCredentialDto>>
}

interface UpdateApi {
    @GET("/api/v1/update/info")
    suspend fun info(
        @Header("Authorization") authorization: String,
    ): Response<BackendEnvelope<UpdateInfoDto>>

    @POST("/api/v1/update/resolve")
    suspend fun resolve(
        @Header("Authorization") authorization: String,
        @Body request: UpdateResolveRequestDto,
    ): Response<BackendEnvelope<UpdateResolveResponseDto>>
}
