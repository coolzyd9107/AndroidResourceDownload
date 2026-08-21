package link.mczihan.androidResourceDownload.feature.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import link.mczihan.androidResourceDownload.data.file.FileRepository
import link.mczihan.androidResourceDownload.data.file.TextEncodingException
import link.mczihan.androidResourceDownload.data.file.UploadSourceResolver
import link.mczihan.androidResourceDownload.data.file.UploadDocument
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.FilePreviewContent
import link.mczihan.androidResourceDownload.domain.model.FilePreviewFormat
import link.mczihan.androidResourceDownload.domain.model.previewFormat
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.strongEntityTagOrNull

internal const val MAX_RESOURCE_NAME_LENGTH = 100

sealed interface FilesUiState {
    val path: WebDavPath

    data class Loading(override val path: WebDavPath) : FilesUiState
    data class Success(override val path: WebDavPath, val files: List<FileNode>) : FilesUiState
    data class Empty(override val path: WebDavPath) : FilesUiState
    data class Error(override val path: WebDavPath, val message: String, val unauthorized: Boolean = false) : FilesUiState
}

sealed interface DirectoryPickerState {
    data object Idle : DirectoryPickerState
    data class Loading(val path: WebDavPath) : DirectoryPickerState
    data class Success(val path: WebDavPath, val directories: List<FileNode>) : DirectoryPickerState
    data class Error(val path: WebDavPath, val message: String) : DirectoryPickerState
}

sealed interface FilePreviewUiState {
    data object Idle : FilePreviewUiState
    data class Loading(val file: FileNode) : FilePreviewUiState
    data class Content(val file: FileNode, val preview: FilePreviewContent) : FilePreviewUiState
    data class Editing(
        val file: FileNode,
        val original: FilePreviewContent.Text,
        val draft: String,
        val saving: Boolean = false,
        val error: String? = null,
    ) : FilePreviewUiState
    data class Error(val file: FileNode, val message: String) : FilePreviewUiState
}

sealed interface FileOperation {
    data class Upload(val document: UploadDocument, val destination: WebDavPath) : FileOperation
    data class CreateDirectory(val path: WebDavPath) : FileOperation
    data class Move(
        val source: WebDavPath,
        val destination: WebDavPath,
        val sourceIsDirectory: Boolean,
        val sourceEtag: String?,
    ) : FileOperation
    data class Rename(
        val source: WebDavPath,
        val destination: WebDavPath,
        val sourceIsDirectory: Boolean,
        val sourceEtag: String?,
    ) : FileOperation
    data class Copy(
        val source: WebDavPath,
        val destination: WebDavPath,
        val sourceIsDirectory: Boolean,
        val sourceEtag: String?,
    ) : FileOperation
    data class Delete(
        val path: WebDavPath,
        val isDirectory: Boolean,
        val etag: String?,
    ) : FileOperation
}

