package link.mczihan.androidResourceDownload.data.download

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

class DownloadFileStore(rootDirectory: File) {
    private val root = rootDirectory.absoluteFile

    fun partialFile(task: DownloadTask): File = taskFile(task, "${task.storageName}.part")

    fun finalFile(task: DownloadTask): File = taskFile(task, task.storageName)

    fun ensureTaskDirectory(task: DownloadTask) {
        val directory = taskDirectory(task.id)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create the download directory")
        }
        if (!directory.isDirectory) throw IOException("Download path is not a directory")
    }

    fun truncatePartial(task: DownloadTask) {
        ensureTaskDirectory(task)
        partialFile(task).outputStream().use { output -> output.fd.sync() }
    }

    fun finalize(task: DownloadTask) {
        move(partialFile(task), finalFile(task), "Download temporary file is missing")
    }

    fun restoreFinalAsPartial(task: DownloadTask) {
        move(finalFile(task), partialFile(task), "Downloaded file is missing")
    }

    private fun move(source: File, destination: File, missingMessage: String) {
        if (!source.isFile) throw IOException(missingMessage)
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun hasFinalFile(task: DownloadTask): Boolean = finalFile(task).isFile

    fun deletePartial(task: DownloadTask) {
        partialFile(task).delete()
        deleteEmptyTaskDirectory(task)
    }

    fun deleteAll(task: DownloadTask) {
        partialFile(task).delete()
        finalFile(task).delete()
        deleteEmptyTaskDirectory(task)
    }

    private fun deleteEmptyTaskDirectory(task: DownloadTask) {
        taskDirectory(task.id).takeIf { it.isDirectory && it.list()?.isEmpty() == true }?.delete()
    }

    private fun taskFile(task: DownloadTask, name: String): File {
        validateStorageName(name)
        val directory = taskDirectory(task.id)
        return File(directory, name).absoluteFile.also { file ->
            if (file.parentFile != directory) throw IOException("Download path escaped its task directory")
        }
    }

    private fun taskDirectory(taskId: String): File {
        require(TASK_ID_PATTERN.matches(taskId)) { "Invalid download task id" }
        return File(root, taskId).absoluteFile.also { directory ->
            if (directory.parentFile != root) throw IOException("Download task escaped its storage root")
        }
    }

    companion object {
        private const val MAX_STORAGE_NAME_BYTES = 200
        private val TASK_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")

        fun storageNameFor(remoteName: String): String {
            val cleaned = buildString(remoteName.length) {
                remoteName.forEach { character ->
                    append(
                        if (character == '/' || character == '\\' || Character.isISOControl(character)) {
                            '_'
                        } else {
                            character
                        },
                    )
                }
            }.trim().ifEmpty { "download" }
            if (cleaned == "." || cleaned == "..") return "download"
            if (cleaned.toByteArray(Charsets.UTF_8).size <= MAX_STORAGE_NAME_BYTES) return cleaned

            val extension = cleaned.substringAfterLast('.', "")
                .takeIf { it.isNotEmpty() && it.length <= 16 }
                ?.let { ".$it" }
                .orEmpty()
            val base = cleaned.dropLast(extension.length)
            val byteBudget = MAX_STORAGE_NAME_BYTES - extension.toByteArray(Charsets.UTF_8).size
            val truncated = buildString {
                var index = 0
                var usedBytes = 0
                while (index < base.length) {
                    val codePoint = base.codePointAt(index)
                    val value = String(Character.toChars(codePoint))
                    val valueBytes = value.toByteArray(Charsets.UTF_8).size
                    if (usedBytes + valueBytes > byteBudget) break
                    append(value)
                    usedBytes += valueBytes
                    index += Character.charCount(codePoint)
                }
            }.ifEmpty { "download" }
            return truncated + extension
        }

        private fun validateStorageName(name: String) {
            require(name.isNotBlank() && name != "." && name != "..") { "Invalid storage name" }
            require(name.none { it == '/' || it == '\\' || Character.isISOControl(it) }) {
                "Invalid storage name"
            }
        }
    }
}
