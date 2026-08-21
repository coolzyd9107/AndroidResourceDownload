package link.mczihan.androidResourceDownload.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import link.mczihan.androidResourceDownload.data.upload.DocumentFileUploadSelectionScanner
import link.mczihan.androidResourceDownload.data.upload.UploadDatabase
import link.mczihan.androidResourceDownload.data.upload.UploadSelectionScanner
import link.mczihan.androidResourceDownload.data.upload.UploadTaskDao

@Module
@InstallIn(SingletonComponent::class)
object UploadModule {
    @Provides
    @Singleton
    fun provideUploadDatabase(@ApplicationContext context: Context): UploadDatabase =
        Room.databaseBuilder(context, UploadDatabase::class.java, "uploads.db").build()

    @Provides
    fun provideUploadTaskDao(database: UploadDatabase): UploadTaskDao = database.uploadTaskDao()

    @Provides
    @Singleton
    fun provideUploadSelectionScanner(
        scanner: DocumentFileUploadSelectionScanner,
    ): UploadSelectionScanner = scanner
}
