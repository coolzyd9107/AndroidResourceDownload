package com.resdownload.android.feature.uploads

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.resdownload.android.data.file.FileRepository
import com.resdownload.android.data.upload.UploadEnqueueSummary
import com.resdownload.android.data.upload.UploadRepository
import com.resdownload.android.data.upload.UploadSelectionException
import com.resdownload.android.domain.model.FileNode
import com.resdownload.android.domain.webdav.WebDavException
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.service.UploadQueueController

sealed interface UploadDestinationPickerState {
    val path: WebDavPath
    val fileCount: Int

    data object Idle : UploadDestinationPickerState {
        override val path: WebDavPath = WebDavPath.root()
        override val fileCount: Int = 0
    }

    data class Loading(
        override val path: WebDavPath,
        override val fileCount: Int,
    ) : UploadDestinationPickerState

    data class Success(
        override val path: WebDavPath,
        override val fileCount: Int,
        val directories: List<FileNode>,
    ) : UploadDestinationPickerState

    data class Error(
        override val path: WebDavPath,
        override val fileCount: Int,
        val message: String,
    ) : UploadDestinationPickerState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UploadsViewModel @Inject constructor(
    private val repository: UploadRepository,
    private val fileRepository: FileRepository,
    private val queueController: UploadQueueController,
) : ViewModel() {
    private val ownerId = MutableStateFlow<String?>(null)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    private val _preparingSelections = MutableStateFlow(0)
    val preparingSelections = _preparingSelections.asStateFlow()
    val messages = messageChannel.receiveAsFlow()
    val tasks = ownerId
        .filterNotNull()
        .flatMapLatest(repository::observe)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
    private val speedEstimator = UploadSpeedEstimator()
    private val _currentSpeeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val currentSpeeds = _currentSpeeds.asStateFlow()
    private var speedTrackingJob: Job? = null
    private val _destinationPickerState = MutableStateFlow<UploadDestinationPickerState>(
        UploadDestinationPickerState.Idle,
    )
    val destinationPickerState = _destinationPickerState.asStateFlow()
    private var destinationPickerJob: Job? = null
    private var destinationPickerVersion = 0L
    private var pendingFileUris: List<Uri> = emptyList()

    fun bindOwner(value: String) {
        if (ownerId.value != null && ownerId.value != value) {
            dismissDestinationPicker()
        }
        queueController.activate(value)
        if (ownerId.value == value) return
        ownerId.value = value
        viewModelScope.launch {
            repository.reconcilePermissionReservations()
            queueController.startIfNeeded(value)
        }
    }

    fun beginFileUpload(uris: List<Uri>) {
        val distinctUris = uris.distinct()
        if (distinctUris.isEmpty()) return
        pendingFileUris = distinctUris
        loadDestinationDirectories(WebDavPath.root())
    }

    fun openDestinationDirectory(path: WebDavPath) {
        if (pendingFileUris.isNotEmpty()) loadDestinationDirectories(path)
    }

    fun navigateDestinationUp() {
        val path = _destinationPickerState.value.path
        if (path.isRoot || pendingFileUris.isEmpty()) return
        loadDestinationDirectories(
            WebDavPath.fromDecodedSegments(path.decodedSegments.dropLast(1)),
        )
    }

    fun retryDestinationPicker() {
        if (pendingFileUris.isNotEmpty()) {
            loadDestinationDirectories(_destinationPickerState.value.path)
        }
    }

    fun dismissDestinationPicker() {
        destinationPickerVersion++
        destinationPickerJob?.cancel()
        destinationPickerJob = null
        pendingFileUris = emptyList()
        _destinationPickerState.value = UploadDestinationPickerState.Idle
    }

    fun confirmFileUpload(destination: WebDavPath) {
        val state = _destinationPickerState.value
        if (state !is UploadDestinationPickerState.Success || state.path != destination) return
        val uris = pendingFileUris
        if (uris.isEmpty()) return
        dismissDestinationPicker()
        enqueueFiles(uris, destination)
    }

    fun enqueueFile(uri: Uri, destination: WebDavPath) = enqueueFiles(listOf(uri), destination)

    fun enqueueFiles(uris: List<Uri>, destination: WebDavPath) {
        val owner = ownerId.value ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _preparingSelections.update { it + 1 }
            var addedFiles = 0
            var skipped = 0
            var failed = 0
            try {
                uris.distinct().forEach { uri ->
                    try {
                        val result = repository.enqueueFile(owner, destination, uri)
                        addedFiles += result.addedFiles
                        skipped += result.skipped
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        failed++
                    }
                }
                val message = when {
                    addedFiles > 0 && failed == 0 && skipped == 0 -> "$addedFiles 个文件已加入上传队列"
                    addedFiles > 0 -> "已加入 $addedFiles 个文件，跳过 ${failed + skipped} 个"
                    failed > 0 -> "无法读取所选文件，请重新选择"
                    else -> "所选文件已在上传队列中"
                }
                if (addedFiles > 0 && !queueController.start(owner)) {
                    messageChannel.send("任务已保存，系统暂未启动上传")
                } else {
                    messageChannel.send(message)
                }
            } finally {
                _preparingSelections.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    fun enqueueTree(uri: Uri, destination: WebDavPath) = enqueue(uri, destination, tree = true)

    private fun enqueue(uri: Uri, destination: WebDavPath, tree: Boolean) {
        val owner = ownerId.value ?: return
        viewModelScope.launch {
            _preparingSelections.update { it + 1 }
            try {
                val result = if (tree) {
                    repository.enqueueTree(owner, destination, uri)
                } else {
                    repository.enqueueFile(owner, destination, uri)
                }
                if (result.added > 0 && !queueController.start(owner)) {
                    messageChannel.send("任务已保存，系统暂未启动上传")
                } else {
                    messageChannel.send(result.toUserMessage())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                messageChannel.send(error.toSelectionMessage())
            } finally {
                _preparingSelections.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    fun retry(taskId: String) = withOwner { owner ->
        if (!queueController.retry(owner, taskId)) messageChannel.send("无法重新启动该任务")
    }

    fun cancel(taskId: String) = withOwner { owner ->
        if (!queueController.cancel(owner, taskId)) {
            messageChannel.send("任务已进入提交阶段或状态已发生变化")
        }
    }

    fun delete(taskId: String) = withOwner { owner ->
        if (!queueController.deleteTerminal(owner, taskId)) {
            messageChannel.send("任务状态已发生变化，无法删除")
        }
    }

    fun cancelAll() = withOwner { owner ->
        val count = queueController.cancelAll(owner)
        messageChannel.send(if (count > 0) "已取消 $count 个任务" else "没有可取消的任务")
    }

    fun clearTerminal() = withOwner { owner ->
        val count = queueController.clearTerminal(owner)
        messageChannel.send(if (count > 0) "已清除 $count 个已结束任务" else "没有可清除的任务")
    }

    fun startPending() = withOwner { owner ->
        if (!queueController.startIfNeeded(owner)) {
            messageChannel.send("无法启动上传队列")
        }
    }

    fun startSpeedTracking() {
        if (speedTrackingJob?.isActive == true) return
        speedTrackingJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    tasks.collect { currentTasks ->
                        _currentSpeeds.value = speedEstimator.update(
                            currentTasks,
                            System.currentTimeMillis(),
                        )
                    }
                }
                launch {
                    while (isActive) {
                        delay(SPEED_REFRESH_INTERVAL_MILLIS)
                        _currentSpeeds.value = speedEstimator.snapshot(System.currentTimeMillis())
                    }
                }
            }
        }
    }

    fun stopSpeedTracking() {
        speedTrackingJob?.cancel()
        speedTrackingJob = null
        speedEstimator.clear()
        _currentSpeeds.value = emptyMap()
    }

    private fun withOwner(block: suspend (String) -> Unit) {
        val owner = ownerId.value ?: return
        viewModelScope.launch { block(owner) }
    }

    private fun loadDestinationDirectories(path: WebDavPath) {
        val fileCount = pendingFileUris.size
        if (fileCount == 0) return
        val version = ++destinationPickerVersion
        destinationPickerJob?.cancel()
        _destinationPickerState.value = UploadDestinationPickerState.Loading(path, fileCount)
        destinationPickerJob = viewModelScope.launch {
            val nextState = try {
                UploadDestinationPickerState.Success(
                    path = path,
                    fileCount = fileCount,
                    directories = fileRepository.list(path).filter {
                        it.isDirectory && !it.isUploadTemporary
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UploadDestinationPickerState.Error(
                    path = path,
                    fileCount = fileCount,
                    message = error.toDestinationMessage(),
                )
            }
            if (version == destinationPickerVersion) _destinationPickerState.value = nextState
        }
    }

    private fun UploadEnqueueSummary.toUserMessage(): String = when {
        added == 0 && skipped > 0 -> "所选内容已在上传队列中"
        addedDirectories == 0 && skipped == 0 -> "已加入上传队列"
        skipped == 0 -> "已加入上传队列：$addedFiles 个文件，$addedDirectories 个文件夹"
        else -> "已加入 $added 个任务，跳过 $skipped 个重复目标"
    }

    private fun Exception.toSelectionMessage(): String = when (this) {
        is UploadSelectionException -> message ?: "无法读取所选内容"
        is SecurityException -> "所选内容的读取权限无效，请重新选择"
        is IllegalArgumentException -> message ?: "上传目标路径无效"
        else -> "无法创建上传任务，请重试"
    }

    private fun Exception.toDestinationMessage(): String = when (this) {
        is WebDavException.AuthenticationRequired,
        is WebDavException.CredentialUnavailable,
        -> "登录或 WebDAV 凭据已失效"
        is WebDavException.PermissionDenied,
        is WebDavException.ReadWriteCredentialRequired,
        -> "当前账户没有读取云端目录的权限"
        is WebDavException.NotFound -> "保存位置不存在"
        is WebDavException.Network -> "网络连接失败，请重试"
        is WebDavException.InvalidResponse -> "云端目录响应无效，请重试"
        else -> message?.takeIf(String::isNotBlank) ?: "无法加载保存位置"
    }

    private companion object {
        const val SPEED_REFRESH_INTERVAL_MILLIS = 1_000L
    }
}
