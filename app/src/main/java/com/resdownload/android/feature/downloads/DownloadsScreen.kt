package com.resdownload.android.feature.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.resdownload.android.core.common.formatFileSize
import com.resdownload.android.core.ui.EmptyPane
import com.resdownload.android.core.ui.SearchTopAppBar
import com.resdownload.android.core.ui.SelectionAction
import com.resdownload.android.core.ui.SelectionBottomBar
import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    tasks: List<DownloadTask>,
    currentSpeeds: Map<String, Long> = emptyMap(),
    onStatusChange: (taskId: String, status: DownloadStatus) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onDelete: (taskId: String) -> Unit,
    onDeleteWithOption: (taskId: String, deleteLocalFile: Boolean) -> Unit = { _, _ -> },
    onCancelAll: () -> Unit = {},
    onClearTerminal: (deleteLocalFiles: Boolean) -> Unit = {},
    onMultiSelectModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var deleteTaskIds by remember { mutableStateOf<List<String>?>(null) }
    var deleteLocalFile by remember { mutableStateOf(true) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearLocalFiles by remember { mutableStateOf(true) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedTaskIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var selectedSearchTaskIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var hasObservedTasks by remember { mutableStateOf(tasks.isNotEmpty()) }
    SideEffect { onMultiSelectModeChange(multiSelectMode) }
    DisposableEffect(Unit) {
        onDispose { onMultiSelectModeChange(false) }
    }
    val itemEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val itemSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val contentSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val countSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val searchingSelectedTasks = multiSelectMode && searchActive && selectedSearchTaskIds.isNotEmpty()
    val searchCandidates = if (searchingSelectedTasks) {
        val candidateIds = selectedSearchTaskIds.toSet()
        tasks.filter { it.id in candidateIds }
    } else {
        tasks
    }
    val filteredTasks = filterDownloadTasks(
        searchCandidates,
        if (searchActive) searchQuery else "",
    )
    val normalizedSearchQuery = searchQuery.trim()
    val selectedTaskIdSet = selectedTaskIds.toSet()
    val selectedTasks = tasks.filter { it.id in selectedTaskIdSet }
    val allVisibleTasksSelected = filteredTasks.isNotEmpty() &&
        filteredTasks.all { it.id in selectedTaskIdSet }
    val resumableTasks = selectedTasks.filter { task ->
        task.status in setOf(
            DownloadStatus.PAUSED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
    }
    val pausableTasks = selectedTasks.filter { it.status == DownloadStatus.RUNNING }
    val cancellableSelectedTasks = selectedTasks.filter { task ->
        task.status in setOf(
            DownloadStatus.PENDING,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
        )
    }
    val deletableSelectedTasks = selectedTasks.filter { task ->
        task.status in setOf(
            DownloadStatus.SUCCESS,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
    }
    val hasCancellableTasks = tasks.any { task ->
        task.status in setOf(
            DownloadStatus.PENDING,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
        )
    }
    val hasClearableTasks = tasks.any { task ->
        task.status in setOf(
            DownloadStatus.SUCCESS,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
    }
    val listBottomPadding = when {
        multiSelectMode -> 16.dp
        hasCancellableTasks && hasClearableTasks -> 152.dp
        hasCancellableTasks || hasClearableTasks -> 84.dp
        else -> 16.dp
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        selectedTaskIds = emptyList()
        if (selectedSearchTaskIds.isNotEmpty()) {
            selectedSearchTaskIds = emptyList()
            searchActive = false
            searchQuery = ""
        }
    }

    fun toggleTaskSelection(taskId: String) {
        selectedTaskIds = selectedTaskIds.toMutableList().apply {
            if (contains(taskId)) remove(taskId) else add(taskId)
        }
    }

    fun toggleAllVisibleTasks() {
        val visibleIds = filteredTasks.map(DownloadTask::id)
        selectedTaskIds = if (visibleIds.isNotEmpty() && visibleIds.all(selectedTaskIdSet::contains)) {
            selectedTaskIds.filterNot(visibleIds::contains)
        } else {
            (selectedTaskIds + visibleIds).distinct()
        }
    }

    fun invertVisibleTasks() {
        val visibleIds = filteredTasks.map(DownloadTask::id)
        selectedTaskIds = selectedTaskIds.filterNot(visibleIds::contains) +
            visibleIds.filterNot(selectedTaskIdSet::contains)
    }

    LaunchedEffect(tasks.map(DownloadTask::id)) {
        if (tasks.isNotEmpty()) hasObservedTasks = true
        if (tasks.isNotEmpty() || hasObservedTasks) {
            val retainedIds = selectedTaskIds.filter { selectedId ->
                tasks.any { it.id == selectedId }
            }
            if (retainedIds != selectedTaskIds) {
                val hadSelection = selectedTaskIds.isNotEmpty()
                selectedTaskIds = retainedIds
                if (hadSelection && retainedIds.isEmpty()) multiSelectMode = false
            }
            selectedSearchTaskIds = selectedSearchTaskIds.filter { selectedId ->
                tasks.any { it.id == selectedId }
            }
        }
    }

    BackHandler(enabled = searchActive && (!multiSelectMode || searchingSelectedTasks)) {
        searchActive = false
        searchQuery = ""
        selectedSearchTaskIds = emptyList()
    }

    BackHandler(
        enabled = multiSelectMode && !searchingSelectedTasks,
        onBack = ::exitMultiSelect,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            if (searchingSelectedTasks) {
                SearchTopAppBar(
                    query = searchQuery,
                    placeholder = "在已选下载任务中搜索",
                    closeContentDescription = "返回下载任务选择",
                    searchContentDescription = "搜索已选下载任务",
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    showSearchAction = false,
                    onClose = {
                        searchActive = false
                        searchQuery = ""
                        selectedSearchTaskIds = emptyList()
                    },
                    subtitle = "${filteredTasks.size} / ${selectedSearchTaskIds.size} 个已选任务",
                )
            } else if (multiSelectMode) {
                TopAppBar(
                    title = { Text("已选择 ${selectedTasks.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = ::exitMultiSelect) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "退出下载任务选择",
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedTaskIds.isEmpty()) {
                                    exitMultiSelect()
                                } else {
                                    selectedSearchTaskIds = selectedTaskIds
                                }
                                searchQuery = ""
                                searchActive = true
                            },
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "在已选下载任务中搜索",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            } else if (searchActive) {
                SearchTopAppBar(
                    query = searchQuery,
                    placeholder = "搜索下载任务",
                    closeContentDescription = "关闭下载搜索",
                    searchContentDescription = "执行下载搜索",
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    showSearchAction = false,
                    onClose = {
                        searchActive = false
                        searchQuery = ""
                        selectedSearchTaskIds = emptyList()
                    },
                    subtitle = normalizedSearchQuery.takeIf(String::isNotEmpty)?.let {
                        "${filteredTasks.size} / ${tasks.size} 个任务"
                    },
                    additionalActions = {
                        if (filteredTasks.isNotEmpty()) {
                            IconButton(onClick = { multiSelectMode = true }) {
                                Icon(
                                    Icons.Default.Checklist,
                                    contentDescription = "选择下载搜索结果",
                                )
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("下载") },
                    subtitle = {
                        AnimatedContent(
                            targetState = tasks.size,
                            transitionSpec = {
                                (fadeIn(itemEffectsSpec) +
                                    slideInVertically(countSpatialSpec) { it / 2 })
                                    .togetherWith(
                                        fadeOut(itemEffectsSpec) +
                                            slideOutVertically(countSpatialSpec) { -it / 2 },
                                    )
                            },
                            label = "downloadCount",
                        ) { count ->
                            Text(text = if (count == 0) "暂无任务" else "$count 个任务")
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索下载任务")
                        }
                        if (tasks.isNotEmpty()) {
                            IconButton(onClick = { multiSelectMode = true }) {
                                Icon(
                                    Icons.Default.Checklist,
                                    contentDescription = "选择下载任务",
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            if (multiSelectMode) {
                SelectionBottomBar {
                    SelectionAction(
                        icon = if (allVisibleTasksSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        label = if (allVisibleTasksSelected) "取消全选" else "全选",
                        onClick = ::toggleAllVisibleTasks,
                        enabled = filteredTasks.isNotEmpty(),
                    )
                    SelectionAction(
                        icon = Icons.Default.FlipToBack,
                        label = "反选",
                        onClick = ::invertVisibleTasks,
                        enabled = filteredTasks.isNotEmpty(),
                    )
                    SelectionAction(
                        icon = Icons.Default.PlayArrow,
                        label = "继续",
                        enabled = resumableTasks.isNotEmpty(),
                        onClick = {
                            resumableTasks.forEach { onStatusChange(it.id, DownloadStatus.RUNNING) }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Pause,
                        label = "暂停",
                        enabled = pausableTasks.isNotEmpty(),
                        onClick = {
                            pausableTasks.forEach { onStatusChange(it.id, DownloadStatus.PAUSED) }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Cancel,
                        label = "取消",
                        enabled = cancellableSelectedTasks.isNotEmpty(),
                        destructive = true,
                        onClick = {
                            cancellableSelectedTasks.forEach {
                                onStatusChange(it.id, DownloadStatus.CANCELLED)
                            }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Delete,
                        label = "删除",
                        enabled = deletableSelectedTasks.isNotEmpty(),
                        destructive = true,
                        onClick = {
                            deleteLocalFile = true
                            deleteTaskIds = deletableSelectedTasks.map(DownloadTask::id)
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (!multiSelectMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (hasCancellableTasks) {
                        ExtendedFloatingActionButton(
                            text = { Text("全部取消") },
                            icon = { Icon(Icons.Default.Cancel, contentDescription = null) },
                            modifier = Modifier.testTag("cancelAllTasks"),
                            onClick = { showCancelAllDialog = true },
                        )
                    }
                    if (hasClearableTasks) {
                        ExtendedFloatingActionButton(
                            text = { Text("全部清除") },
                            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                            modifier = Modifier.testTag("clearTerminalTasks"),
                            onClick = {
                                clearLocalFiles = true
                                showClearDialog = true
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = filteredTasks,
            contentKey = { visibleTasks -> visibleTasks.isEmpty() },
            transitionSpec = {
                (fadeIn(itemEffectsSpec) + scaleIn(contentSpatialSpec, initialScale = 0.98f))
                    .togetherWith(
                        fadeOut(itemEffectsSpec) + scaleOut(contentSpatialSpec, targetScale = 0.98f),
                    )
            },
            modifier = Modifier.fillMaxSize(),
            label = "downloadContent",
        ) { visibleTasks ->
            val isTargetContent = visibleTasks == filteredTasks
            if (visibleTasks.isEmpty()) {
                EmptyPane(
                    message = if (tasks.isEmpty()) {
                        "暂无下载任务"
                    } else {
                        "未找到匹配的下载任务"
                    },
                    modifier = Modifier
                        .padding(innerPadding)
                        .then(
                            if (isTargetContent) Modifier else Modifier.clearAndSetSemantics { },
                        ),
                    icon = Icons.Default.Download,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(bottom = listBottomPadding),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                ) {
                    items(visibleTasks, key = DownloadTask::id) { task ->
                        DownloadTaskItem(
                            task = task,
                            currentSpeed = currentSpeeds[task.id] ?: 0L,
                            onStatusChange = { status -> onStatusChange(task.id, status) },
                            onOpen = { onOpen(task) },
                            onDelete = {
                                deleteTaskIds = listOf(task.id)
                                deleteLocalFile = true
                            },
                            selectionMode = multiSelectMode,
                            selected = task.id in selectedTaskIdSet,
                            onSelectionToggle = { toggleTaskSelection(task.id) },
                            enabled = isTargetContent,
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = itemEffectsSpec,
                                    placementSpec = itemSpatialSpec,
                                    fadeOutSpec = itemEffectsSpec,
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCancelAllDialog) {
        AlertDialog(
            onDismissRequest = { showCancelAllDialog = false },
            title = { Text("全部取消？") },
            text = { Text("确定要取消所有等待中、正在下载或已暂停的任务吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelAllDialog = false
                        onCancelAll()
                    },
                ) {
                    Text("取消全部任务")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelAllDialog = false }) {
                    Text("返回")
                }
            },
        )
    }

    deleteTaskIds?.let { taskIds ->
        AlertDialog(
            onDismissRequest = { deleteTaskIds = null },
            title = { Text(if (taskIds.size == 1) "删除任务" else "删除所选任务") },
            text = {
                Column {
                    Text(
                        if (taskIds.size == 1) {
                            "确定要删除此下载任务吗？"
                        } else {
                            "确定要删除所选的 ${taskIds.size} 个已结束下载任务吗？"
                        },
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteLocalFile,
                            onCheckedChange = { deleteLocalFile = it },
                        )
                        Text("同时删除本地文件")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTaskIds = null
                    taskIds.forEach { id -> onDeleteWithOption(id, deleteLocalFile) }
                    exitMultiSelect()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTaskIds = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("全部清除") },
            text = {
                Column {
                    Text("确定要清除所有已结束的下载任务吗？")
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = clearLocalFiles,
                            onCheckedChange = { clearLocalFiles = it },
                        )
                        Text("同时删除本地文件")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearTerminal(clearLocalFiles)
                }) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    currentSpeed: Long,
    onStatusChange: (DownloadStatus) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectionToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val totalBytes = task.totalBytes
    val progress = if (totalBytes != null && totalBytes > 0L) {
        (task.downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "downloadProgress",
    )
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatialFloatSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val spatialSizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selectionMode) {
                    Modifier.clickable(enabled = enabled, onClick = onSelectionToggle)
                } else {
                    Modifier
                },
            )
            .then(if (enabled) Modifier else Modifier.clearAndSetSemantics { }),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = statusContainerColor(task.status),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = task.status,
                            transitionSpec = {
                                (fadeIn(effectsSpec) + scaleIn(spatialFloatSpec, initialScale = 0.65f))
                                    .togetherWith(
                                        fadeOut(effectsSpec) +
                                            scaleOut(spatialFloatSpec, targetScale = 0.65f),
                                    )
                            },
                            label = "downloadStatusIcon",
                        ) { status ->
                            Icon(
                                imageVector = statusIcon(status),
                                contentDescription = null,
                                tint = statusColor(status),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = taskProgressText(task, progress, currentSpeed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onSelectionToggle() },
                    )
                }
            }

            AnimatedVisibility(
                visible = task.status == DownloadStatus.RUNNING,
                enter = fadeIn(effectsSpec) + expandVertically(spatialSizeSpec),
                exit = fadeOut(effectsSpec) + shrinkVertically(spatialSizeSpec),
            ) {
                if (totalBytes != null && totalBytes > 0L) {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(task.status)
                if (task.supportRange && task.status != DownloadStatus.SUCCESS) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "可续传",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!selectionMode) {
                Box(
                    modifier = Modifier.align(Alignment.End),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TaskActions(
                        status = task.status,
                        enabled = enabled,
                        onStatusChange = onStatusChange,
                        onOpen = onOpen,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    Surface(
        shape = CircleShape,
        color = statusContainerColor(status),
    ) {
        Text(
            text = statusLabel(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor(status),
        )
    }
}

@Composable
private fun TaskActions(
    status: DownloadStatus,
    enabled: Boolean,
    onStatusChange: (DownloadStatus) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (status) {
            DownloadStatus.RUNNING -> {
                FilledTonalIconButton(
                    onClick = { onStatusChange(DownloadStatus.PAUSED) },
                    enabled = enabled,
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "暂停下载")
                }
                IconButton(
                    onClick = { onStatusChange(DownloadStatus.CANCELLED) },
                    enabled = enabled,
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = "取消下载")
                }
            }
            DownloadStatus.PENDING -> IconButton(
                onClick = { onStatusChange(DownloadStatus.CANCELLED) },
                enabled = enabled,
            ) {
                Icon(Icons.Default.Cancel, contentDescription = "取消下载")
            }
            DownloadStatus.PAUSED -> {
                FilledTonalIconButton(
                    onClick = { onStatusChange(DownloadStatus.RUNNING) },
                    enabled = enabled,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "继续下载")
                }
                IconButton(
                    onClick = { onStatusChange(DownloadStatus.CANCELLED) },
                    enabled = enabled,
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = "取消下载")
                }
            }
            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                FilledTonalIconButton(
                    onClick = { onStatusChange(DownloadStatus.RUNNING) },
                    enabled = enabled,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试下载")
                }
                AnimatedDeleteIconButton(
                    onDelete = onDelete,
                    contentDescription = "删除下载任务",
                    enabled = enabled,
                )
            }
            DownloadStatus.SUCCESS -> {
                FilledTonalIconButton(onClick = onOpen, enabled = enabled) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "打开文件")
                }
                AnimatedDeleteIconButton(
                    onDelete = onDelete,
                    contentDescription = "删除下载任务和本地文件",
                    enabled = enabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedDeleteIconButton(
    onDelete: () -> Unit,
    contentDescription: String,
    enabled: Boolean,
) {
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (deleting) 0.72f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "deleteScale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (deleting) -14f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "deleteRotation",
    )

    IconButton(
        enabled = enabled && !deleting,
        onClick = {
            deleting = true
            onDelete()
            scope.launch {
                kotlinx.coroutines.delay(140L)
                deleting = false
            }
        },
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            },
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun taskProgressText(
    task: DownloadTask,
    progress: Float,
    currentSpeed: Long,
): String = when (task.status) {
    DownloadStatus.SUCCESS -> formatFileSize(task.totalBytes)
    DownloadStatus.FAILED -> task.errorMessage ?: "下载中断，可从断点重试"
    DownloadStatus.CANCELLED -> "任务已取消"
    else -> buildString {
        append(formatFileSize(task.downloadedBytes))
        append(" / ")
        append(formatFileSize(task.totalBytes))
        if (task.totalBytes != null && task.totalBytes > 0L) {
            append(" · ")
            append((progress * 100).toInt())
            append('%')
        }
        if (task.status == DownloadStatus.RUNNING) {
            append(" · ")
            append(formatFileSize(currentSpeed))
            append("/s")
        }
    }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.PENDING -> "等待中"
    DownloadStatus.RUNNING -> "下载中"
    DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.SUCCESS -> "已完成"
    DownloadStatus.FAILED -> "失败"
    DownloadStatus.CANCELLED -> "已取消"
}

internal fun filterDownloadTasks(
    tasks: List<DownloadTask>,
    query: String,
): List<DownloadTask> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return tasks
    return tasks.filter { task ->
        task.fileName.contains(normalizedQuery, ignoreCase = true) ||
            task.remotePath.contains(normalizedQuery, ignoreCase = true) ||
            task.relativePath.contains(normalizedQuery, ignoreCase = true) ||
            statusLabel(task.status).contains(normalizedQuery, ignoreCase = true)
    }
}

private fun statusIcon(status: DownloadStatus) = when (status) {
    DownloadStatus.PENDING, DownloadStatus.RUNNING -> Icons.Default.Download
    DownloadStatus.PAUSED -> Icons.Default.Pause
    DownloadStatus.SUCCESS -> Icons.Default.CheckCircle
    DownloadStatus.FAILED -> Icons.Default.Error
    DownloadStatus.CANCELLED -> Icons.Default.Cancel
}

@Composable
private fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
    DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun statusContainerColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceContainerHighest
    else -> MaterialTheme.colorScheme.secondaryContainer
}
