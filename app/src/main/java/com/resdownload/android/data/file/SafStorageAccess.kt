package com.resdownload.android.data.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.resdownload.android.core.common.collisionFileName
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
    private val allocationMutex = Mutex()

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
     * file named [fileName], adding a numeric suffix when the name already exists.
     *
     * @return the content:// URI of the written file, or null on failure.
     */
    suspend fun writeFileToTree(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        source: File,
    ): Uri? {
        val createdDocument = AtomicReference<DocumentFile?>()
        return try {
            val result = withContext(Dispatchers.IO) {
                allocationMutex.withLock {
                    val tree = DocumentFile.fromTreeUri(context, treeUri)
                        ?: return@withLock null.also { Timber.w("SAF tree is null: %s", treeUri) }
                    if (!tree.canWrite()) return@withLock null.also {
                        Timber.w("SAF tree is not writable: %s", treeUri)
                    }
                    val target = createAvailableDocument(tree, fileName, mimeType)
                        ?: return@withLock null.also { Timber.w("SAF createFile returned null") }
                    createdDocument.set(target)
                    try {
                        val output = context.contentResolver.openOutputStream(target.uri, "w")
                            ?: throw IOException("SAF openOutputStream returned null")
                        output.use {
                            copy(source, output)
                            output.flush()
                        }
                        target.uri
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        deleteCreatedDocument(target)
                        createdDocument.set(null)
                        Timber.w(error, "SAF write failed for %s", fileName)
                        null
                    }
                }
            }
            createdDocument.set(null)
            result
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                createdDocument.getAndSet(null)?.let(::deleteCreatedDocument)
            }
            throw error
        }
    }

    /**
     * Creates an empty file in [treeUri] and returns an [OutputStream] for
     * streaming data into it. The caller is responsible for closing the stream.
     */
    suspend fun createFileInTree(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
    ): OutputStream? {
        val createdDocument = AtomicReference<DocumentFile?>()
        val createdStream = AtomicReference<OutputStream?>()
        return try {
            val result = withContext(Dispatchers.IO) {
                allocationMutex.withLock {
                    val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withLock null
                    if (!tree.canWrite()) return@withLock null
                    val target = createAvailableDocument(tree, fileName, mimeType) ?: return@withLock null
                    createdDocument.set(target)
                    try {
                        context.contentResolver.openOutputStream(target.uri, "w").also { output ->
                            if (output == null) {
                                deleteCreatedDocument(target)
                                createdDocument.set(null)
                            } else {
                                createdStream.set(output)
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        deleteCreatedDocument(target)
                        createdDocument.set(null)
                        Timber.w(error, "SAF stream creation failed for %s", fileName)
                        null
                    }
                }
            }
            createdDocument.set(null)
            createdStream.set(null)
            result
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { createdStream.getAndSet(null)?.close() }
                createdDocument.getAndSet(null)?.let(::deleteCreatedDocument)
            }
            throw error
        }
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

    private suspend fun copy(source: File, output: OutputStream) {
        FileInputStream(source).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                output.write(buffer, 0, read)
            }
        }
    }

    private fun createAvailableDocument(
        tree: DocumentFile,
        requestedName: String,
        mimeType: String,
    ): DocumentFile? {
        val existingNames = tree.listFiles().mapNotNullTo(mutableSetOf(), DocumentFile::getName)
        for (index in 0..MAX_COLLISION_INDEX) {
            val candidate = collisionFileName(requestedName, index)
            if (candidate in existingNames || tree.findFile(candidate) != null) continue
            val created = tree.createFile(mimeType, candidate) ?: continue
            val actualName = created.name
            if (actualName != null && actualName !in existingNames) return created
            if (actualName in existingNames) {
                Timber.w("SAF provider returned an existing document for %s", candidate)
                return null
            }
            if (!deleteCreatedDocument(created)) return null
        }
        return null
    }

    private fun deleteCreatedDocument(document: DocumentFile): Boolean = document.delete().also {
        if (!it) Timber.w("Unable to clean SAF document: %s", document.uri)
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_COLLISION_INDEX = 10_000
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
