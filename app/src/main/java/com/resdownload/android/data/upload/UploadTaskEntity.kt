package com.resdownload.android.data.upload

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.resdownload.android.domain.model.UploadStatus
import com.resdownload.android.domain.model.UploadTask

@Entity(
    tableName = "upload_tasks",
    indices = [
        Index(value = ["owner_id", "status"]),
        Index(value = ["owner_id", "remote_path"]),
        Index(value = ["owner_id", "batch_id"]),
        Index(value = ["permission_uri"]),
    ],
)
data class UploadTaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "destination_root") val destinationRoot: String,
    @ColumnInfo(name = "remote_path") val remotePath: String,
    @ColumnInfo(name = "source_uri") val sourceUri: String?,
    @ColumnInfo(name = "permission_uri") val permissionUri: String?,
    @ColumnInfo(name = "is_directory") val isDirectory: Boolean,
    @ColumnInfo(name = "is_tree_upload") val isTreeUpload: Boolean,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long?,
    @ColumnInfo(name = "uploaded_bytes") val uploadedBytes: Long,
    val status: UploadStatus,
    val committing: Boolean,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "queue_order") val queueOrder: Int,
    @ColumnInfo(name = "path_depth") val pathDepth: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun toDomain(): UploadTask = UploadTask(
        id = id,
        ownerId = ownerId,
        batchId = batchId,
        fileName = fileName,
        relativePath = relativePath,
        destinationRoot = destinationRoot,
        remotePath = remotePath,
        sourceUri = sourceUri,
        permissionUri = permissionUri,
        isDirectory = isDirectory,
        isTreeUpload = isTreeUpload,
        mimeType = mimeType,
        totalBytes = totalBytes,
        uploadedBytes = uploadedBytes,
        status = status,
        committing = committing,
        errorMessage = errorMessage,
        queueOrder = queueOrder,
        pathDepth = pathDepth,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(task: UploadTask): UploadTaskEntity = UploadTaskEntity(
            id = task.id,
            ownerId = task.ownerId,
            batchId = task.batchId,
            fileName = task.fileName,
            relativePath = task.relativePath,
            destinationRoot = task.destinationRoot,
            remotePath = task.remotePath,
            sourceUri = task.sourceUri,
            permissionUri = task.permissionUri,
            isDirectory = task.isDirectory,
            isTreeUpload = task.isTreeUpload,
            mimeType = task.mimeType,
            totalBytes = task.totalBytes,
            uploadedBytes = task.uploadedBytes,
            status = task.status,
            committing = task.committing,
            errorMessage = task.errorMessage,
            queueOrder = task.queueOrder,
            pathDepth = task.pathDepth,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}

class UploadStatusConverters {
    @TypeConverter
    fun fromStatus(status: UploadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}
