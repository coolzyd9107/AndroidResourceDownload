package com.resdownload.android.data.download

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask

@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["owner_id", "status"]),
        Index(value = ["owner_id", "remote_path"]),
    ],
)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "remote_path") val remotePath: String,
    @ColumnInfo(name = "storage_name") val storageName: String,
    @ColumnInfo(name = "relative_path", defaultValue = "") val relativePath: String = "",
    @ColumnInfo(name = "public_uri") val publicUri: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long?,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long,
    val status: DownloadStatus,
    @ColumnInfo(name = "support_range") val supportRange: Boolean,
    val etag: String?,
    @ColumnInfo(name = "last_modified") val lastModified: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun toDomain(): DownloadTask = DownloadTask(
        id = id,
        ownerId = ownerId,
        fileName = fileName,
        remotePath = remotePath,
        storageName = storageName,
        relativePath = relativePath,
        publicUri = publicUri,
        mimeType = mimeType,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status,
        supportRange = supportRange,
        etag = etag,
        lastModified = lastModified,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(task: DownloadTask): DownloadTaskEntity = DownloadTaskEntity(
            id = task.id,
            ownerId = task.ownerId,
            fileName = task.fileName,
            remotePath = task.remotePath,
            storageName = task.storageName,
            relativePath = task.relativePath,
            publicUri = task.publicUri,
            mimeType = task.mimeType,
            totalBytes = task.totalBytes,
            downloadedBytes = task.downloadedBytes,
            status = task.status,
            supportRange = task.supportRange,
            etag = task.etag,
            lastModified = task.lastModified,
            errorMessage = task.errorMessage,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}

class DownloadStatusConverters {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
}
