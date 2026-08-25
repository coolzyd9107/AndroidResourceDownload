package com.resdownload.android.data.upload

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [UploadTaskEntity::class, UploadPermissionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(UploadStatusConverters::class)
abstract class UploadDatabase : RoomDatabase() {
    abstract fun uploadTaskDao(): UploadTaskDao
}
