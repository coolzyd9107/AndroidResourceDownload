package link.mczihan.androidResourceDownload.feature.files

import androidx.activity.BackEventCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.core.common.formatDate
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.ui.ContentState
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.core.ui.ErrorPane
import link.mczihan.androidResourceDownload.core.ui.LoadingPane
import link.mczihan.androidResourceDownload.data.mock.mockFilesForPath
import link.mczihan.androidResourceDownload.data.mock.mockPreviewForFile
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.FilePreviewContent
import link.mczihan.androidResourceDownload.domain.model.FilePreviewFormat
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.previewFormat
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.domain.webdav.strongEntityTagOrNull

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
    var transferRequest by remember { mutableStateOf<TransferRequest?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var showCreateDirectoryDialog by remember { mutableStateOf(false) }
    var demoDestinationPath by remember { mutableStateOf(WebDavPath.root()) }
    var demoPreviewState by remember { mutableStateOf<FilePreviewUiState>(FilePreviewUiState.Idle) }
    val mutationState = viewModel?.mutationState?.collectAsStateWithLifecycle()?.value
        ?: FileMutationState.Idle
    val realDirectoryPickerState = viewModel?.directoryPickerState
        ?.collectAsStateWithLifecycle()
        ?.value
        ?: DirectoryPickerState.Idle
    val previewState = viewModel?.previewState?.collectAsStateWithLifecycle()?.value
        ?: demoPreviewState
    val isAdmin = role == Role.ADMIN
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        when {
            uri == null -> Unit
            !isAdmin -> Unit
            viewModel != null -> viewModel.prepareUpload(uri)
            else -> onMessage("演示模式不执行云端文件操作")
        }
    }
    LaunchedEffect(isAdmin) {
        if (!isAdmin) {
            transferRequest = null
            renameTarget = null
            deleteTarget = null
            showCreateDirectoryDialog = false
            viewModel?.dismissDestinationPicker()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel?.messages?.collect { message ->
            selectedFile = null
            transferRequest = null
            renameTarget = null
            deleteTarget = null
            onMessage(message)
        }
    }
    val activePath = realState?.path ?: WebDavPath.parseDecoded(currentPath)
    val directoryPickerState = if (viewModel == null && transferRequest != null) {
        demoDirectoryPickerState(demoDestinationPath)
    } else {
        realDirectoryPickerState
    }
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
            is FilesUiState.Success -> contentState.files
                .filter { isAdmin || !it.isUploadTemporary }
                .let { visibleFiles ->
                    FilePaneState(
                        contentState.path,
                        if (visibleFiles.isEmpty()) {
                            FilePaneContent.Empty("此目录为空")
                        } else {
                            FilePaneContent.Files(visibleFiles)
                        },
                    )
                }
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

    PredictiveBackHandler(
        enabled = selectedFile == null &&
            transferRequest == null &&
            renameTarget == null &&
            deleteTarget == null &&
            !showCreateDirectoryDialog &&
            previewState == FilePreviewUiState.Idle &&
            mutationState == FileMutationState.Idle &&
            !activePath.isRoot,
    ) { events ->
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
                    if (isAdmin && mutationState == FileMutationState.Idle) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ExtendedFloatingActionButton(
                                text = { Text("新建文件夹") },
                                icon = {
                                    Icon(
                                        Icons.Default.CreateNewFolder,
                                        contentDescription = "新建文件夹",
                                    )
                                },
                                onClick = { showCreateDirectoryDialog = true },
                            )
                            ExtendedFloatingActionButton(
                                text = { Text("上传") },
                                icon = {
                                    Icon(Icons.Default.UploadFile, contentDescription = "上传文件")
                                },
                                onClick = { uploadLauncher.launch(arrayOf("*/*")) },
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
                            isAdmin = isAdmin,
                            onManage = { if (isAdmin) selectedFile = it },
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
            isAdmin = isAdmin,
            onPreview = file.previewFormat()?.let {
                {
                    selectedFile = null
                    if (viewModel == null) {
                        demoPreviewState = mockPreviewForFile(file)?.let { content ->
                            FilePreviewUiState.Content(file, content)
                        } ?: FilePreviewUiState.Error(file, "演示文件没有可用的预览内容")
                    } else {
                        viewModel.preview(file)
                    }
                }
            },
            onDismiss = { selectedFile = null },
            onDownload = {
                onDownload(file)
                selectedFile = null
            },
            onRename = {
                selectedFile = null
                renameTarget = file
            },
            onMove = {
                selectedFile = null
                transferRequest = TransferRequest(file, TransferType.MOVE)
                if (viewModel == null) {
                    demoDestinationPath = activePath
                } else {
                    viewModel.openDestinationPicker(activePath)
                }
            },
            onCopy = {
                selectedFile = null
                transferRequest = TransferRequest(file, TransferType.COPY)
                if (viewModel == null) {
                    demoDestinationPath = activePath
                } else {
                    viewModel.openDestinationPicker(activePath)
                }
            },
            onDelete = {
                if (viewModel == null) {
                    selectedFile = null
                    onMessage("演示模式不执行云端文件操作")
                } else {
                    deleteTarget = file
                }
            },
        )
    }

    renameTarget?.takeIf { isAdmin }?.let { file ->
        RenameResourceDialog(
            file = file,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                if (viewModel == null) {
                    onMessage("演示模式不执行云端文件操作")
                } else {
                    viewModel.rename(
                        source = WebDavPath.parseDecoded(file.path),
                        sourceIsDirectory = file.isDirectory,
                        newName = newName,
                        sourceEtag = file.etag,
                    )
                }
                renameTarget = null
            },
        )
    }

    transferRequest?.takeIf { isAdmin }?.let { request ->
        DestinationDirectoryDialog(
            request = request,
            state = directoryPickerState,
            onOpenDirectory = { path ->
                if (viewModel == null) {
                    demoDestinationPath = path
                } else {
                    viewModel.openDestinationDirectory(path)
                }
            },
            onNavigateUp = {
                if (viewModel == null) {
                    if (!demoDestinationPath.isRoot) {
                        demoDestinationPath = WebDavPath.fromDecodedSegments(
                            demoDestinationPath.decodedSegments.dropLast(1),
                        )
                    }
                } else {
                    viewModel.navigateDestinationUp()
                }
            },
            onRetry = {
                if (viewModel == null) Unit else viewModel.retryDestinationPicker()
            },
            onDismiss = {
                transferRequest = null
                viewModel?.dismissDestinationPicker()
            },
            onConfirm = { directory ->
                val source = WebDavPath.parseDecoded(request.file.path)
                if (viewModel == null) {
                    onMessage("演示模式不执行云端文件操作")
                } else if (request.type == TransferType.MOVE) {
                    viewModel.move(
                        source = source,
                        sourceIsDirectory = request.file.isDirectory,
                        destinationDirectory = directory,
                        sourceEtag = request.file.etag,
                    )
                } else {
                    viewModel.copy(
                        source = source,
                        sourceIsDirectory = request.file.isDirectory,
                        destinationDirectory = directory,
                        sourceEtag = request.file.etag,
                    )
                }
                transferRequest = null
                viewModel?.dismissDestinationPicker()
            },
        )
    }

    if (showCreateDirectoryDialog && isAdmin) {
        CreateDirectoryDialog(
            directory = activePath,
            onDismiss = { showCreateDirectoryDialog = false },
            onConfirm = { name ->
                if (viewModel == null) {
                    onMessage("演示模式不执行云端文件操作")
                } else {
                    viewModel.createDirectory(name)
                }
                showCreateDirectoryDialog = false
            },
        )
    }

    deleteTarget?.takeIf { isAdmin }?.let { file ->
        DeleteConfirmationDialog(
            file = file,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel?.delete(
                    WebDavPath.parseDecoded(file.path),
                    file.isDirectory,
                    file.etag,
                )
                deleteTarget = null
            },
        )
    }

    if (previewState != FilePreviewUiState.Idle) {
        FilePreviewDialog(
            state = previewState,
            editable = isAdmin,
            onDismiss = {
                if (viewModel == null) {
                    demoPreviewState = FilePreviewUiState.Idle
                } else {
                    viewModel.dismissPreview()
                }
            },
            onRetry = {
                if (viewModel == null) {
                    val file = previewState.fileOrNull()
                    demoPreviewState = file?.let { previewFile ->
                        mockPreviewForFile(previewFile)?.let { content ->
                            FilePreviewUiState.Content(previewFile, content)
                        } ?: FilePreviewUiState.Error(previewFile, "演示文件没有可用的预览内容")
                    } ?: FilePreviewUiState.Idle
                } else {
                    viewModel.retryPreview()
                }
            },
            onEdit = {
                if (viewModel == null) {
                    val state = demoPreviewState as? FilePreviewUiState.Content
                    val text = state?.preview as? FilePreviewContent.Text
                    if (state != null && text != null && !text.truncated) {
                        demoPreviewState = FilePreviewUiState.Editing(
                            file = state.file,
                            original = text,
                            draft = text.text,
                        )
                    }
                } else {
                    viewModel.startPreviewEdit()
                }
            },
            onDraftChange = { text ->
                if (viewModel == null) {
                    val state = demoPreviewState as? FilePreviewUiState.Editing
                    if (state != null && text.length <= MAX_EDITED_TEXT_CHARACTERS) {
                        demoPreviewState = state.copy(draft = text, error = null)
                    }
                } else {
                    viewModel.updatePreviewDraft(text)
                }
            },
            onSave = {
                if (!isAdmin) {
                    Unit
                } else if (viewModel == null) {
                    val state = demoPreviewState as? FilePreviewUiState.Editing
                    if (state != null && state.draft != state.original.text) {
                        demoPreviewState = FilePreviewUiState.Content(
                            state.file,
                            state.original.copy(text = state.draft),
                        )
                        onMessage("演示模式不执行云端文件操作")
                    }
                } else {
                    viewModel.savePreviewEdit()
                }
            },
            onCancelEdit = {
                if (viewModel == null) {
                    val state = demoPreviewState as? FilePreviewUiState.Editing
                    if (state != null && !state.saving) {
                        demoPreviewState = FilePreviewUiState.Content(state.file, state.original)
                    }
                } else {
                    viewModel.cancelPreviewEdit()
                }
            },
        )
    }

    when (val mutation = mutationState.takeIf { isAdmin } ?: FileMutationState.Idle) {
        FileMutationState.Idle -> Unit
        FileMutationState.PreparingUpload -> PreparingUploadDialog(
            onCancel = viewModel?.let { it::dismissMutation } ?: {},
        )
        is FileMutationState.UploadReady -> UploadConfirmationDialog(
            documentName = mutation.document.displayName,
            directory = mutation.directory,
            onDismiss = viewModel?.let { it::dismissMutation } ?: {},
            onConfirm = { viewModel?.upload(it) },
        )
        is FileMutationState.Running -> MutationRunningDialog(
            state = mutation,
            onCancel = if (
                mutation.operation is FileOperation.Upload && !mutation.committing
            ) {
                viewModel?.let { it::cancelMutation }
            } else {
                null
            },
        )
        is FileMutationState.AwaitingOverwrite -> OverwriteConfirmationDialog(
            operation = mutation.operation,
            onDismiss = viewModel?.let { it::dismissMutation } ?: {},
            onConfirm = viewModel?.let { it::confirmOverwrite } ?: {},
        )
        is FileMutationState.Failed -> MutationFailedDialog(
            state = mutation,
            onDismiss = viewModel?.let { it::dismissMutation } ?: {},
            onRetry = viewModel?.let { it::retryMutation } ?: {},
        )
    }
}

