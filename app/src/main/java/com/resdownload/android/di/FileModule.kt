package com.resdownload.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.resdownload.android.data.file.FileRepository
import com.resdownload.android.data.file.ContentResolverUploadSource
import com.resdownload.android.data.file.UploadSourceResolver
import com.resdownload.android.data.file.WebDavFileRepository
import com.resdownload.android.domain.webdav.WebDavClient

@Module
@InstallIn(SingletonComponent::class)
object FileModule {
    @Provides
    @Singleton
    fun provideFileRepository(webDavClient: WebDavClient): FileRepository =
        WebDavFileRepository(webDavClient)

    @Provides
    @Singleton
    fun provideUploadSourceResolver(source: ContentResolverUploadSource): UploadSourceResolver = source
}
