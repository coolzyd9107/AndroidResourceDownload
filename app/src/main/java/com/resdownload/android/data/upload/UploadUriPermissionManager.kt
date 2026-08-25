package com.resdownload.android.data.upload

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class UploadUriPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun persistRead(uri: Uri): Boolean {
        val resolver = context.contentResolver
        if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return true
        return runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrElse { error ->
            Timber.w(error, "Unable to persist upload source permission: %s", uri)
            false
        }
    }

    fun releaseRead(uri: Uri): Boolean {
        val persisted = context.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == uri && it.isReadPermission }
            ?: return true
        return runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        }.onFailure { error ->
            Timber.w(error, "Unable to release upload source permission: %s", persisted.uri)
        }.getOrDefault(false)
    }
}
