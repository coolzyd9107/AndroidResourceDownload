package com.resdownload.android.domain.model

data class UploadTask(
    val id: String,
    val ownerId: String,
    val batchId: String,
    val fileName: String,
    val relativePath: String,
    val destinationRoot: String,
    val remotePath: String,
    val sourceUri: String?,
    val permissionUri: String?,
    val isDirectory: Boolean,
    val isTreeUpload: Boolean,
    val mimeType: String?,
    val totalBytes: Long?,
    val uploadedBytes: Long = 0L,
    val status: UploadStatus = UploadStatus.PENDING,
    val committing: Boolean = false,
    val errorMessage: String? = null,
    val queueOrder: Int,
    val pathDepth: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class UploadStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}