private enum class TransferType { MOVE, COPY }

private data class TransferRequest(val file: FileNode, val type: TransferType)

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

private fun demoDirectoryPickerState(path: WebDavPath): DirectoryPickerState =
    when (val files = mockFilesForPath(path.toString())) {
        null -> DirectoryPickerState.Error(path, "暂时无法加载此目录")
        else -> DirectoryPickerState.Success(path, files.filter(FileNode::isDirectory))
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
    isAdmin: Boolean = false,
    onManage: (FileNode) -> Unit = {},
) {
    val itemEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val itemSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 8.dp,
            end = 12.dp,
            bottom = if (isAdmin) 184.dp else 112.dp,
        ),
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
                trailingContent = if (file.isDirectory || isAdmin) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (file.isDirectory) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isAdmin) {
                                IconButton(
                                    enabled = enabled,
                                    onClick = { onManage(file) },
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "管理 ${file.name}",
                                    )
                                }
                            }
                        }
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
    onPreview: (() -> Unit)?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                            imageVector = if (file.isDirectory) {
                                Icons.Default.Folder
                            } else {
                                Icons.AutoMirrored.Filled.InsertDriveFile
                            },
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
                        text = if (file.isDirectory) "文件夹" else file.mimeType ?: "未知类型",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!file.isDirectory) DetailLine("大小", formatFileSize(file.size))
            DetailLine("修改时间", formatDate(file.lastModified))
            DetailLine("路径", file.path)
            if (!file.isDirectory) {
                if (onPreview == null) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onPreview,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("预览")
                        }
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("下载")
                        }
                    }
                }
            }
            if (isAdmin) {
                FilledTonalButton(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重命名")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onMove,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("移动")
                    }
                    FilledTonalButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("复制")
                    }
                }
                FilledTonalButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("删除")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilePreviewDialog(
    state: FilePreviewUiState,
    editable: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val file = state.fileOrNull() ?: return
    val editing = state as? FilePreviewUiState.Editing
    val saving = editing?.saving == true
    var showDiscardEdit by rememberSaveable(file.path) { mutableStateOf(false) }
    val requestExitEdit = {
        when {
            saving -> Unit
            editing?.draft != editing?.original?.text -> showDiscardEdit = true
            else -> onCancelEdit()
        }
    }
    val canEdit = editable && state is FilePreviewUiState.Content &&
        state.file.previewFormat() == FilePreviewFormat.PLAIN_TEXT &&
        (state.preview as? FilePreviewContent.Text)?.let { text ->
            !text.truncated && text.encodingEditable &&
                text.entityTag.strongEntityTagOrNull() != null
        } == true
    Dialog(
        onDismissRequest = {
            when {
                saving -> Unit
                editing != null -> requestExitEdit()
                else -> onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = file.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                enabled = !saving,
                                onClick = if (editing == null) onDismiss else requestExitEdit,
                            ) {
                                Icon(
                                    imageVector = if (editing == null) {
                                        Icons.Default.Close
                                    } else {
                                        Icons.AutoMirrored.Filled.ArrowBack
                                    },
                                    contentDescription = if (editing == null) "关闭预览" else "退出编辑",
                                )
                            }
                        },
                        actions = {
                            if (canEdit) {
                                IconButton(onClick = onEdit) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑文本")
                                }
                            }
                            if (editing != null) {
                                if (saving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else if (editable) {
                                    IconButton(
                                        enabled = editing.draft != editing.original.text,
                                        onClick = onSave,
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = "保存编辑")
                                    }
                                }
                            }
                        },
                    )
                },
            ) { innerPadding ->
                when (state) {
                    FilePreviewUiState.Idle -> Unit
                    is FilePreviewUiState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    is FilePreviewUiState.Error -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        FilledTonalButton(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("重试")
                        }
                    }
                    is FilePreviewUiState.Content -> when (val preview = state.preview) {
                        is FilePreviewContent.Text -> TextPreviewPane(
                            preview = preview,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        )
                        is FilePreviewContent.Image -> ImagePreviewPane(
                            fileName = file.name,
                            preview = preview,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        )
                    }
                    is FilePreviewUiState.Editing -> TextEditorPane(
                        state = state,
                        editable = editable,
                        onDraftChange = onDraftChange,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
        if (showDiscardEdit && editing != null && !saving) {
            AlertDialog(
                onDismissRequest = { showDiscardEdit = false },
                title = { Text("放弃修改？") },
                text = { Text("尚未保存的修改将会丢失。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardEdit = false
                            onCancelEdit()
                        },
                    ) { Text("放弃") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardEdit = false }) { Text("继续编辑") }
                },
            )
        }
    }
}

