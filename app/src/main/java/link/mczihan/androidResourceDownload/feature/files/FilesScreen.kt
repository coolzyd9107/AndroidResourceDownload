package link.mczihan.androidResourceDownload.feature.files

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val filePaneState = if (viewModel == null) {
        when (val contentState = state) {
            ContentState.Loading -> FilePaneState(activePath, FilePaneContent.Loading)
            is ContentState.Empty -> FilePaneState(
                activePath,
                FilePaneContent.Empty(contentState.message),
            )
            is ContentState.Error -> FilePaneState(
                activePath,
                FilePaneContent.Error(contentState.message),
            )
            is ContentState.Success -> FilePaneState(
                activePath,
                FilePaneContent.Files(contentState.value),
            )
        }
    } else {
        when (val contentState = realState) {
            null, is FilesUiState.Loading -> FilePaneState(activePath, FilePaneContent.Loading)
            is FilesUiState.Empty -> FilePaneState(
                contentState.path,
                FilePaneContent.Empty("此目录为空"),
            )
            is FilesUiState.Error -> FilePaneState(
                contentState.path,
                FilePaneContent.Error(contentState.message),
            )
            is FilesUiState.Success -> FilePaneState(
                contentState.path,
                FilePaneContent.Files(contentState.files),
            )
        }
    }
    var folderHistory by remember { mutableStateOf<List<FilePaneState>>(emptyList()) }
    val predictiveBackProgress = remember { Animatable(0f) }
    var predictiveBackInProgress by remember { mutableStateOf(false) }
    var predictiveBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var screenWidthPx by remember { mutableIntStateOf(0) }
    val predictiveSettleScope = rememberCoroutineScope()
    var predictiveSettleJob by remember { mutableStateOf<Job?>(null) }
    val parentPath = activePath.decodedSegments
        .takeIf { it.isNotEmpty() }
        ?.dropLast(1)
        ?.let { WebDavPath.fromDecodedSegments(it) }
    val parentPreviewState = parentPath?.let { parent ->
        folderHistory.lastOrNull()?.takeIf { it.path == parent }
            ?: if (viewModel == null) {
                demoFilePaneState(parent)
            } else {
                FilePaneState(parent, FilePaneContent.Loading)
            }
    }
    val folderSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val folderScaleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val folderEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val navigateUp = {
        folderHistory = folderHistory.dropLast(1)
        if (viewModel == null) {
            currentPath = WebDavPath.fromDecodedSegments(activePath.decodedSegments.dropLast(1)).toString()
        } else {
            viewModel.navigateUp()
        }
    }

    PredictiveBackHandler(enabled = selectedFile == null && !activePath.isRoot) { events ->
        predictiveSettleJob?.cancel()
        predictiveBackProgress.stop()
        predictiveBackInProgress = true
        try {
            events.collect { event ->
                predictiveBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
                predictiveBackEdge = event.swipeEdge
            }
            navigateUp()
            predictiveBackProgress.snapTo(0f)
            predictiveBackInProgress = false
        } catch (error: CancellationException) {
            predictiveSettleJob = predictiveSettleScope.launch {
                predictiveBackProgress.animateTo(0f, folderScaleSpec)
                predictiveBackInProgress = false
            }
            throw error
        } catch (error: Throwable) {
            predictiveBackProgress.snapTo(0f)
            predictiveBackInProgress = false
            throw error
        }
    }

    val predictiveDirection = if (predictiveBackEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    val predictiveShape = RoundedCornerShape(32.dp)
    val predictiveElevation = with(LocalDensity.current) { 12.dp.toPx() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { screenWidthPx = it.width },
        ) {
            if (predictiveBackInProgress && parentPreviewState != null) {
                FolderBackPreview(
                    pane = parentPreviewState,
                    progress = predictiveBackProgress.value,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = predictiveBackProgress.value
                        translationX = predictiveDirection * screenWidthPx * 0.16f * progress
                        scaleX = 1f - (0.08f * progress)
                        scaleY = 1f - (0.08f * progress)
                        shadowElevation = predictiveElevation * progress
                        shape = predictiveShape
                        clip = predictiveBackInProgress
                    },
                topBar = {
                    TopAppBar(
                        title = { Text("文件") },
                        subtitle = {
                            AnimatedContent(
                                targetState = displayedPath,
                                transitionSpec = {
                                    (fadeIn(folderEffectsSpec) +
                                        slideInVertically(folderSpatialSpec) { it / 2 })
                                        .togetherWith(
                                            fadeOut(folderEffectsSpec) +
                                                slideOutVertically(folderSpatialSpec) { -it / 2 },
                                        )
                                },
                                label = "filePath",
                            ) { path ->
                                Text(
                                    text = path,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            AnimatedVisibility(
                                visible = !activePath.isRoot,
                                enter = fadeIn(folderEffectsSpec) + scaleIn(folderScaleSpec),
                                exit = fadeOut(folderEffectsSpec) + scaleOut(folderScaleSpec),
                            ) {
                                IconButton(onClick = navigateUp) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回上一级",
                                    )
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
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
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
                                icon = {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                },
                                onClick = { onMessage("目录已创建") },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                AnimatedContent(
                    targetState = filePaneState,
                    contentKey = { pane -> pane.path to pane.content::class },
                    transitionSpec = {
                        if (initialState.path != targetState.path) {
                            val direction = if (
                                targetState.path.decodedSegments.size >
                                initialState.path.decodedSegments.size
                            ) {
                                1
                            } else {
                                -1
                            }
                            (
                                fadeIn(folderEffectsSpec) +
                                    slideInHorizontally(folderSpatialSpec) { width ->
                                        direction * width / 4
                                    } +
                                    scaleIn(folderScaleSpec, initialScale = 0.98f)
                                ).togetherWith(
                                fadeOut(folderEffectsSpec) +
                                    slideOutHorizontally(folderSpatialSpec) { width ->
                                        -direction * width / 8
                                    },
                            ).using(SizeTransform(clip = true))
                        } else {
                            fadeIn(folderEffectsSpec).togetherWith(fadeOut(folderEffectsSpec))
                        }
                    },
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    label = "folderContent",
                ) { pane ->
                    val isTargetContent = pane == filePaneState
                    val contentModifier = if (isTargetContent) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { }
                    }
                    when (val content = pane.content) {
                        FilePaneContent.Loading -> LoadingPane(contentModifier)
                        is FilePaneContent.Empty -> EmptyPane(
                            message = content.message,
                            modifier = contentModifier,
                        )
                        is FilePaneContent.Error -> ErrorPane(
                            message = content.message,
                            modifier = contentModifier,
                            enabled = isTargetContent,
                            onRetry = {
                                if (viewModel == null) {
                                    state = fileStateForPath(currentPath)
                                } else {
                                    viewModel.retry()
                                }
                            },
                        )
                        is FilePaneContent.Files -> FileList(
                            files = content.value,
                            modifier = contentModifier,
                            enabled = isTargetContent,
                            onFileClick = { file ->
                                if (file.isDirectory) {
                                    folderHistory = (folderHistory + filePaneState).takeLast(12)
                                    if (viewModel == null) {
                                        currentPath = file.path
                                    } else {
                                        viewModel.openDirectory(WebDavPath.parseDecoded(file.path))
                                    }
                                } else {
                                    selectedFile = file
                                }
                            },
                        )
                    }
                }
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

private data class FilePaneState(
    val path: WebDavPath,
    val content: FilePaneContent,
)

private sealed interface FilePaneContent {
    data object Loading : FilePaneContent
    data class Empty(val message: String) : FilePaneContent
    data class Error(val message: String) : FilePaneContent
    data class Files(val value: List<FileNode>) : FilePaneContent
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderBackPreview(
    pane: FilePaneState,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .graphicsLayer {
                val scale = 0.94f + (0.06f * progress)
                scaleX = scale
                scaleY = scale
                alpha = 0.6f + (0.4f * progress)
            }
            .clearAndSetSemantics { },
        topBar = {
            TopAppBar(
                title = { Text("文件") },
                subtitle = {
                    Text(
                        text = pane.path.toString(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (!pane.path.isRoot) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        when (val content = pane.content) {
            FilePaneContent.Loading -> LoadingPane(Modifier.padding(innerPadding))
            is FilePaneContent.Empty -> EmptyPane(
                message = content.message,
                modifier = Modifier.padding(innerPadding),
            )
            is FilePaneContent.Error -> ErrorPane(
                message = content.message,
                onRetry = {},
                enabled = false,
                modifier = Modifier.padding(innerPadding),
            )
            is FilePaneContent.Files -> FileList(
                files = content.value,
                onFileClick = {},
                enabled = false,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private fun demoFilePaneState(path: WebDavPath): FilePaneState =
    when (val contentState = fileStateForPath(path.toString())) {
        ContentState.Loading -> FilePaneState(path, FilePaneContent.Loading)
        is ContentState.Empty -> FilePaneState(
            path,
            FilePaneContent.Empty(contentState.message),
        )
        is ContentState.Error -> FilePaneState(
            path,
            FilePaneContent.Error(contentState.message),
        )
        is ContentState.Success -> FilePaneState(
            path,
            FilePaneContent.Files(contentState.value),
        )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileList(
    files: List<FileNode>,
    onFileClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val itemEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val itemSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = if (file.isDirectory) MaterialTheme.shapes.medium else CircleShape,
                        color = if (file.isDirectory) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (file.isDirectory) {
                                    Icons.Default.Folder
                                } else {
                                    Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = if (file.isDirectory) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
                trailingContent = if (file.isDirectory) {
                    {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = itemEffectsSpec,
                        placementSpec = itemSpatialSpec,
                        fadeOutSpec = itemEffectsSpec,
                    )
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = enabled) { onFileClick(file) },
            )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
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
