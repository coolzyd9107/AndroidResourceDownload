package link.mczihan.androidResourceDownload.data.download

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.core.common.collisionFileName
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

enum class PublicDownloadOperation {
    CREATE,
    WRITE,
    PUBLISH,
}

enum class PublicDownloadPresence {
    PRESENT,
    PENDING,
    MISSING,
    UNKNOWN,
}

class PublicDownloadException(
    val operation: PublicDownloadOperation,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

@Singleton
class PublicDownloadStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver = context.contentResolver
    private val createMutex = Mutex()

    suspend fun create(task: DownloadTask, mimeType: String?): String {
        val createdUri = AtomicReference<Uri?>()
        return try {
            withContext(Dispatchers.IO) {
                val uri = createMutex.withLock {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        createWithMediaStore(task, mimeType)
                    } else {
                        createLegacyDownload(task)
                    }
                }
                createdUri.set(uri)
                uri.toString()
            }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                createdUri.getAndSet(null)?.let(::delete)
            }
            throw error
        }
    }

    suspend fun write(
        task: DownloadTask,
        publicUri: String,
        source: File,
        mimeType: String?,
        onPublished: (String) -> Unit = {},
    ): String =
        withContext(Dispatchers.IO) {
            if (!source.isFile) {
                throw PublicDownloadException(
                    PublicDownloadOperation.WRITE,
                    "Downloaded file is missing",
                )
            }
            val uri = parsePublicUri(publicUri) ?: throw PublicDownloadException(
                PublicDownloadOperation.WRITE,
                "Invalid public download URI",
            )
            val destination = when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        throw IOException("MediaStore Downloads is unavailable")
                    }
                    writeWithMediaStore(uri, source)
                    uri
                }
                ContentResolver.SCHEME_FILE -> writeLegacyDownload(task, uri, source, mimeType)
                else -> throw IOException("Unsupported public download URI")
            }
            destination.toString().also(onPublished)
        }

    suspend fun exists(publicUri: String?): Boolean = withContext(Dispatchers.IO) {
        parsePublicUri(publicUri)?.let(::exists) == true
    }

    suspend fun presence(publicUri: String?): PublicDownloadPresence = withContext(Dispatchers.IO) {
        if (publicUri == null) return@withContext PublicDownloadPresence.MISSING
        val uri = parsePublicUri(publicUri) ?: return@withContext PublicDownloadPresence.UNKNOWN
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> contentPresence(uri)
            ContentResolver.SCHEME_FILE -> legacyPresence(uri)
            else -> PublicDownloadPresence.MISSING
        }
    }

    suspend fun delete(publicUri: String?): Boolean = withContext(Dispatchers.IO) {
        if (publicUri == null) return@withContext true
        parsePublicUri(publicUri)?.let(::delete) == true
    }

    fun uriForViewing(publicUri: String?): Uri? {
        val uri = parsePublicUri(publicUri) ?: return null
        if (!exists(uri)) return null
        return if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = legacyFile(uri) ?: return null
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }.getOrNull()
        } else {
            uri
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createWithMediaStore(
        task: DownloadTask,
        mimeType: String?,
    ): Uri {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativeDir = if (task.relativePath.isNotBlank()) {
            "${Environment.DIRECTORY_DOWNLOADS}/${task.relativePath}"
        } else {
            Environment.DIRECTORY_DOWNLOADS
        }
        val existingNames = mediaStoreNames(collection, relativeDir).toMutableSet()
        for (index in 0..MAX_COLLISION_INDEX) {
            val displayName = collisionFileName(task.storageName, index)
            if (displayName in existingNames) continue
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: DEFAULT_MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = try {
                resolver.insert(collection, values) ?: throw PublicDownloadException(
                    PublicDownloadOperation.CREATE,
                    "MediaStore did not create a public download",
                )
            } catch (error: PublicDownloadException) {
                throw error
            } catch (error: Exception) {
                throw PublicDownloadException(
                    PublicDownloadOperation.CREATE,
                    "Unable to create a public download",
                    error,
                )
            }
            val actualName = try {
                mediaStoreDisplayName(uri)
            } catch (error: Exception) {
                delete(uri)
                throw PublicDownloadException(
                    PublicDownloadOperation.CREATE,
                    "Unable to verify the public download name",
                    error,
                )
            }
            if (actualName == displayName) return uri
            if (!delete(uri)) {
                throw PublicDownloadException(
                    PublicDownloadOperation.CREATE,
                    "Unable to release a conflicting public download name",
                )
            }
            existingNames += displayName
            actualName?.let(existingNames::add)
        }
        throw PublicDownloadException(
            PublicDownloadOperation.CREATE,
            "Unable to allocate a public download name",
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreNames(collection: Uri, relativeDir: String): Set<String> {
        val cursor = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf("$relativeDir/"),
            null,
        ) ?: throw IOException("MediaStore did not enumerate public downloads")
        return cursor.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            buildSet { while (it.moveToNext()) add(it.getString(nameColumn)) }
        }
    }

    private fun mediaStoreDisplayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        cursor.takeIf { it.moveToFirst() }?.getString(0)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun writeWithMediaStore(uri: Uri, source: File) {
        val output = try {
            resolver.openOutputStream(uri, "w")
                ?: throw PublicDownloadException(
                    PublicDownloadOperation.WRITE,
                    "MediaStore did not open the public download",
                )
        } catch (error: PublicDownloadException) {
            throw error
        } catch (error: Exception) {
            throw PublicDownloadException(
                PublicDownloadOperation.WRITE,
                "Unable to open the public download",
                error,
            )
        }
        try {
            output.use { copy(source, it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw PublicDownloadException(
                PublicDownloadOperation.WRITE,
                "Unable to write the public download",
                error,
            )
        }
        val published = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        val updated = try {
            resolver.update(uri, published, null, null)
        } catch (error: Exception) {
            throw PublicDownloadException(
                PublicDownloadOperation.PUBLISH,
                "Unable to publish the downloaded file",
                error,
            )
        }
        if (updated <= 0) {
            throw PublicDownloadException(
                PublicDownloadOperation.PUBLISH,
                "MediaStore did not publish the downloaded file",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun createLegacyDownload(task: DownloadTask): Uri {
        val baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .absoluteFile
        val directory = if (task.relativePath.isNotBlank()) {
            File(baseDirectory, task.relativePath)
        } else {
            baseDirectory
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create the public Download directory")
        }
        if (!directory.isDirectory) throw IOException("Public Download path is not a directory")
        require(TASK_ID_PATTERN.matches(task.id)) { "Invalid download task id" }
        val stage = File(directory, ".ard-${task.id}-${UUID.randomUUID()}.part").absoluteFile
        if (stage.parentFile != directory || !stage.createNewFile()) {
            throw IOException("Unable to reserve a public download stage")
        }
        return Uri.fromFile(stage)
    }

    private suspend fun writeLegacyDownload(
        task: DownloadTask,
        uri: Uri,
        source: File,
        mimeType: String?,
    ): Uri {
        val stage = legacyFile(uri) ?: throw IOException("Invalid public download path")
        val expectedPrefix = ".ard-${task.id}-"
        if (!stage.name.startsWith(expectedPrefix) || !stage.name.endsWith(".part") || !stage.isFile) {
            throw IOException("Reserved public download stage is invalid")
        }
        FileOutputStream(stage, false).use { output ->
            copy(source, output)
            output.fd.sync()
        }
        val destination = moveToAvailableFile(stage, task.storageName)
        runCatching {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destination.absolutePath),
                arrayOf(mimeType ?: DEFAULT_MIME_TYPE),
                null,
            )
        }
        return Uri.fromFile(destination)
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
            output.flush()
        }
    }

    private fun exists(uri: Uri): Boolean = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> runCatching {
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_PENDING)
            } else {
                arrayOf(MediaStore.MediaColumns._ID)
            }
            resolver.query(
                uri,
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst() &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || cursor.getInt(1) == 0)
            } == true
        }.getOrDefault(false)
        ContentResolver.SCHEME_FILE -> legacyFile(uri)?.isFile == true
        else -> false
    }

    private fun contentPresence(uri: Uri): PublicDownloadPresence = try {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_PENDING)
        } else {
            arrayOf(MediaStore.MediaColumns._ID)
        }
        val cursor = resolver.query(
            uri,
            projection,
            null,
            null,
            null,
        ) ?: return PublicDownloadPresence.UNKNOWN
        cursor.use {
            when {
                !it.moveToFirst() -> PublicDownloadPresence.MISSING
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && it.getInt(1) != 0 ->
                    PublicDownloadPresence.PENDING
                else -> PublicDownloadPresence.PRESENT
            }
        }
    } catch (_: Exception) {
        PublicDownloadPresence.UNKNOWN
    }

    @Suppress("DEPRECATION")
    private fun legacyPresence(uri: Uri): PublicDownloadPresence {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return PublicDownloadPresence.UNKNOWN
        }
        val file = legacyFile(uri) ?: return PublicDownloadPresence.MISSING
        return runCatching {
            if (file.isFile) PublicDownloadPresence.PRESENT else PublicDownloadPresence.MISSING
        }.getOrDefault(PublicDownloadPresence.UNKNOWN)
    }

    private fun delete(uri: Uri): Boolean = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> runCatching {
            resolver.delete(uri, null, null) > 0 || contentRowExists(uri) == false
        }.getOrDefault(false)
        ContentResolver.SCHEME_FILE -> runCatching {
            val file = legacyFile(uri) ?: return@runCatching false
            if (Files.notExists(file.toPath())) {
                true
            } else if (file.delete()) {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                true
            } else {
                false
            }
        }.getOrDefault(false)
        else -> false
    }

    private fun contentRowExists(uri: Uri): Boolean? = try {
        val cursor = resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns._ID),
            null,
            null,
            null,
        ) ?: return null
        cursor.use { it.moveToFirst() }
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun legacyFile(uri: Uri): File? {
        val path = uri.path ?: return null
        val directory = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile
        }.getOrNull() ?: return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        // 允许文件在 Downloads 目录或其子目录中
        return file.takeIf { 
            var parent = it.parentFile
            while (parent != null) {
                if (parent == directory) return@takeIf true
                parent = parent.parentFile
            }
            false
        }
    }

    private fun parsePublicUri(value: String?): Uri? {
        if (value.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        return when {
            uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY -> uri
            uri.scheme == ContentResolver.SCHEME_FILE && legacyFile(uri) != null -> uri
            else -> null
        }
    }

    private fun moveToAvailableFile(stage: File, requestedName: String): File {
        val directory = stage.parentFile ?: throw IOException("Public download stage has no parent")
        for (index in 0..MAX_COLLISION_INDEX) {
            val name = collisionFileName(requestedName, index)
            val candidate = File(directory, name).absoluteFile
            if (candidate.parentFile != directory) {
                throw IOException("Public download path escaped its root")
            }
            try {
                Files.move(stage.toPath(), candidate.toPath())
                return candidate
            } catch (_: FileAlreadyExistsException) {
                continue
            }
        }
        throw IOException("Unable to allocate a public download name")
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_COLLISION_INDEX = 10_000
        val TASK_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
