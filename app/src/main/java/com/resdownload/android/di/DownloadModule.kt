package com.resdownload.android.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import com.resdownload.android.data.download.DownloadDatabase
import com.resdownload.android.data.download.DownloadFileStore
import com.resdownload.android.data.download.DownloadTaskDao

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideDownloadDatabase(@ApplicationContext context: Context): DownloadDatabase =
        Room.databaseBuilder(context, DownloadDatabase::class.java, "downloads.db")
            .addMigrations(DownloadDatabase.MIGRATION_1_2, DownloadDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideDownloadTaskDao(database: DownloadDatabase): DownloadTaskDao =
        database.downloadTaskDao()

    @Provides
    @Singleton
    fun provideDownloadFileStore(@ApplicationContext context: Context): DownloadFileStore =
        DownloadFileStore(File(context.filesDir, "downloads"))
}
