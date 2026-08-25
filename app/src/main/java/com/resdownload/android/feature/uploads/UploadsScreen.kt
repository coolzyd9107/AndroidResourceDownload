package com.resdownload.android.feature.uploads

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    modifier: Modifier = Modifier,
) {
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val itemEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val itemSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val contentSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val countSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val subtitle = if (preparingSelections > 0) "正在读取所选内容" else taskCountLabel(tasks.size)
    val hasCancellableTasks = tasks.any { task ->
        task.status in setOf(UploadStatus.PENDING, UploadStatus.RUNNING) &&
            !task.isDirectory &&
            !task.committing
    }
    val hasClearableTasks = tasks.any { task ->
        task.status in setOf(UploadStatus.FAILED, UploadStatus.CANCELLED)
    }
    val listBottomPadding = when {
        hasCancellableTasks && hasClearableTasks -> 152.dp
        hasCancellableTasks || hasClearableTasks -> 84.dp
        else -> 16.dp
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("上传") },
                subtitle = {
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = {
                            (fadeIn(itemEffectsSpec) + slideInVertically(countSpatialSpec) { it / 2 })
                                .togetherWith(
                                    fadeOut(itemEffectsSpec) +
                                        slideOutVertically(countSpatialSpec) { -it / 2 },
                                )
                        },
                        label = "uploadCount",
                    ) { text -> Text(text) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
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
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = tasks,
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
            val isTargetContent = visibleTasks == tasks
            if (visibleTasks.isEmpty()) {
                EmptyPane(
                    message = if (preparingSelections > 0) "正在创建上传任务" else "暂无上传任务",
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
                        .padding(bottom = listBottomPadding),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                ) {
                    items(visibleTasks, key = UploadTask::id) { task ->
                        UploadTaskItem(
                            task = task,
                            currentSpeed = currentSpeeds[task.id] ?: 0L,
                            onRetry = { onRetry(task.id) },
                            onCancel = { onCancel(task.id) },
                            onDelete = { onDelete(task.id) },
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadTaskItem(
    task: UploadTask,
    currentSpeed: Long,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
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
    val spatialSizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier else Modifier.clearAndSetSemantics { }),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleMedium,
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
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(
                visible = task.status == UploadStatus.RUNNING && !task.committing,
                enter = fadeIn(effectsSpec) + expandVertically(spatialSizeSpec),
                exit = fadeOut(effectsSpec) + shrinkVertically(spatialSizeSpec),
            ) {
                if (!task.isDirectory && totalBytes != null && totalBytes > 0L) {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            StatusBadge(task)
            Box(
                modifier = Modifier.align(Alignment.End),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TaskActions(
                    task = task,
                    enabled = enabled,
                    onRetry = onRetry,
                    onCancel = onCancel,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(task: UploadTask) {
    Surface(shape = CircleShape, color = statusContainerColor(task.status)) {
        Text(
            text = statusLabel(task),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
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
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (task.status) {
            UploadStatus.PENDING, UploadStatus.RUNNING -> {
                if (!task.isDirectory && !task.committing) {
                    IconButton(onClick = onCancel, enabled = enabled) {
                        Icon(Icons.Default.Cancel, contentDescription = "取消上传")
                    }
                }
            }
            UploadStatus.FAILED, UploadStatus.CANCELLED -> {
                FilledTonalIconButton(onClick = onRetry, enabled = enabled) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试上传")
                }
                AnimatedDeleteIconButton(
                    onDelete = onDelete,
                    contentDescription = "删除上传任务",
                    enabled = enabled,
                )
            }
            UploadStatus.SUCCESS -> AnimatedDeleteIconButton(
                onDelete = onDelete,
                contentDescription = "删除上传记录",
                enabled = enabled,
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
