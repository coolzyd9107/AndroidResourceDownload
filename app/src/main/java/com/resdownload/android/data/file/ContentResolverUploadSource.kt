package com.resdownload.android.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.resdownload.android.domain.webdav.WebDavUpload

class UploadDocument(
    val displayName: String,
    val contentLength: Long?,
    val contentType: String?,
    private val openStream: () -> InputStream,
) {
    fun toWebDavUpload(onProgress: (Long) -> Unit = {}): WebDavUpload = WebDavUpload(
        contentLength = contentLength,
        contentType = contentType,
        openStream = openStream,
        onProgress = onProgress,
    )
}

fun interface UploadSourceResolver {
    suspend fun resolve(uri: Uri): UploadDocument
}

@Singleton
class ContentResolverUploadSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : UploadSourceResolver {
    override suspend fun resolve(uri: Uri): UploadDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName: String? = null
        var contentLength: Long? = null
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0)?.trim()?.takeIf(String::isNotEmpty)
                contentLength = if (cursor.isNull(1)) {
                    null
                } else {
                    cursor.getLong(1).takeIf { it >= 0L }
                }
            }
        }
        val fallbackName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "upload"
        resolver.openInputStream(uri)?.close()
            ?: throw IOException("Unable to open the selected upload file")
        UploadDocument(
            displayName = displayName ?: fallbackName,
            contentLength = contentLength,
            contentType = resolver.getType(uri),
            openStream = {
                resolver.openInputStream(uri)
                    ?: throw IOException("Unable to reopen the selected upload file")
            },
        )
    }
}
