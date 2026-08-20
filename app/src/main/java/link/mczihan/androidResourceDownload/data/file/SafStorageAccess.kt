package link.mczihan.androidResourceDownload.data.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Wraps the Android Storage Access Framework (SAF) — the system file manager
 * API — so the app can let the user pick a save location and write files there
 * without requesting broad storage permissions.
 *
 * Typical flow:
 * 1. Call [createOpenDocumentTreeIntent] and launch it from an Activity.
 * 2. In the result callback, call [persistTreePermission] with the returned URI.
 * 3. Use [writeFileToTree] / [createFileInTree] to save downloads into the
 *    user-selected directory.
 */
@Singleton
class SafStorageAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Builds an [Intent] that opens the system file picker in "select directory"
     * mode. Launch with `ActivityResultContracts.StartActivityForResult` or
     * `rememberLauncherForActivityResult`.
     *
     * @param initialUri optional URI to pre-select; pass null to let the system
     *   decide the starting location.
     */
    fun createOpenDocumentTreeIntent(initialUri: Uri? = null): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (initialUri != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
        }
        return intent
    }

    /**
     * Persists the read/write permission for a tree URI returned by the system
     * file picker so the app can access it across reboots.
     *
     * @return true if the permission was persisted successfully.
     */
    fun persistTreePermission(treeUri: Uri): Boolean {
        return runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            Timber.d("Persisted SAF tree permission: %s", treeUri)
            true
        }.getOrElse { error ->
            Timber.w(error, "Failed to persist SAF tree permission: %s", treeUri)
            false
        }
    }

    /** Releases a previously persisted tree permission. */
    fun releaseTreePermission(treeUri: Uri) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(treeUri, flags)
        }
    }

    /** Returns all persisted tree URIs the app currently holds permission for. */
    fun persistedTreeUris(): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }
            .map { it.uri }

    /**
     * Writes [source] into the directory identified by [treeUri], creating a
     * file named [fileName] (or replacing an existing one with the same name).
     *
     * @return the content:// URI of the written file, or null on failure.
     */
    suspend fun writeFileToTree(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        source: File,
    ): Uri? = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext null.also { Timber.w("SAF tree is null: %s", treeUri) }
        if (!tree.canWrite()) return@withContext null.also {
            Timber.w("SAF tree is not writable: %s", treeUri)
        }
        val existing = tree.findFile(fileName)
        val target = existing ?: tree.createFile(mimeType, fileName)
        ?: return@withContext null.also { Timber.w("SAF createFile returned null") }
        runCatching {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                copy(source, output)
                output.flush()
            }
        }.onFailure { error ->
            Timber.w(error, "SAF write failed for %s", fileName)
            return@withContext null
        }
        target.uri
    }

    /**
     * Creates an empty file in [treeUri] and returns an [OutputStream] for
     * streaming data into it. The caller is responsible for closing the stream.
     */
    suspend fun createFileInTree(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
    ): OutputStream? = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        if (!tree.canWrite()) return@withContext null
        val existing = tree.findFile(fileName)
        val target = existing ?: tree.createFile(mimeType, fileName) ?: return@withContext null
        runCatching { context.contentResolver.openOutputStream(target.uri, "w") }.getOrNull()
    }

    /** Lists immediate children of [treeUri]. */
    suspend fun listTreeFiles(treeUri: Uri): List<SafDocument> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        tree.listFiles().map { file ->
            SafDocument(
                uri = file.uri,
                name = file.name,
                isDirectory = file.isDirectory,
                size = file.length().takeIf { it > 0L },
                mimeType = file.type,
                lastModified = file.lastModified().takeIf { it > 0L },
            )
        }
    }

    /** Deletes a document by its content:// URI. Returns true on success. */
    suspend fun deleteDocument(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            .getOrDefault(false)
    }

    private fun copy(source: File, output: OutputStream) {
        FileInputStream(source).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                output.write(buffer, 0, read)
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

/** A lightweight representation of a document inside a SAF tree. */
data class SafDocument(
    val uri: Uri,
    val name: String?,
    val isDirectory: Boolean,
    val size: Long?,
    val mimeType: String?,
    val lastModified: Long?,
)