@Composable
private fun TextEditorPane(
    state: FilePreviewUiState.Editing,
    editable: Boolean,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = state.draft,
        onValueChange = onDraftChange,
        modifier = modifier.padding(16.dp),
        enabled = editable && !state.saving,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        label = { Text("编辑文本") },
        supportingText = {
            Text(
                when {
                    !editable -> "当前账户无编辑权限"
                    state.error != null -> state.error
                    else -> "${state.draft.length}/$MAX_EDITED_TEXT_CHARACTERS"
                },
            )
        },
        isError = state.error != null,
    )
}

@Composable
private fun TextPreviewPane(
    preview: FilePreviewContent.Text,
    modifier: Modifier = Modifier,
) {
    val verticalScroll = rememberScrollState()
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (preview.truncated) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "内容过长，仅显示开头部分",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text = preview.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(verticalScroll)
                        .padding(bottom = 24.dp),
                    fontFamily = if (preview.monospace) FontFamily.Monospace else null,
                    softWrap = true,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewPane(
    fileName: String,
    preview: FilePreviewContent.Image,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(preview.bytes) {
        ImageRequest.Builder(context)
            .data(preview.bytes)
            .size(IMAGE_PREVIEW_DECODE_SIZE)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .crossfade(false)
            .build()
    }
    var scale by remember(preview.bytes) { mutableFloatStateOf(1f) }
    var offsetX by remember(preview.bytes) { mutableFloatStateOf(0f) }
    var offsetY by remember(preview.bytes) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, MAX_IMAGE_PREVIEW_SCALE)
        scale = nextScale
        if (nextScale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX = (offsetX + panChange.x).coerceIn(-MAX_IMAGE_PAN, MAX_IMAGE_PAN)
            offsetY = (offsetY + panChange.y).coerceIn(-MAX_IMAGE_PAN, MAX_IMAGE_PAN)
        }
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .transformable(transformState),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "无法解析此图片",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun UploadConfirmationDialog(
    documentName: String,
    directory: WebDavPath,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var remoteName by remember(documentName) { mutableStateOf(documentName) }
    val valid = runCatching { directory.child(remoteName.trim()) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上传到云端") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("目标目录：$directory")
                OutlinedTextField(
                    value = remoteName,
                    onValueChange = { remoteName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("云端文件名") },
                    singleLine = true,
                    isError = remoteName.isNotBlank() && !valid,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(remoteName.trim()) },
            ) { Text("上传") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DestinationDirectoryDialog(
    request: TransferRequest,
    state: DirectoryPickerState,
    onOpenDirectory: (WebDavPath) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (WebDavPath) -> Unit,
) {
    val source = remember(request.file.path) { WebDavPath.parseDecoded(request.file.path) }
    val currentPath = state.currentPathOrNull()
    val destination = currentPath?.let { path ->
        source.name?.let { remoteName -> runCatching { path.child(remoteName) }.getOrNull() }
    }
    val validDestination = state is DirectoryPickerState.Success &&
        destination != null &&
        destination != source &&
        !destination.isDescendantOf(source) &&
        !source.isDescendantOf(destination)
    val directories = (state as? DirectoryPickerState.Success)?.directories.orEmpty()
        .mapNotNull { directory ->
            val path = runCatching { WebDavPath.parseDecoded(directory.path) }.getOrNull()
                ?: return@mapNotNull null
            val allowed = !request.file.isDirectory ||
                (path != source && !path.isDescendantOf(source))
            if (allowed) directory to path else null
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (request.type == TransferType.MOVE) "选择移动位置" else "选择复制位置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = request.file.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = currentPath?.isRoot == false,
                        onClick = onNavigateUp,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "目标文件夹返回上一级",
                        )
                    }
                    Text(
                        text = currentPath?.toString() ?: "/",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (state) {
                    DirectoryPickerState.Idle,
                    is DirectoryPickerState.Loading,
                    -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    is DirectoryPickerState.Error -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                    is DirectoryPickerState.Success -> if (directories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "没有子文件夹",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(directories, key = { it.second.toString() }) { (directory, path) ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = directory.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = "打开文件夹 ${directory.name}",
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                        )
                                    },
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { onOpenDirectory(path) },
                                )
                            }
                        }
                    }
                }
                if (state is DirectoryPickerState.Success && !validDestination) {
                    Text(
                        text = "当前位置与原位置相同或不可作为目标",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = "目标：${destination ?: "-"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = validDestination,
                onClick = { currentPath?.let(onConfirm) },
            ) { Text(if (request.type == TransferType.MOVE) "移动到此处" else "复制到此处") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PreparingUploadDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("正在读取文件") },
        text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

@Composable
private fun CreateDirectoryDialog(
    directory: WebDavPath,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(directory) { mutableStateOf("") }
    val normalizedName = name.trim()
    val validationMessage = when {
        normalizedName.isEmpty() -> null
        normalizedName.length > MAX_RESOURCE_NAME_LENGTH ->
            "文件夹名称不能超过 $MAX_RESOURCE_NAME_LENGTH 个字符"
        runCatching { directory.child(normalizedName) }.isFailure -> "文件夹名称包含无效字符"
        else -> null
    }
    val valid = normalizedName.isNotEmpty() && validationMessage == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "位置：$directory",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    isError = validationMessage != null,
                    supportingText = validationMessage?.let { message ->
                        { Text(message) }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(normalizedName) },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RenameResourceDialog(
    file: FileNode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val source = remember(file.path) { WebDavPath.parseDecoded(file.path) }
    val currentName = requireNotNull(source.name)
    val parent = remember(source) {
        WebDavPath.fromDecodedSegments(source.decodedSegments.dropLast(1))
    }
    var name by remember(file.path) { mutableStateOf(currentName) }
    val resourceLabel = if (file.isDirectory) "文件夹名称" else "文件名"
    val validationMessage = when {
        name.isBlank() -> null
        name.length > MAX_RESOURCE_NAME_LENGTH ->
            "$resourceLabel 不能超过 $MAX_RESOURCE_NAME_LENGTH 个字符"
        runCatching { parent.child(name) }.isFailure -> "$resourceLabel 包含无效字符"
        else -> null
    }
    val valid = name.isNotBlank() &&
        name != currentName &&
        validationMessage == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (file.isDirectory) "重命名文件夹" else "重命名文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "位置：$parent",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("新名称") },
                    singleLine = true,
                    isError = validationMessage != null,
                    supportingText = validationMessage?.let { message ->
                        { Text(message) }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(name) },
            ) { Text("重命名") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DeleteConfirmationDialog(
    file: FileNode,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除 ${file.name}？") },
        text = {
            Text(
                if (file.isDirectory) {
                    "将永久删除 ${file.path} 及其中全部内容，此操作无法撤销。"
                } else {
                    "将永久删除 ${file.path}，此操作无法撤销。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MutationRunningDialog(
    state: FileMutationState.Running,
    onCancel: (() -> Unit)?,
) {
    val total = state.totalBytes
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (state.committing) "正在提交云端文件" else state.operation.actionLabel()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.operation.targetDescription())
                if (state.operation is FileOperation.Upload && total != null && total > 0L) {
                    val progress = (state.uploadedBytes.toFloat() / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${formatFileSize(state.uploadedBytes)} / ${formatFileSize(total)}")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (onCancel != null) TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}

@Composable
private fun OverwriteConfirmationDialog(
    operation: FileOperation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目标已存在") },
        text = {
            Text("${operation.targetDescription()} 已存在，是否覆盖？")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("覆盖") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MutationFailedDialog(
    state: FileMutationState.Failed,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("云端文件操作失败") },
        text = { Text(state.message) },
        confirmButton = {
            if (state.operation != null) {
                TextButton(onClick = onRetry) { Text("重试") }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = if (state.operation != null) {
            { TextButton(onClick = onDismiss) { Text("关闭") } }
        } else {
            null
        },
    )
}

private fun WebDavPath.isDescendantOf(parent: WebDavPath): Boolean =
    decodedSegments.size > parent.decodedSegments.size &&
        decodedSegments.take(parent.decodedSegments.size) == parent.decodedSegments

private fun FileOperation.actionLabel(): String = when (this) {
    is FileOperation.Upload -> "正在上传"
    is FileOperation.CreateDirectory -> "正在新建文件夹"
    is FileOperation.Move -> "正在移动"
    is FileOperation.Rename -> "正在重命名"
    is FileOperation.Copy -> "正在复制"
    is FileOperation.Delete -> "正在删除"
}

private fun FileOperation.targetDescription(): String = when (this) {
    is FileOperation.Upload -> destination.toString()
    is FileOperation.CreateDirectory -> path.toString()
    is FileOperation.Move -> destination.toString()
    is FileOperation.Rename -> destination.toString()
    is FileOperation.Copy -> destination.toString()
    is FileOperation.Delete -> path.toString()
}

private fun DirectoryPickerState.currentPathOrNull(): WebDavPath? = when (this) {
    DirectoryPickerState.Idle -> null
    is DirectoryPickerState.Loading -> path
    is DirectoryPickerState.Success -> path
    is DirectoryPickerState.Error -> path
}

private fun FilePreviewUiState.fileOrNull(): FileNode? = when (this) {
    FilePreviewUiState.Idle -> null
    is FilePreviewUiState.Loading -> file
    is FilePreviewUiState.Content -> file
    is FilePreviewUiState.Editing -> file
    is FilePreviewUiState.Error -> file
}

private const val IMAGE_PREVIEW_DECODE_SIZE = 2_048
private const val MAX_EDITED_TEXT_CHARACTERS = 100_000
private const val MAX_IMAGE_PREVIEW_SCALE = 5f
private const val MAX_IMAGE_PAN = 4_096f

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
