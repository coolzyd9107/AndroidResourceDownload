package com.resdownload.android.data.upload

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.resdownload.android.domain.webdav.WebDavPath

data class UploadSourceEntry(
    val sourceUri: Uri?,
    val relativeSegments: List<String>,
    val isDirectory: Boolean,
    val size: Long?,
    val mimeType: String?,
)

class UploadSelectionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

interface UploadSelectionScanner {
    suspend fun scanFile(uri: Uri): List<UploadSourceEntry>
    suspend fun scanTree(uri: Uri): List<UploadSourceEntry>
}

@Singleton
class DocumentFileUploadSelectionScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : UploadSelectionScanner {
    override suspend fun scanFile(uri: Uri): List<UploadSourceEntry> = withContext(Dispatchers.IO) {
        val document = DocumentFile.fromSingleUri(context, uri)
            ?: throw UploadSelectionException("无法读取所选文件")
        if (!document.exists() || !document.canRead() || document.isDirectory || document.isVirtual) {
            throw UploadSelectionException("所选内容不是可读取的文件")
        }
        val name = requireValidName(document.name)
        listOf(
            UploadSourceEntry(
                sourceUri = document.uri,
                relativeSegments = listOf(name),
                isDirectory = false,
                size = querySize(document),
                mimeType = document.type ?: context.contentResolver.getType(document.uri),
            ),
        )
    }

