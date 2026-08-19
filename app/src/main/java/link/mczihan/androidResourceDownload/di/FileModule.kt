package link.mczihan.androidResourceDownload.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import link.mczihan.androidResourceDownload.data.file.FileRepository
import link.mczihan.androidResourceDownload.data.file.WebDavFileRepository
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient

@Module
@InstallIn(SingletonComponent::class)
object FileModule {
    @Provides
    @Singleton
    fun provideFileRepository(webDavClient: WebDavClient): FileRepository =
        WebDavFileRepository(webDavClient)
}
