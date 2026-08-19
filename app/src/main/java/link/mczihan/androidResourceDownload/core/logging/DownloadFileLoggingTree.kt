package link.mczihan.androidResourceDownload.core.logging

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import timber.log.Timber

class DownloadFileLoggingTree(
    context: Context,
    private val executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "download-file-logger").apply { isDaemon = true }
    },
) : Timber.Tree() {
    private val context = context.applicationContext
    private val formatter = LogLineFormatter()
    private var writer: BufferedWriter? = null

    val fileName: String = "AndroidResourceDownload-${FILE_NAME_TIME.format(ZonedDateTime.now())}.log"

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val entry = FileLogEntry(
            timestampEpochMillis = System.currentTimeMillis(),
            priority = priority,
            tag = tag ?: "App",
            message = message,
            throwable = t,
        )
        executor.execute { write(entry) }
    }

    private fun write(entry: FileLogEntry) {
        try {
            val destination = writer ?: openWriter().also { writer = it }
            destination.write(formatter.format(entry))
            destination.flush()
        } catch (error: Exception) {
            runCatching { writer?.close() }
            writer = null
            Log.e(INTERNAL_TAG, "Unable to write app log to Downloads", error)
        }
    }

    private fun openWriter(): BufferedWriter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        openMediaStoreWriter()
    } else {
        openLegacyWriter()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openMediaStoreWriter(): BufferedWriter {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create log in Downloads")
        return try {
            resolver.openOutputStream(uri, "w")
                ?.bufferedWriter(Charsets.UTF_8)
                ?: throw IOException("Unable to open log in Downloads")
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun openLegacyWriter(): BufferedWriter {
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create Downloads directory")
        }
        return File(directory, fileName).outputStream().bufferedWriter(Charsets.UTF_8)
    }

    private companion object {
        const val INTERNAL_TAG = "DownloadFileLogging"
        val FILE_NAME_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)
    }
}

internal data class FileLogEntry(
    val timestampEpochMillis: Long,
    val priority: Int,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
)

internal class LogLineFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun format(entry: FileLogEntry): String = buildString {
        append(LOG_TIME.format(Instant.ofEpochMilli(entry.timestampEpochMillis).atZone(zoneId)))
        append(' ')
        append(priorityName(entry.priority))
        append('/')
        append(entry.tag)
        append(": ")
        append(entry.message)
        append('\n')
        if (entry.throwable != null) {
            val stackTrace = StringWriter().also { output ->
                entry.throwable.printStackTrace(PrintWriter(output))
            }.toString()
            append(stackTrace)
            if (!stackTrace.endsWith('\n')) append('\n')
        }
    }

    private fun priorityName(priority: Int): String = when (priority) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG -> "DEBUG"
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        Log.ASSERT -> "ASSERT"
        else -> priority.toString()
    }

    private companion object {
        val LOG_TIME: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX", Locale.US)
    }
}
