package com.resdownload.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import com.resdownload.android.BuildConfig
import com.resdownload.android.data.auth.AuthApi
import com.resdownload.android.data.auth.AuthRepository
import com.resdownload.android.data.auth.DefaultAuthRepository
import com.resdownload.android.data.auth.UpdateApi
import com.resdownload.android.data.auth.WebDavCredentialApi
import com.resdownload.android.data.webdav.BackendWebDavCredentialLoader
import com.resdownload.android.data.webdav.CredentialBackedWebDavClient
import com.resdownload.android.data.webdav.InMemoryWebDavCredentialProvider
import com.resdownload.android.domain.webdav.WebDavClient
import com.resdownload.android.domain.webdav.WebDavCredentialProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideBackendHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor { message -> Timber.tag("OkHttp").d(message) }.apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    @BackendRetrofit
    fun provideBackendRetrofit(
        @BackendHttpClient client: OkHttpClient,
    ): Retrofit {
        val baseUrl = BuildConfig.API_BASE_URL.trim().let {
            if (it.endsWith('/')) it else "$it/"
        }.toHttpUrl()
        require(baseUrl.isHttps) { "Backend API base URL must use HTTPS" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
    }

    @Provides
    @Singleton
    @BackendHttpClient
    fun provideQualifiedBackendHttpClient(): OkHttpClient = provideBackendHttpClient()

    @Provides
    @Singleton
    @WebDavHttpClient
    fun provideWebDavHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @PublicHttpClient
    fun providePublicHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(@BackendRetrofit retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideCredentialApi(@BackendRetrofit retrofit: Retrofit): WebDavCredentialApi =
        retrofit.create(WebDavCredentialApi::class.java)

    @Provides
    @Singleton
    fun provideUpdateApi(@BackendRetrofit retrofit: Retrofit): UpdateApi =
        retrofit.create(UpdateApi::class.java)

    @Provides
    @Singleton
    fun provideAuthRepository(
        authApi: AuthApi,
        sessionStore: com.resdownload.android.core.security.SessionStore,
    ): AuthRepository = DefaultAuthRepository(authApi, sessionStore)

    @Provides
    @Singleton
    fun provideWebDavCredentialProvider(
        credentialApi: WebDavCredentialApi,
        authRepository: AuthRepository,
    ): WebDavCredentialProvider = InMemoryWebDavCredentialProvider(
        BackendWebDavCredentialLoader(credentialApi, authRepository),
    )

    @Provides
    @Singleton
    fun provideWebDavClient(
        credentialProvider: WebDavCredentialProvider,
        @WebDavHttpClient client: OkHttpClient,
    ): WebDavClient = CredentialBackedWebDavClient(credentialProvider, client)
}
