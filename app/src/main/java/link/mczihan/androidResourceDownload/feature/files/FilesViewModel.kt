package link.mczihan.androidResourceDownload.feature.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.data.file.FileRepository
import link.mczihan.androidResourceDownload.data.file.UploadSourceResolver
import link.mczihan.androidResourceDownload.data.file.UploadDocument
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath

sealed interface FilesUiState {
    val path: WebDavPath

    data class Loading(override val path: WebDavPath) : FilesUiState
    data class Success(override val path: WebDavPath, val files: List<FileNode>) : FilesUiState
    data class Empty(override val path: WebDavPath) : FilesUiState
    data class Error(override val path: WebDavPath, val message: String, val unauthorized: Boolean = false) : FilesUiState
}

sealed interface FileOperation {
    data class Upload(val document: UploadDocument, val destination: WebDavPath) : FileOperation
    data class Move(
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
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var loadJob: Job? = null
    private var mutationJob: Job? = null
    private var loadVersion = 0L
    private var mutationVersion = 0L

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

    fun prepareUpload(uri: Uri) {
        if (_mutationState.value is FileMutationState.Running) return
        mutationJob?.cancel()
        val version = ++mutationVersion
        val directory = _state.value.path
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

    fun move(
        source: WebDavPath,
        sourceIsDirectory: Boolean,
        destinationDirectory: String,
        destinationName: String,
        sourceEtag: String? = null,
    ) {
        transfer(source, sourceIsDirectory, sourceEtag, destinationDirectory, destinationName, move = true)
    }

    fun copy(
        source: WebDavPath,
        sourceIsDirectory: Boolean,
        destinationDirectory: String,
        destinationName: String,
        sourceEtag: String? = null,
    ) {
        transfer(source, sourceIsDirectory, sourceEtag, destinationDirectory, destinationName, move = false)
    }

    fun delete(path: WebDavPath, isDirectory: Boolean = false, etag: String? = null) {
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
        destinationDirectory: String,
        destinationName: String,
        move: Boolean,
    ) {
        val destination = runCatching {
            WebDavPath.parseDecoded(destinationDirectory.trim()).child(destinationName.trim())
        }.getOrElse {
            _mutationState.value = FileMutationState.Failed(null, "目标路径或名称无效")
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
                    is FileOperation.Move -> repository.move(
                        operation.source,
                        operation.destination,
                        overwrite = overwrite,
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
                        "目标是已有文件夹，禁止直接覆盖。请更换目标名称，或先单独删除目标文件夹。",
                    )
                } else if (destinationIsCollection == null) {
                    FileMutationState.Failed(operation, "无法确认目标类型，请刷新后重试")
                } else {
                    FileMutationState.AwaitingOverwrite(operation)
                }
                updateMutation(version, nextState)
            } catch (error: Exception) {
                updateMutation(version, FileMutationState.Failed(operation, error.userMessage()))
            }
        }
    }

    private fun updateMutation(version: Long, state: FileMutationState) {
        if (version == mutationVersion) _mutationState.value = state
    }

    private fun FileOperation.successMessage(): String = when (this) {
        is FileOperation.Upload -> "已上传到 $destination"
        is FileOperation.Move -> "已移动到 $destination"
        is FileOperation.Copy -> "已复制到 $destination"
        is FileOperation.Delete -> "已删除 $path"
    }

    private fun FileOperation.destinationOrNull(): WebDavPath? = when (this) {
        is FileOperation.Upload -> destination
        is FileOperation.Move -> destination
        is FileOperation.Copy -> destination
        is FileOperation.Delete -> null
    }

    private fun Exception.userMessage(): String = when (this) {
        is WebDavException.AuthenticationRequired -> "WebDAV 凭据已失效，请重新登录"
        is WebDavException.PermissionDenied,
        is WebDavException.ReadWriteCredentialRequired,
        -> "当前账户没有云端写入权限"
        is WebDavException.CollectionOverwriteDenied ->
            "目标是已有文件夹，禁止直接覆盖。请更换目标名称，或先单独删除目标文件夹。"
        is WebDavException.NotFound -> "云端文件不存在，列表可能已变化"
        is WebDavException.Conflict -> "目标目录不存在或云端发生冲突"
        is WebDavException.Locked -> "云端文件已被锁定"
        is WebDavException.Network -> "网络连接失败，请稍后重试"
        is WebDavException.InvalidResponse -> "云端返回的数据无法确认文件类型，请刷新后重试"
        is IllegalArgumentException -> message ?: "路径无效"
        else -> message?.takeIf(String::isNotBlank) ?: "云端文件操作失败"
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MILLIS = 100L
    }
}
