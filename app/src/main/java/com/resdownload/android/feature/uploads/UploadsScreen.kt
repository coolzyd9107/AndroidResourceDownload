package com.resdownload.android.feature.uploads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.resdownload.android.core.common.formatFileSize
import com.resdownload.android.core.ui.EmptyPane
import com.resdownload.android.core.ui.SearchTopAppBar
import com.resdownload.android.core.ui.SelectionAction
import com.resdownload.android.core.ui.SelectionBottomBar
import com.resdownload.android.core.ui.TaskActionIconButton
import com.resdownload.android.core.ui.taskLongPress
import com.resdownload.android.domain.model.UploadStatus
import com.resdownload.android.domain.model.UploadTask

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UploadsScreen(
    tasks: List<UploadTask>,
    currentSpeeds: Map<String, Long> = emptyMap(),
    preparingSelections: Int = 0,
    onRetry: (taskId: String) -> Unit,
    onCancel: (taskId: String) -> Unit,
    onDelete: (taskId: String) -> Unit,
    onCancelAll: () -> Unit = {},
    onClearTerminal: () -> Unit = {},
    onMultiSelectModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedTaskIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var selectedSearchTaskIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var hasObservedTasks by remember { mutableStateOf(tasks.isNotEmpty()) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
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
    val filteredTasks = filterUploadTasks(
        searchCandidates,
        if (searchActive) searchQuery else "",
    )
    val normalizedSearchQuery = searchQuery.trim()
    val selectedTaskIdSet = selectedTaskIds.toSet()
    val selectedTasks = tasks.filter { it.id in selectedTaskIdSet }
    val allVisibleTasksSelected = filteredTasks.isNotEmpty() &&
        filteredTasks.all { it.id in selectedTaskIdSet }
    val retryableTasks = selectedTasks.filter { task ->
        task.status in setOf(UploadStatus.FAILED, UploadStatus.CANCELLED)
    }
    val retryOperations = buildList {
        val scheduledTreeBatches = mutableSetOf<String>()
        retryableTasks.forEach { task ->
            if (task.isTreeUpload && task.status == UploadStatus.FAILED) {
                if (scheduledTreeBatches.add(task.batchId)) add(task)
            } else {
                add(task)
            }
        }
    }
    val cancellableSelectedTasks = selectedTasks.filter { task ->
        task.status in setOf(UploadStatus.PENDING, UploadStatus.RUNNING) &&
            !task.isDirectory &&
            !task.committing
    }
    val deletableSelectedTasks = selectedTasks.filter { task ->
        task.status in setOf(UploadStatus.SUCCESS, UploadStatus.FAILED, UploadStatus.CANCELLED)
    }
    val selectedDirectoryBatchIds = deletableSelectedTasks
        .filter(UploadTask::isDirectory)
        .mapTo(mutableSetOf(), UploadTask::batchId)
    val deletableDirectoryBatchIds = selectedDirectoryBatchIds.filterTo(mutableSetOf()) { batchId ->
        tasks.none { task ->
            task.batchId == batchId &&
                task.status in setOf(UploadStatus.PENDING, UploadStatus.RUNNING)
        }
    }
    val deletionOperations = buildList {
        deletableDirectoryBatchIds.forEach { batchId ->
            deletableSelectedTasks.firstOrNull { it.isDirectory && it.batchId == batchId }
                ?.let(::add)
        }
        addAll(deletableSelectedTasks.filter { task ->
            !task.isDirectory && task.batchId !in deletableDirectoryBatchIds
        })
    }
    val affectedDeletionTasks = tasks.filter { task ->
        task.status in setOf(UploadStatus.SUCCESS, UploadStatus.FAILED, UploadStatus.CANCELLED) &&
            (task.batchId in deletableDirectoryBatchIds ||
                (!task.isDirectory && task.id in selectedTaskIdSet))
    }
    val subtitle = if (preparingSelections > 0) {
        "正在读取所选内容"
    } else {
        taskCountLabel(tasks.size)
    }
    val hasCancellableTasks = tasks.any { task ->
        task.status in setOf(UploadStatus.PENDING, UploadStatus.RUNNING) &&
            !task.isDirectory &&
            !task.committing
    }
    val hasClearableTasks = tasks.any { task ->
        task.status in setOf(UploadStatus.FAILED, UploadStatus.CANCELLED)
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
        val visibleIds = filteredTasks.map(UploadTask::id)
        selectedTaskIds = if (visibleIds.isNotEmpty() && visibleIds.all(selectedTaskIdSet::contains)) {
            selectedTaskIds.filterNot(visibleIds::contains)
        } else {
            (selectedTaskIds + visibleIds).distinct()
        }
    }

    fun invertVisibleTasks() {
        val visibleIds = filteredTasks.map(UploadTask::id)
        selectedTaskIds = selectedTaskIds.filterNot(visibleIds::contains) +
            visibleIds.filterNot(selectedTaskIdSet::contains)
    }

    LaunchedEffect(tasks.map(UploadTask::id)) {
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
                    placeholder = "在已选上传任务中搜索",
                    closeContentDescription = "返回上传任务选择",
                    searchContentDescription = "搜索已选上传任务",
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
                                contentDescription = "退出上传任务选择",
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
                                contentDescription = "在已选上传任务中搜索",
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
                    placeholder = "搜索上传任务",
                    closeContentDescription = "关闭上传搜索",
                    searchContentDescription = "执行上传搜索",
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    showSearchAction = false,
                    onClose = {
                        searchActive = false
                        searchQuery = ""
                        selectedSearchTaskIds = emptyList()
                    },
                    subtitle = when {
                        preparingSelections > 0 -> "正在读取所选内容"
                        normalizedSearchQuery.isNotEmpty() ->
                            "${filteredTasks.size} / ${tasks.size} 个任务"
                        else -> null
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("上传") },
                    subtitle = {
                        AnimatedContent(
                            targetState = subtitle,
                            transitionSpec = {
                                (fadeIn(itemEffectsSpec) +
                                    slideInVertically(countSpatialSpec) { it / 2 })
                                    .togetherWith(
                                        fadeOut(itemEffectsSpec) +
                                            slideOutVertically(countSpatialSpec) { -it / 2 },
                                    )
                            },
                            label = "uploadCount",
                        ) { text -> Text(text) }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索上传任务")
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
                        icon = Icons.Default.Refresh,
                        label = "重试",
                        enabled = retryOperations.isNotEmpty(),
                        onClick = {
                            retryOperations.forEach { onRetry(it.id) }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Cancel,
                        label = "取消",
                        enabled = cancellableSelectedTasks.isNotEmpty(),
                        destructive = true,
                        onClick = {
                            cancellableSelectedTasks.forEach { onCancel(it.id) }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Delete,
                        label = "删除",
                        enabled = deletionOperations.isNotEmpty(),
                        destructive = true,
                        onClick = { showDeleteSelectedDialog = true },
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
                            onClick = { showClearDialog = true },
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
            label = "uploadContent",
        ) { visibleTasks ->
            val isTargetContent = visibleTasks == filteredTasks
            if (visibleTasks.isEmpty()) {
                EmptyPane(
                    message = when {
                        preparingSelections > 0 -> "正在创建上传任务"
                        tasks.isEmpty() -> "暂无上传任务"
                        else -> "未找到匹配的上传任务"
                    },
                    modifier = Modifier
                        .padding(innerPadding)
                        .then(if (isTargetContent) Modifier else Modifier.clearAndSetSemantics { }),
                    icon = Icons.Default.Upload,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("uploadTaskList"),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = listBottomPadding + 16.dp,
                    ),
                ) {
                    items(visibleTasks, key = UploadTask::id) { task ->
                        UploadTaskItem(
                            task = task,
                            currentSpeed = currentSpeeds[task.id] ?: 0L,
                            onRetry = { onRetry(task.id) },
                            onCancel = { onCancel(task.id) },
                            onDelete = { onDelete(task.id) },
                            selectionMode = multiSelectMode,
                            selected = task.id in selectedTaskIdSet,
                            onSelectionToggle = { toggleTaskSelection(task.id) },
                            onLongSelect = {
                                multiSelectMode = true
                                selectedTaskIds = (selectedTaskIds + task.id).distinct()
                            },
                            enabled = isTargetContent,
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = itemEffectsSpec,
                                    placementSpec = itemSpatialSpec,
                                    fadeOutSpec = itemEffectsSpec,
                                )
                                .padding(horizontal = 12.dp, vertical = 3.dp),
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
            text = {
                Text("确定要取消所有可取消的等待中或正在上传的文件任务吗？文件夹创建任务不会取消。")
            },
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

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("全部清除？") },
            text = { Text("确定要清除所有失败或已取消的上传任务吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearTerminal()
                    },
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("返回")
                }
            },
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("删除所选任务？") },
            text = {
                Text("此操作将删除 ${affectedDeletionTasks.size} 个已结束上传任务，确定继续吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSelectedDialog = false
                        deletionOperations.forEach { onDelete(it.id) }
                        exitMultiSelect()
                    },
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun UploadTaskItem(
    task: UploadTask,
    currentSpeed: Long,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectionToggle: () -> Unit,
    onLongSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val totalBytes = task.totalBytes
    val progress = if (totalBytes != null && totalBytes > 0L) {
        (task.uploadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "uploadProgress",
    )
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatialFloatSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selectionMode) {
                    Modifier.clickable(
                        enabled = enabled,
                        onClickLabel = "切换任务选择",
                        onClick = onSelectionToggle,
                    )
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .taskLongPress(
                        enabled = enabled && !selectionMode,
                        label = "选择上传任务",
                        onLongPress = onLongSelect,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("uploadStatusIcon-${task.id}"),
                        shape = MaterialTheme.shapes.small,
                        color = statusContainerColor(task.status),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = task.status,
                                transitionSpec = {
                                    (fadeIn(effectsSpec) +
                                        scaleIn(spatialFloatSpec, initialScale = 0.65f))
                                        .togetherWith(
                                            fadeOut(effectsSpec) +
                                                scaleOut(spatialFloatSpec, targetScale = 0.65f),
                                        )
                                },
                                label = "uploadStatusIcon",
                            ) { status ->
                                Icon(
                                    imageVector = statusIcon(status, task.isDirectory),
                                    contentDescription = null,
                                    tint = statusColor(status),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (task.relativePath != task.fileName) {
                            Text(
                                text = task.relativePath,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = taskProgressText(task, progress, currentSpeed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = task.status == UploadStatus.RUNNING && !task.committing,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            fadeIn(effectsSpec).togetherWith(fadeOut(effectsSpec))
                        },
                        label = "uploadProgressVisibility",
                    ) { visible ->
                        if (visible && !task.isDirectory && totalBytes != null && totalBytes > 0L) {
                            LinearWavyProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (visible) {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            Spacer(Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .taskLongPress(
                            enabled = enabled && !selectionMode,
                            label = "选择上传任务",
                            onLongPress = onLongSelect,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    StatusBadge(
                        task = task,
                        modifier = Modifier.testTag("uploadStatusBadge-${task.id}"),
                    )
                }
                if (!selectionMode) {
                    TaskActions(
                        task = task,
                        enabled = enabled,
                        onRetry = onRetry,
                        onCancel = onCancel,
                        onDelete = onDelete,
                        onLongSelect = onLongSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    task: UploadTask,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = statusContainerColor(task.status),
    ) {
        Text(
            text = statusLabel(task),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = statusColor(task.status),
        )
    }
}

@Composable
private fun TaskActions(
    task: UploadTask,
    enabled: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLongSelect: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (task.status) {
            UploadStatus.PENDING, UploadStatus.RUNNING -> {
                if (!task.isDirectory && !task.committing) {
                    TaskActionIconButton(
                        onClick = onCancel,
                        onLongPress = onLongSelect,
                        clickLabel = "取消上传",
                        longClickLabel = "选择上传任务",
                        enabled = enabled,
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = "取消上传")
                    }
                }
            }
            UploadStatus.FAILED, UploadStatus.CANCELLED -> {
                TaskActionIconButton(
                    onClick = onRetry,
                    onLongPress = onLongSelect,
                    clickLabel = "重试上传",
                    longClickLabel = "选择上传任务",
                    enabled = enabled,
                    tonal = true,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试上传")
                }
                AnimatedDeleteIconButton(
                    onDelete = onDelete,
                    contentDescription = "删除上传任务",
                    enabled = enabled,
                    onLongSelect = onLongSelect,
                )
            }
            UploadStatus.SUCCESS -> AnimatedDeleteIconButton(
                onDelete = onDelete,
                contentDescription = "删除上传记录",
                enabled = enabled,
                onLongSelect = onLongSelect,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedDeleteIconButton(
    onDelete: () -> Unit,
    contentDescription: String,
    enabled: Boolean,
    onLongSelect: () -> Unit,
) {
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (deleting) 0.72f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "uploadDeleteScale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (deleting) -14f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "uploadDeleteRotation",
    )
    TaskActionIconButton(
        enabled = enabled && !deleting,
        onLongPress = onLongSelect,
        clickLabel = contentDescription,
        longClickLabel = "选择上传任务",
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

private fun taskProgressText(task: UploadTask, progress: Float, currentSpeed: Long): String =
    when (task.status) {
        UploadStatus.SUCCESS -> if (task.isDirectory) "文件夹已创建" else formatFileSize(task.totalBytes)
        UploadStatus.FAILED -> task.errorMessage ?: "上传失败，可重试"
        UploadStatus.CANCELLED -> "任务已取消，重试将从头上传"
        UploadStatus.PENDING -> if (task.isDirectory) {
            "等待创建文件夹"
        } else {
            "${formatFileSize(task.uploadedBytes)} / ${formatFileSize(task.totalBytes)}"
        }
        UploadStatus.RUNNING -> when {
            task.errorMessage != null -> task.errorMessage
            task.committing -> "正在提交到云端"
            task.isDirectory -> "正在创建文件夹"
            else -> buildString {
                append(formatFileSize(task.uploadedBytes))
                append(" / ")
                append(formatFileSize(task.totalBytes))
                if (task.totalBytes != null && task.totalBytes > 0L) {
                    append(" · ")
                    append((progress * 100).toInt())
                    append('%')
                }
                append(" · ")
                append(formatFileSize(currentSpeed))
                append("/s")
            }
        }
    }

private fun statusLabel(task: UploadTask): String = when (task.status) {
    UploadStatus.PENDING -> "等待中"
    UploadStatus.RUNNING -> when {
        task.errorMessage != null -> "等待确认"
        task.committing -> "提交中"
        else -> "上传中"
    }
    UploadStatus.SUCCESS -> "已完成"
    UploadStatus.FAILED -> "失败"
    UploadStatus.CANCELLED -> "已取消"
}

internal fun filterUploadTasks(
    tasks: List<UploadTask>,
    query: String,
): List<UploadTask> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return tasks
    return tasks.filter { task ->
        task.fileName.contains(normalizedQuery, ignoreCase = true) ||
            task.relativePath.contains(normalizedQuery, ignoreCase = true) ||
            task.remotePath.contains(normalizedQuery, ignoreCase = true) ||
            statusLabel(task).contains(normalizedQuery, ignoreCase = true)
    }
}

private fun statusIcon(status: UploadStatus, isDirectory: Boolean) = when (status) {
    UploadStatus.PENDING, UploadStatus.RUNNING -> if (isDirectory) Icons.Default.Folder else Icons.Default.Upload
    UploadStatus.SUCCESS -> Icons.Default.CheckCircle
    UploadStatus.FAILED -> Icons.Default.Error
    UploadStatus.CANCELLED -> Icons.Default.Cancel
}

@Composable
private fun statusColor(status: UploadStatus): Color = when (status) {
    UploadStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
    UploadStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
    UploadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun statusContainerColor(status: UploadStatus): Color = when (status) {
    UploadStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
    UploadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    UploadStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceContainerHighest
    else -> MaterialTheme.colorScheme.secondaryContainer
}

private fun taskCountLabel(count: Int): String = if (count == 0) "暂无任务" else "$count 个任务"
