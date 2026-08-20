package link.mczihan.androidResourceDownload.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebDavHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendRetrofit
