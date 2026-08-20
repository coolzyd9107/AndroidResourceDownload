package link.mczihan.androidResourceDownload.feature.downloads

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    tasks: List<DownloadTask>,
    currentSpeeds: Map<String, Long> = emptyMap(),
    onStatusChange: (taskId: String, status: DownloadStatus) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onDelete: (taskId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val itemSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val contentSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val countSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()

    Scaffold(
        modifier = modifier,
        topBar = {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
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
            label = "downloadContent",
        ) { visibleTasks ->
            val isTargetContent = visibleTasks == tasks
            if (visibleTasks.isEmpty()) {
                EmptyPane(
                    message = "暂无下载任务",
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
                        .padding(innerPadding),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                ) {
                    items(visibleTasks, key = DownloadTask::id) { task ->
                        DownloadTaskItem(
                            task = task,
                            currentSpeed = currentSpeeds[task.id] ?: 0L,
                            onStatusChange = { status -> onStatusChange(task.id, status) },
                            onOpen = { onOpen(task) },
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    currentSpeed: Long,
    onStatusChange: (DownloadStatus) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
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