sealed interface FileMutationState {
    data object Idle : FileMutationState
    data object PreparingUpload : FileMutationState
    data class UploadReady(val document: UploadDocument, val directory: WebDavPath) : FileMutationState
    data class Running(
        val operation: FileOperation,
        val uploadedBytes: Long = 0L,
        val totalBytes: Long? = null,
        val committing: Boolean = false,
    ) : FileMutationState
    data class AwaitingOverwrite(val operation: FileOperation) : FileMutationState
    data class Failed(val operation: FileOperation?, val message: String) : FileMutationState
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: FileRepository,
    private val uploadSource: UploadSourceResolver = UploadSourceResolver {
        throw UnsupportedOperationException("Upload source is unavailable")
    },
) : ViewModel() {
    private val _state = MutableStateFlow<FilesUiState>(FilesUiState.Loading(WebDavPath.root()))
    val state: StateFlow<FilesUiState> = _state.asStateFlow()
    private val _mutationState = MutableStateFlow<FileMutationState>(FileMutationState.Idle)
    val mutationState: StateFlow<FileMutationState> = _mutationState.asStateFlow()
    private val _directoryPickerState = MutableStateFlow<DirectoryPickerState>(DirectoryPickerState.Idle)
    val directoryPickerState: StateFlow<DirectoryPickerState> = _directoryPickerState.asStateFlow()
    private val _previewState = MutableStateFlow<FilePreviewUiState>(FilePreviewUiState.Idle)
    val previewState: StateFlow<FilePreviewUiState> = _previewState.asStateFlow()
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var loadJob: Job? = null
    private var mutationJob: Job? = null
    private var directoryPickerJob: Job? = null
    private var previewJob: Job? = null
    private var loadVersion = 0L
    private var mutationVersion = 0L
    private var directoryPickerVersion = 0L
    private var previewVersion = 0L

    init {
        load(WebDavPath.root())
    }

    private fun load(path: WebDavPath) {
        val version = ++loadVersion
        loadJob?.cancel()
        _state.value = FilesUiState.Loading(path)
        loadJob = viewModelScope.launch {
            val result = try {
                repository.list(path).let { files ->
                    if (files.isEmpty()) FilesUiState.Empty(path) else FilesUiState.Success(path, files)
                }
            } catch (error: WebDavException.AuthenticationRequired) {
                FilesUiState.Error(path, "WebDAV 凭据已失效，请重新登录", unauthorized = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FilesUiState.Error(path, error.message ?: "文件列表加载失败")
            }
            if (version == loadVersion) _state.value = result
        }
    }

    fun openDirectory(path: WebDavPath) {
        load(path)
    }

    fun navigateUp() {
        val segments = _state.value.path.decodedSegments
        if (segments.isEmpty()) return
        load(WebDavPath.fromDecodedSegments(segments.dropLast(1)))
    }

    fun retry() = load(_state.value.path)

    fun preview(file: FileNode) {
        if (file.previewFormat() == null) return
        previewJob?.cancel()
        val version = ++previewVersion
        _previewState.value = FilePreviewUiState.Loading(file)
        previewJob = viewModelScope.launch {
            val nextState = try {
                FilePreviewUiState.Content(file, repository.preview(file))
            } catch (_: TimeoutCancellationException) {
                FilePreviewUiState.Error(file, "预览加载超时，请重试")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FilePreviewUiState.Error(file, error.previewMessage())
            }
            if (version == previewVersion) _previewState.value = nextState
        }
    }

    fun retryPreview() {
        val file = (_previewState.value as? FilePreviewUiState.Error)?.file ?: return
        preview(file)
    }

    fun startPreviewEdit() {
        val state = _previewState.value as? FilePreviewUiState.Content ?: return
        val text = state.preview as? FilePreviewContent.Text ?: return
        if (state.file.previewFormat() != FilePreviewFormat.PLAIN_TEXT ||
            text.truncated || !text.encodingEditable || text.entityTag.strongEntityTagOrNull() == null
        ) {
            return
        }
        _previewState.value = FilePreviewUiState.Editing(
            file = state.file,
            original = text,
            draft = text.text,
        )
    }

    fun updatePreviewDraft(text: String) {
        val state = _previewState.value as? FilePreviewUiState.Editing ?: return
        if (state.saving || text.length > MAX_EDITED_TEXT_CHARACTERS) return
        _previewState.value = state.copy(draft = text, error = null)
    }

    fun cancelPreviewEdit() {
        val state = _previewState.value as? FilePreviewUiState.Editing ?: return
        if (state.saving) return
        _previewState.value = FilePreviewUiState.Content(state.file, state.original)
    }

    fun savePreviewEdit() {
        val state = _previewState.value as? FilePreviewUiState.Editing ?: return
        if (state.saving || state.draft == state.original.text) return
        previewJob?.cancel()
        val version = ++previewVersion
        _previewState.value = state.copy(saving = true, error = null)
        previewJob = viewModelScope.launch {
            try {
                withTimeout(TEXT_SAVE_TIMEOUT_MILLIS) {
                    repository.updateText(state.file, state.original, state.draft)
                }
                if (version != previewVersion) return@launch
                _previewState.value = FilePreviewUiState.Idle
                load(_state.value.path)
                messageChannel.send("已保存 ${state.file.path}")
            } catch (_: TimeoutCancellationException) {
                if (version == previewVersion) {
                    _previewState.value = state.copy(saving = false, error = "保存超时，请确认网络后重试")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: WebDavException.PreconditionFailed) {
                if (version == previewVersion) {
                    _previewState.value = state.copy(
                        saving = false,
                        error = "云端文件已被修改，请取消编辑并重新打开预览",
                    )
                }
            } catch (error: Exception) {
                if (version == previewVersion) {
                    _previewState.value = state.copy(saving = false, error = error.editMessage())
                }
            }
        }
    }

    fun dismissPreview() {
        if ((_previewState.value as? FilePreviewUiState.Editing)?.saving == true) return
        previewVersion++
        previewJob?.cancel()
        _previewState.value = FilePreviewUiState.Idle
    }

    fun prepareUpload(uri: Uri) {
        if (_mutationState.value != FileMutationState.Idle) return
        mutationJob?.cancel()
        val version = ++mutationVersion
        val directory = _state.value.path
        _mutationState.value = FileMutationState.PreparingUpload
        mutationJob = viewModelScope.launch {
            val nextState = try {
                FileMutationState.UploadReady(uploadSource.resolve(uri), directory)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FileMutationState.Failed(null, error.userMessage())
            }
            updateMutation(version, nextState)
        }
    }

    fun upload(remoteName: String) {
        val ready = _mutationState.value as? FileMutationState.UploadReady ?: return
        val destination = runCatching { ready.directory.child(remoteName.trim()) }
            .getOrElse {
                _mutationState.value = FileMutationState.Failed(null, "文件名无效")
                return
            }
        runOperation(FileOperation.Upload(ready.document, destination), overwrite = false)
    }

    fun createDirectory(name: String) {
        if (_mutationState.value != FileMutationState.Idle) return
        val normalizedName = name.trim()
        if (normalizedName.length !in 1..MAX_RESOURCE_NAME_LENGTH) {
            _mutationState.value = FileMutationState.Failed(null, "文件夹名称需为 1-$MAX_RESOURCE_NAME_LENGTH 个字符")
            return
        }
        val destination = runCatching { _state.value.path.child(normalizedName) }
            .getOrElse {
                _mutationState.value = FileMutationState.Failed(null, "文件夹名称无效")
                return
            }
        runOperation(FileOperation.CreateDirectory(destination), overwrite = false)
    }

    fun openDestinationPicker(path: WebDavPath) = loadDestinationDirectories(path)

    fun openDestinationDirectory(path: WebDavPath) = loadDestinationDirectories(path)

    fun navigateDestinationUp() {
        val state = _directoryPickerState.value
        val path = when (state) {
            DirectoryPickerState.Idle -> return
            is DirectoryPickerState.Loading -> state.path
            is DirectoryPickerState.Success -> state.path
            is DirectoryPickerState.Error -> state.path
        }
        if (path.isRoot) return
        loadDestinationDirectories(WebDavPath.fromDecodedSegments(path.decodedSegments.dropLast(1)))
    }

    fun retryDestinationPicker() {
        val state = _directoryPickerState.value
        val path = when (state) {
            DirectoryPickerState.Idle -> return
            is DirectoryPickerState.Loading -> state.path
            is DirectoryPickerState.Success -> state.path
            is DirectoryPickerState.Error -> state.path
        }
        loadDestinationDirectories(path)
    }

    fun dismissDestinationPicker() {
        directoryPickerVersion++
        directoryPickerJob?.cancel()
        _directoryPickerState.value = DirectoryPickerState.Idle
    }

    fun move(
        source: WebDavPath,
        sourceIsDirectory: Boolean,
        destinationDirectory: WebDavPath,
        sourceEtag: String? = null,
    ) {
        transfer(source, sourceIsDirectory, sourceEtag, destinationDirectory, move = true)
    }

    fun copy(
        source: WebDavPath,
        sourceIsDirectory: Boolean,
        destinationDirectory: WebDavPath,
        sourceEtag: String? = null,
    ) {
        transfer(source, sourceIsDirectory, sourceEtag, destinationDirectory, move = false)
    }

    fun rename(
        source: WebDavPath,
        sourceIsDirectory: Boolean,
        newName: String,
        sourceEtag: String? = null,
    ) {
        if (_mutationState.value != FileMutationState.Idle) return
        val label = if (sourceIsDirectory) "文件夹名称" else "文件名"
        if (newName.isBlank() || newName.length > MAX_RESOURCE_NAME_LENGTH) {
            _mutationState.value = FileMutationState.Failed(
                null,
                "$label 需为 1-$MAX_RESOURCE_NAME_LENGTH 个字符",
            )
            return
        }
        if (source.name == null) {
            _mutationState.value = FileMutationState.Failed(null, "WebDAV 根目录不能重命名")
            return
        }
        val parent = WebDavPath.fromDecodedSegments(source.decodedSegments.dropLast(1))
        val destination = runCatching { parent.child(newName) }
            .getOrElse {
                _mutationState.value = FileMutationState.Failed(null, "$label 包含无效字符")
                return
            }
        if (destination == source) {
            _mutationState.value = FileMutationState.Failed(null, "新名称与当前名称相同")
            return
        }
        runOperation(
            FileOperation.Rename(source, destination, sourceIsDirectory, sourceEtag),
            overwrite = false,
        )
    }

    fun delete(path: WebDavPath, isDirectory: Boolean = false, etag: String? = null) {
        if (_mutationState.value != FileMutationState.Idle) return
        runOperation(FileOperation.Delete(path, isDirectory, etag), overwrite = false)
    }

    fun confirmOverwrite() {
        val operation = (_mutationState.value as? FileMutationState.AwaitingOverwrite)?.operation
            ?: return
        runOperation(operation, overwrite = true)
    }

    fun retryMutation() {
        val operation = (_mutationState.value as? FileMutationState.Failed)?.operation ?: return
        runOperation(operation, overwrite = false)
    }

    fun dismissMutation() {
        if (_mutationState.value !is FileMutationState.Running) {
            mutationVersion++
            mutationJob?.cancel()
            _mutationState.value = FileMutationState.Idle
        }
    }

    fun cancelMutation() {
        val running = _mutationState.value as? FileMutationState.Running ?: return
        if (running.operation !is FileOperation.Upload || running.committing) return
        mutationJob?.cancel()
    }

    private fun transfer(
        source: WebDavPath,
        sourceIsDirectory: Boolean,
        sourceEtag: String?,
        destinationDirectory: WebDavPath,
        move: Boolean,
    ) {
        if (_mutationState.value != FileMutationState.Idle) return
        val destination = runCatching { destinationDirectory.child(requireNotNull(source.name)) }
            .getOrElse {
                _mutationState.value = FileMutationState.Failed(null, "目标文件夹无效")
                return
            }
        val operation = if (move) {
            FileOperation.Move(source, destination, sourceIsDirectory, sourceEtag)
        } else {
            FileOperation.Copy(source, destination, sourceIsDirectory, sourceEtag)
        }
        runOperation(operation, overwrite = false)
    }

    private fun runOperation(operation: FileOperation, overwrite: Boolean) {
        if (_mutationState.value is FileMutationState.Running) return
        mutationJob?.cancel()
        val version = ++mutationVersion
        mutationJob = viewModelScope.launch {
            val totalBytes = (operation as? FileOperation.Upload)?.document?.contentLength
            updateMutation(version, FileMutationState.Running(operation, totalBytes = totalBytes))
            try {
                when (operation) {
                    is FileOperation.Upload -> {
                        var lastUpdateAt = 0L
                        repository.upload(
                            operation.destination,
                            operation.document.toWebDavUpload { uploadedBytes ->
                                val now = System.currentTimeMillis()
                                if (now - lastUpdateAt >= PROGRESS_UPDATE_INTERVAL_MILLIS ||
                                    uploadedBytes == totalBytes
                                ) {
                                    lastUpdateAt = now
                                    updateMutation(
                                        version,
                                        FileMutationState.Running(
                                            operation,
                                            uploadedBytes,
                                            totalBytes,
                                        ),
                                    )
                                }
                            },
                            overwrite,
                            onCommitting = {
                                updateMutation(
                                    version,
                                    FileMutationState.Running(
                                        operation = operation,
                                        uploadedBytes = totalBytes ?: 0L,
                                        totalBytes = totalBytes,
                                        committing = true,
                                    ),
                                )
                            },
                        )
                    }
                    is FileOperation.CreateDirectory -> repository.createDirectory(operation.path)
                    is FileOperation.Move -> repository.move(
                        operation.source,
                        operation.destination,
                        overwrite = overwrite,
                        sourceIsCollection = operation.sourceIsDirectory,
                        sourceEtag = operation.sourceEtag,
                    )
                    is FileOperation.Rename -> repository.move(
                        operation.source,
                        operation.destination,
                        overwrite = false,
                        sourceIsCollection = operation.sourceIsDirectory,
                        sourceEtag = operation.sourceEtag,
                    )
                    is FileOperation.Copy -> repository.copy(
                        operation.source,
                        operation.destination,
                        overwrite = overwrite,
                        sourceIsCollection = operation.sourceIsDirectory,
                        sourceEtag = operation.sourceEtag,
                    )
                    is FileOperation.Delete -> repository.delete(
                        operation.path,
                        operation.isDirectory,
                        operation.etag,
                    )
                }
                if (version != mutationVersion) return@launch
                _mutationState.value = FileMutationState.Idle
                load(_state.value.path)
                messageChannel.send(operation.successMessage())
            } catch (error: CancellationException) {
                if (version == mutationVersion) {
                    _mutationState.value = FileMutationState.Idle
                    load(_state.value.path)
                }
                throw error
            } catch (_: WebDavException.PreconditionFailed) {
                if (operation is FileOperation.CreateDirectory) {
                    updateMutation(
                        version,
                        FileMutationState.Failed(
                            null,
                            "同名文件或文件夹已存在，或云端不允许在此处新建文件夹",
                        ),
                    )
                    return@launch
                }
                if (operation is FileOperation.Rename) {
                    updateMutation(
                        version,
                        FileMutationState.Failed(
                            null,
                            "同名文件或文件夹已存在，或原文件或文件夹已发生变化，请刷新后重试",
                        ),
                    )
                    return@launch
                }
                val destination = operation.destinationOrNull()
                if (overwrite || destination == null) {
                    updateMutation(
                        version,
                        FileMutationState.Failed(operation, "源文件或目标已发生变化，请刷新后重试"),
                    )
                    return@launch
                }
                val destinationIsCollection = try {
                    repository.isCollection(destination)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    updateMutation(version, FileMutationState.Failed(operation, error.userMessage()))
                    return@launch
                }
                val nextState = if (destinationIsCollection == true) {
                    FileMutationState.Failed(
                        null,
                        "目标位置已有同名文件夹，不能直接覆盖。请选择其他位置，或先删除目标文件夹。",
                    )
                } else if (destinationIsCollection == null) {
                    FileMutationState.Failed(operation, "无法确认目标类型，请刷新后重试")
                } else {
                    FileMutationState.AwaitingOverwrite(operation)
                }
                updateMutation(version, nextState)
            } catch (error: Exception) {
                val failure = if (operation is FileOperation.Rename && error is WebDavException.Conflict) {
                    FileMutationState.Failed(
                        null,
                        "无法重命名：同名文件或文件夹已存在，或云端发生冲突",
                    )
                } else {
                    FileMutationState.Failed(operation, error.userMessage())
                }
                updateMutation(version, failure)
            }
        }
    }

    private fun updateMutation(version: Long, state: FileMutationState) {
        if (version == mutationVersion) _mutationState.value = state
    }

    private fun loadDestinationDirectories(path: WebDavPath) {
        val version = ++directoryPickerVersion
        directoryPickerJob?.cancel()
        _directoryPickerState.value = DirectoryPickerState.Loading(path)
        directoryPickerJob = viewModelScope.launch {
            val nextState = try {
                DirectoryPickerState.Success(
                    path,
                    repository.list(path).filter(FileNode::isDirectory),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DirectoryPickerState.Error(path, error.userMessage())
            }
            if (version == directoryPickerVersion) _directoryPickerState.value = nextState
        }
    }

    private fun FileOperation.successMessage(): String = when (this) {
        is FileOperation.Upload -> "已上传到 $destination"
        is FileOperation.CreateDirectory -> "已创建文件夹 $path"
        is FileOperation.Move -> "已移动到 $destination"
        is FileOperation.Rename -> "已重命名为 ${destination.name}"
        is FileOperation.Copy -> "已复制到 $destination"
        is FileOperation.Delete -> "已删除 $path"
    }

    private fun FileOperation.destinationOrNull(): WebDavPath? = when (this) {
        is FileOperation.Upload -> destination
        is FileOperation.CreateDirectory -> path
        is FileOperation.Move -> destination
        is FileOperation.Rename -> destination
        is FileOperation.Copy -> destination
        is FileOperation.Delete -> null
    }

    private fun Exception.userMessage(): String = when (this) {
        is WebDavException.AuthenticationRequired -> "WebDAV 凭据已失效，请重新登录"
        is WebDavException.PermissionDenied,
        is WebDavException.ReadWriteCredentialRequired,
        -> "当前账户没有云端写入权限"
        is WebDavException.CollectionOverwriteDenied ->
            "目标位置已有同名文件夹，不能直接覆盖。请选择其他位置，或先删除目标文件夹。"
        is WebDavException.NotFound -> "云端文件不存在，列表可能已变化"
        is WebDavException.Conflict -> "目标目录不存在或云端发生冲突"
        is WebDavException.Locked -> "云端文件已被锁定"
        is WebDavException.Network -> "网络连接失败，请稍后重试"
        is WebDavException.InvalidResponse -> "云端返回的数据无法确认文件类型，请刷新后重试"
        is IllegalArgumentException -> message ?: "路径无效"
        else -> message?.takeIf(String::isNotBlank) ?: "云端文件操作失败"
    }

    private fun Exception.previewMessage(): String = when (this) {
        is WebDavException.AuthenticationRequired -> "WebDAV 凭据已失效，请重新登录"
        is WebDavException.PermissionDenied -> "当前账户没有读取此文件的权限"
        is WebDavException.NotFound -> "云端文件不存在，列表可能已变化"
        is WebDavException.ResponseTooLarge -> "文件过大，无法在线预览"
        is WebDavException.InvalidResponse -> "文件内容损坏或格式不受支持"
        is WebDavException.Network -> "网络连接失败，请稍后重试"
        else -> "预览加载失败，请稍后重试"
    }

    private fun Exception.editMessage(): String = when (this) {
        is WebDavException.AuthenticationRequired -> "WebDAV 凭据已失效，请重新登录"
        is WebDavException.PermissionDenied,
        is WebDavException.ReadWriteCredentialRequired,
        -> "当前账户没有编辑权限"
        is WebDavException.NotFound -> "云端文件不存在，列表可能已变化"
        is WebDavException.ResponseTooLarge -> "编辑后的文本过大，无法保存"
        is WebDavException.Network -> "网络连接失败，请稍后重试"
        is TextEncodingException -> "新增字符无法使用文件原编码保存"
        else -> "保存失败，请稍后重试"
    }

    private companion object {
        const val MAX_EDITED_TEXT_CHARACTERS = 100_000
        const val TEXT_SAVE_TIMEOUT_MILLIS = 60_000L
        const val PROGRESS_UPDATE_INTERVAL_MILLIS = 100L
    }
}
