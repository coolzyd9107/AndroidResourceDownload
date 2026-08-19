package link.mczihan.androidResourceDownload.data.download

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DownloadTaskEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DownloadStatusConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
}
