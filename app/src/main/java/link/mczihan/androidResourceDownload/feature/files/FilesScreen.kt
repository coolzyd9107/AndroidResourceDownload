package link.mczihan.androidResourceDownload.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.core.common.formatDate
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.ui.ContentState
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.core.ui.ErrorPane
import link.mczihan.androidResourceDownload.core.ui.LoadingPane
import link.mczihan.androidResourceDownload.data.mock.mockFilesForPath
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    role: Role,
    onProfile: () -> Unit,
    onDownload: (FileNode) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = if (BuildConfig.DEMO_MODE) null else hiltViewModel<FilesViewModel>()
    val realState = viewModel?.state?.collectAsStateWithLifecycle()?.value
    var currentPath by rememberSaveable { mutableStateOf("/") }
    var state by remember(currentPath) {
        mutableStateOf(fileStateForPath(currentPath))
    }
    var selectedFile by remember { mutableStateOf<FileNode?>(null) }
    val activePath = realState?.path ?: WebDavPath.parseDecoded(currentPath)
    val displayedPath = activePath.toString()
    val navigateUp = {
        if (viewModel == null) {
            currentPath = WebDavPath.fromDecodedSegments(activePath.decodedSegments.dropLast(1)).toString()
        } else {
            viewModel.navigateUp()
        }
    }

    BackHandler(enabled = selectedFile != null) {
        selectedFile = null
    }
    BackHandler(enabled = selectedFile == null && !activePath.isRoot, onBack = navigateUp)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("文件")
                        Text(
                            text = displayedPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (!activePath.isRoot) {
                        IconButton(onClick = navigateUp) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回上一级")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (viewModel == null) {
                            state = fileStateForPath(currentPath)
                        } else {
                            viewModel.retry()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新文件列表")
                    }
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Default.Person, contentDescription = "个人中心")
                    }
                },
            )
        },
        floatingActionButton = {
            if (BuildConfig.DEMO_MODE && role == Role.ADMIN) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExtendedFloatingActionButton(
                        text = { Text("上传") },
                        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        onClick = { onMessage("已创建上传任务") },
                    )
                    ExtendedFloatingActionButton(
                        text = { Text("新建目录") },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        onClick = { onMessage("目录已创建") },
                    )
                }
            }
        },
    ) { innerPadding ->
        if (viewModel == null) {
            when (val contentState = state) {
                ContentState.Loading -> LoadingPane(Modifier.padding(innerPadding))
                is ContentState.Empty -> EmptyPane(
                    message = contentState.message,
                    modifier = Modifier.padding(innerPadding),
                )
                is ContentState.Error -> ErrorPane(
                    message = contentState.message,
                    onRetry = { state = fileStateForPath(currentPath) },
                    modifier = Modifier.padding(innerPadding),
                )
                is ContentState.Success -> FileList(
                    files = contentState.value,
                    onFileClick = { file ->
                        if (file.isDirectory) currentPath = file.path else selectedFile = file
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        } else {
            when (val contentState = realState) {
                null, is FilesUiState.Loading -> LoadingPane(Modifier.padding(innerPadding))
                is FilesUiState.Empty -> EmptyPane(
                    message = "此目录为空",
                    modifier = Modifier.padding(innerPadding),
                )
                is FilesUiState.Error -> ErrorPane(
                    message = contentState.message,
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(innerPadding),
                )
                is FilesUiState.Success -> FileList(
                    files = contentState.files,
                    onFileClick = { file ->
                        if (file.isDirectory) {
                            viewModel.openDirectory(WebDavPath.parseDecoded(file.path))
                        } else {
                            selectedFile = file
                        }
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }

    selectedFile?.let { file ->
        FileDetailsSheet(
            file = file,
            isAdmin = BuildConfig.DEMO_MODE && role == Role.ADMIN,
            onDismiss = { selectedFile = null },
            onDownload = {
                onDownload(file)
                selectedFile = null
            },
            onRename = { onMessage("已重命名 ${file.name}") },
            onDelete = {
                selectedFile = null
                onMessage("已删除 ${file.name}")
            },
        )
    }
}

private fun fileStateForPath(path: String): ContentState<List<FileNode>> =
    when (val files = mockFilesForPath(path)) {
        null -> ContentState.Error("暂时无法加载此目录")
        else -> if (files.isEmpty()) {
            ContentState.Empty("此目录为空")
        } else {
            ContentState.Success(files)
        }
    }

@Composable
private fun FileList(
    files: List<FileNode>,
    onFileClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        items(files, key = { it.path }) { file ->
            ListItem(
                headlineContent = {
                    Text(
                        text = file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = if (file.isDirectory) {
                            "文件夹 · ${formatDate(file.lastModified)}"
                        } else {
                            "${formatFileSize(file.size)} · ${formatDate(file.lastModified)}"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = if (file.isDirectory) {
                            Icons.Default.Folder
                        } else {
                            Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = if (file.isDirectory) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                modifier = Modifier.clickable { onFileClick(file) },
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDetailsSheet(
    file: FileNode,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = file.mimeType ?: "未知类型",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DetailLine("大小", formatFileSize(file.size))
            DetailLine("修改时间", formatDate(file.lastModified))
            DetailLine("路径", file.path)
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("下载")
            }
            if (isAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onRename,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("重命名")
                    }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
