package link.mczihan.androidResourceDownload.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    tasks: List<DownloadTask>,
    onStatusChange: (taskId: String, status: DownloadStatus) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onDelete: (taskId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("下载") }) },
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            EmptyPane(
                message = "暂无下载任务",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(tasks, key = DownloadTask::id) { task ->
                    DownloadTaskItem(
                        task = task,
                        onStatusChange = { status -> onStatusChange(task.id, status) },
                        onOpen = { onOpen(task) },
                        onDelete = { onDelete(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    onStatusChange: (DownloadStatus) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val totalBytes = task.totalBytes
    val progress = if (totalBytes != null && totalBytes > 0L) {
        (task.downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    ListItem(
        headlineContent = {
            Text(
                text = task.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = taskProgressText(task),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.status == DownloadStatus.RUNNING) {
                    if (totalBytes != null && totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = statusLabel(task.status),
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor(task.status),
                    )
                    if (task.supportRange && task.status != DownloadStatus.SUCCESS) {
                        Text(
                            text = "可续传",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = statusIcon(task.status),
                contentDescription = null,
                tint = statusColor(task.status),
            )
        },
        trailingContent = {
            Row {
                when (task.status) {
                    DownloadStatus.RUNNING -> {
                        IconButton(onClick = { onStatusChange(DownloadStatus.PAUSED) }) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停下载")
                        }
                        IconButton(onClick = { onStatusChange(DownloadStatus.CANCELLED) }) {
                            Icon(Icons.Default.Cancel, contentDescription = "取消下载")
                        }
                    }
                    DownloadStatus.PENDING -> IconButton(
                        onClick = { onStatusChange(DownloadStatus.CANCELLED) },
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = "取消下载")
                    }
                    DownloadStatus.PAUSED -> {
                        FilledTonalIconButton(onClick = { onStatusChange(DownloadStatus.RUNNING) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "继续下载")
                        }
                        IconButton(onClick = { onStatusChange(DownloadStatus.CANCELLED) }) {
                            Icon(Icons.Default.Cancel, contentDescription = "取消下载")
                        }
                    }
                    DownloadStatus.FAILED -> {
                        FilledTonalIconButton(onClick = { onStatusChange(DownloadStatus.RUNNING) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重试下载")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "删除下载任务")
                        }
                    }
                    DownloadStatus.CANCELLED -> {
                        FilledTonalIconButton(onClick = { onStatusChange(DownloadStatus.RUNNING) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重试下载")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "删除下载任务")
                        }
                    }
                    DownloadStatus.SUCCESS -> {
                        IconButton(onClick = onOpen) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "打开文件")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "删除下载任务和本地文件")
                        }
                    }
                }
            }
        },
    )
}

private fun taskProgressText(task: DownloadTask): String = when (task.status) {
    DownloadStatus.SUCCESS -> formatFileSize(task.totalBytes)
    DownloadStatus.FAILED -> task.errorMessage ?: "下载中断，可从断点重试"
    DownloadStatus.CANCELLED -> "任务已取消"
    else -> "${formatFileSize(task.downloadedBytes)} / ${formatFileSize(task.totalBytes)}"
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
    DownloadStatus.PENDING -> Icons.Default.Download
    DownloadStatus.RUNNING -> Icons.Default.Download
    DownloadStatus.PAUSED -> Icons.Default.Pause
    DownloadStatus.SUCCESS -> Icons.Default.CheckCircle
    DownloadStatus.FAILED -> Icons.Default.Error
    DownloadStatus.CANCELLED -> Icons.Default.Cancel
}

@Composable
private fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.secondary
}