    override suspend fun scanTree(uri: Uri): List<UploadSourceEntry> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: throw UploadSelectionException("无法读取所选文件夹")
        if (!root.exists() || !root.canRead() || !root.isDirectory) {
            throw UploadSelectionException("所选内容不是可读取的文件夹")
        }
        val entries = mutableListOf<UploadSourceEntry>()
        val seenUris = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()
        val budget = TraversalBudget(discoveredEntries = 1)
        walkDirectory(
            directoryUri = root.uri,
            relativeSegments = listOf(requireValidName(root.name)),
            depth = 1,
            entries = entries,
            seenUris = seenUris,
            seenPaths = seenPaths,
            budget = budget,
        )
        entries
    }

    private suspend fun walkDirectory(
        directoryUri: Uri,
        relativeSegments: List<String>,
        depth: Int,
        entries: MutableList<UploadSourceEntry>,
        seenUris: MutableSet<String>,
        seenPaths: MutableSet<String>,
        budget: TraversalBudget,
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > MAX_TREE_DEPTH) {
            throw UploadSelectionException("文件夹层级超过 $MAX_TREE_DEPTH 层")
        }
        addEntry(
            UploadSourceEntry(
                sourceUri = null,
                relativeSegments = relativeSegments,
                isDirectory = true,
                size = null,
                mimeType = null,
            ),
            directoryUri,
            entries,
            seenUris,
            seenPaths,
        )
        val children = queryChildren(
            directoryUri,
            relativeSegments,
            budget,
        )
        children.forEach { child ->
            currentCoroutineContext().ensureActive()
            val name = requireValidName(child.name)
            val childSegments = relativeSegments + name
            when {
                child.isVirtual -> throw UploadSelectionException(
                    "暂不支持虚拟文档：${childSegments.joinToString("/")}",
                )
                child.isDirectory -> walkDirectory(
                    child.uri,
                    childSegments,
                    depth + 1,
                    entries,
                    seenUris,
                    seenPaths,
                    budget,
                )
                else -> addEntry(
                    UploadSourceEntry(
                        sourceUri = child.uri,
                        relativeSegments = childSegments,
                        isDirectory = false,
                        size = child.size,
                        mimeType = child.mimeType,
                    ),
                    child.uri,
                    entries,
                    seenUris,
                    seenPaths,
                )
            }
        }
    }

    private suspend fun queryChildren(
        directoryUri: Uri,
        relativeSegments: List<String>,
        budget: TraversalBudget,
    ): List<ProviderDocument> {
        val path = relativeSegments.joinToString("/")
        return try {
            val documentId = runCatching { DocumentsContract.getDocumentId(directoryUri) }
                .getOrElse { DocumentsContract.getTreeDocumentId(directoryUri) }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryUri,
                documentId,
            )
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                try {
                    val cursor = context.contentResolver.query(
                        childrenUri,
                        CHILD_PROJECTION,
                        null,
                        null,
                        null,
                        cancellationSignal,
                    ) ?: throw UploadSelectionException("文件夹提供器未返回目录内容：$path")
                    val result = cursor.use {
                        buildList {
                            while (cursor.moveToNext()) {
                                if (!continuation.isActive) throw CancellationException()
                                if (budget.discoveredEntries >= MAX_TREE_ENTRIES) {
                                    throw UploadSelectionException(
                                        "单次最多上传 $MAX_TREE_ENTRIES 个文件和文件夹",
                                    )
                                }
                                budget.discoveredEntries++
                                val childId = cursor.getString(0)
                                    ?: throw UploadSelectionException(
                                        "文件夹包含缺少标识的文档：$path",
                                    )
                                val mimeType = cursor.getString(2)
                                val flags = if (cursor.isNull(4)) 0 else cursor.getInt(4)
                                add(
                                    ProviderDocument(
                                        uri = DocumentsContract.buildDocumentUriUsingTree(
                                            directoryUri,
                                            childId,
                                        ),
                                        name = cursor.getString(1),
                                        isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                                        isVirtual = flags and
                                            DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT != 0,
                                        size = if (cursor.isNull(3)) null else cursor.getLong(3)
                                            .takeIf { it >= 0L },
                                        mimeType = mimeType?.takeUnless {
                                            it == DocumentsContract.Document.MIME_TYPE_DIR
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }.sortedWith(
                compareByDescending<ProviderDocument>(ProviderDocument::isDirectory)
                    .thenBy { it.name.orEmpty() },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: UploadSelectionException) {
            throw error
        } catch (error: Exception) {
            throw UploadSelectionException("无法读取文件夹 $path", error)
        }
    }

    private fun addEntry(
        entry: UploadSourceEntry,
        uri: Uri,
        entries: MutableList<UploadSourceEntry>,
        seenUris: MutableSet<String>,
        seenPaths: MutableSet<String>,
    ) {
        if (!seenUris.add(uri.toString())) {
            throw UploadSelectionException("文件夹包含循环或重复的文档引用")
        }
        val relativePath = entry.relativeSegments.joinToString("/")
        if (!seenPaths.add(relativePath)) {
            throw UploadSelectionException("文件夹包含重复路径：$relativePath")
        }
        if (entries.size >= MAX_TREE_ENTRIES) {
            throw UploadSelectionException("单次最多上传 $MAX_TREE_ENTRIES 个文件和文件夹")
        }
        entries += entry
    }

    private fun requireValidName(value: String?): String {
        val name = value ?: throw UploadSelectionException("文件或文件夹缺少名称")
        if (name.isBlank() || name.length > MAX_UPLOAD_NAME_LENGTH) {
            throw UploadSelectionException("文件或文件夹名称需为 1-$MAX_UPLOAD_NAME_LENGTH 个字符")
        }
        try {
            WebDavPath.root().child(name)
        } catch (error: Exception) {
            throw UploadSelectionException("文件或文件夹名称包含无效字符：$name", error)
        }
        return name
    }

    private fun querySize(document: DocumentFile): Long? {
        val resolver = context.contentResolver
        val queried = resolver.query(
            document.uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0).takeIf { it >= 0L }
        }
        return queried ?: document.length().takeIf { it > 0L }
    }

    private companion object {
        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        const val MAX_UPLOAD_NAME_LENGTH = 100
        const val MAX_TREE_DEPTH = 64
        const val MAX_TREE_ENTRIES = 10_000
    }
}

private data class ProviderDocument(
    val uri: Uri,
    val name: String?,
    val isDirectory: Boolean,
    val isVirtual: Boolean,
    val size: Long?,
    val mimeType: String?,
)

private data class TraversalBudget(var discoveredEntries: Int)
