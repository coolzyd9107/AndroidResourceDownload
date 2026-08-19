package link.mczihan.androidResourceDownload.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import link.mczihan.androidResourceDownload.data.download.DownloadDatabase
import link.mczihan.androidResourceDownload.data.download.DownloadFileStore
import link.mczihan.androidResourceDownload.data.download.DownloadTaskDao

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideDownloadDatabase(@ApplicationContext context: Context): DownloadDatabase =
        Room.databaseBuilder(context, DownloadDatabase::class.java, "downloads.db").build()

    @Provides
    fun provideDownloadTaskDao(database: DownloadDatabase): DownloadTaskDao =
        database.downloadTaskDao()

    @Provides
    @Singleton
    fun provideDownloadFileStore(@ApplicationContext context: Context): DownloadFileStore =
        DownloadFileStore(File(context.filesDir, "downloads"))
}
