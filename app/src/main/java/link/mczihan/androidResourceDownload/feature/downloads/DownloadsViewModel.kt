package link.mczihan.androidResourceDownload.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.data.download.DownloadFileOpener
import link.mczihan.androidResourceDownload.data.download.DownloadRepository
import link.mczihan.androidResourceDownload.data.download.EnqueueResult
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.service.DownloadQueueController

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val queueController: DownloadQueueController,
    private val fileOpener: DownloadFileOpener,
) : ViewModel() {
    private val ownerId = MutableStateFlow<String?>(null)
    private val messageChannel = Channel<String>(Channel.BUFFERED)

    val tasks = ownerId
        .filterNotNull()
        .flatMapLatest(repository::observe)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
    private val speedEstimator = DownloadSpeedEstimator()
    private val _currentSpeeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val currentSpeeds = _currentSpeeds.asStateFlow()
    val messages = messageChannel.receiveAsFlow()
    private var speedTrackingJob: Job? = null
    private var reconciliationJob: Job? = null

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

    fun bindOwner(value: String) {
        queueController.activate(value)
        if (ownerId.value == value) return
        ownerId.value = value
        viewModelScope.launch {
            if (queueController.hasPublicDownloadAccess()) {
                repository.reconcileUncommittedPublications(value)
            }
            queueController.startIfNeeded(value)
        }
    }

    fun enqueue(file: FileNode) {
        val currentOwner = ownerId.value ?: return
        viewModelScope.launch {
            val result = try {
                repository.enqueue(currentOwner, file)
            } catch (_: IllegalArgumentException) {
                messageChannel.send("文件路径无效，无法下载")
                return@launch
            }
            val message = when (result) {
                EnqueueResult.ADDED -> "已加入下载队列"
                EnqueueResult.RESTARTED -> "已重新加入下载队列"
                EnqueueResult.ALREADY_QUEUED -> "该文件已在下载队列中"
                EnqueueResult.ALREADY_DOWNLOADED -> "该文件已经下载完成"
            }
            if ((result == EnqueueResult.ADDED || result == EnqueueResult.RESTARTED) &&
                !queueController.start(currentOwner)
            ) {
                messageChannel.send("任务已保存，系统暂未启动下载")
            } else {
                messageChannel.send(message)
            }
        }
    }

    fun pause(taskId: String) = withOwner { owner ->
        if (!queueController.pause(owner, taskId)) messageChannel.send("任务状态已发生变化")
    }

    fun retry(taskId: String) = withOwner { owner ->
        if (!queueController.retry(owner, taskId)) messageChannel.send("无法重新启动该任务")
    }

    fun startPending() = withOwner { owner ->
        repository.reconcileUncommittedPublications(owner)
        if (!queueController.startIfNeeded(owner)) messageChannel.send("无法启动下载队列")
    }

    fun removeMissingSuccessful(ownerId: String): Job {
        reconciliationJob?.cancel()
        return viewModelScope.launch {
            repository.removeMissingSuccessful(ownerId)
        }.also { reconciliationJob = it }
    }

    fun cancel(taskId: String) = withOwner { owner ->
        if (!queueController.cancel(owner, taskId)) messageChannel.send("任务状态已发生变化")
    }

    fun delete(taskId: String) = withOwner { owner ->
        if (!queueController.deleteTerminal(owner, taskId)) {
            messageChannel.send("任务状态已变化，或本地文件无法删除")
        }
    }

    fun open(task: DownloadTask) {
        if (!fileOpener.open(task)) {
            viewModelScope.launch { messageChannel.send("没有可打开此文件的应用，或文件已被移除") }
        }
    }

    private fun withOwner(block: suspend (String) -> Unit) {
        val currentOwner = ownerId.value ?: return
        viewModelScope.launch { block(currentOwner) }
    }

    private companion object {
        const val SPEED_REFRESH_INTERVAL_MILLIS = 1_000L
    }
}
