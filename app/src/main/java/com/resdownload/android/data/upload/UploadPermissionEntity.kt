package com.resdownload.android.data.upload

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_permissions")
data class UploadPermissionEntity(
    @PrimaryKey val uri: String,
    val createdAt: Long,
)
