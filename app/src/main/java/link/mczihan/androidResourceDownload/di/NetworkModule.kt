package link.mczihan.androidResourceDownload.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.data.auth.AuthApi
import link.mczihan.androidResourceDownload.data.auth.AuthRepository
import link.mczihan.androidResourceDownload.data.auth.DefaultAuthRepository
import link.mczihan.androidResourceDownload.data.auth.UpdateApi
import link.mczihan.androidResourceDownload.data.auth.WebDavCredentialApi
import link.mczihan.androidResourceDownload.data.webdav.BackendWebDavCredentialLoader
import link.mczihan.androidResourceDownload.data.webdav.CredentialBackedWebDavClient
import link.mczihan.androidResourceDownload.data.webdav.InMemoryWebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideBackendHttpClient(): OkHttpClient = OkHttpClient.Builder()
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
        }
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
    fun provideWebDavHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

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
        sessionStore: link.mczihan.androidResourceDownload.core.security.SessionStore,
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
