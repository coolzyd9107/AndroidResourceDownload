package link.mczihan.androidResourceDownload.data.download

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadTaskEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DownloadStatusConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE download_tasks ADD COLUMN public_uri TEXT")
            }
        }
    }
}
