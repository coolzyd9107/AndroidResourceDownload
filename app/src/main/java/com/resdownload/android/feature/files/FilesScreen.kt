package com.resdownload.android.feature.files

import java.util.ArrayDeque
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role as SemanticsRole
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
import com.resdownload.android.BuildConfig
import com.resdownload.android.core.common.formatDate
import com.resdownload.android.core.common.formatFileSize
import com.resdownload.android.core.ui.ContentState
import com.resdownload.android.core.ui.EmptyPane
import com.resdownload.android.core.ui.ErrorPane
import com.resdownload.android.core.ui.LoadingPane
import com.resdownload.android.core.ui.ScalePredictiveBackLayout
import com.resdownload.android.core.ui.SearchTopAppBar
import com.resdownload.android.data.mock.mockFilesForPath
import com.resdownload.android.data.mock.mockPreviewForFile
import com.resdownload.android.domain.model.FileNode
import com.resdownload.android.domain.model.FilePreviewContent
import com.resdownload.android.domain.model.FilePreviewFormat
import com.resdownload.android.domain.model.Role
import com.resdownload.android.domain.model.previewFormat
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.domain.webdav.strongEntityTagOrNull

private data class PredictiveFolderTransition(
    val source: WebDavPath,
    val destination: WebDavPath,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilesScreen(
    role: Role,
    onDownload: (FileNode, String) -> Unit,
    onMessage: (String) -> Unit,
    onUploadFile: (WebDavPath) -> Unit = {},
    onUploadFolder: (WebDavPath) -> Unit = {},
    onMultiSelectModeChange: (Boolean) -> Unit = {},
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
    var showUploadMenu by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedSearchScope by rememberSaveable {
        mutableStateOf(FileSearchScope.CURRENT_DIRECTORY)
    }
    var searchSubmitted by rememberSaveable { mutableStateOf(false) }
    var selectedContentSearchActive by rememberSaveable { mutableStateOf(false) }
    var demoDestinationPath by remember { mutableStateOf(WebDavPath.root()) }
    var demoPreviewState by remember { mutableStateOf<FilePreviewUiState>(FilePreviewUiState.Idle) }
    var demoSearchState by remember { mutableStateOf<FileSearchUiState>(FileSearchUiState.Idle) }
    var demoSearchJob by remember { mutableStateOf<Job?>(null) }
    var demoSearchVersion by remember { mutableIntStateOf(0) }
    var demoMultiSelectMode by remember { mutableStateOf(false) }
    var demoSelectedFiles by remember { mutableStateOf<Map<String, FileNode>>(emptyMap()) }
    var selectedSearchRoots by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    val mutationState = viewModel?.mutationState?.collectAsStateWithLifecycle()?.value
        ?: FileMutationState.Idle
    val realDirectoryPickerState = viewModel?.directoryPickerState
        ?.collectAsStateWithLifecycle()
        ?.value
        ?: DirectoryPickerState.Idle
    val previewState = viewModel?.previewState?.collectAsStateWithLifecycle()?.value
        ?: demoPreviewState
    val realSearchState = viewModel?.searchState?.collectAsStateWithLifecycle()?.value
    val isAdmin = role == Role.ADMIN
    val isRefreshing = viewModel?.isRefreshing?.collectAsStateWithLifecycle()?.value ?: false
    val multiSelectMode = viewModel?.multiSelectMode?.collectAsStateWithLifecycle()?.value
        ?: demoMultiSelectMode
    val selectedFileMap = viewModel?.selectedFiles?.collectAsStateWithLifecycle()?.value
        ?: demoSelectedFiles
    val selectedPaths = selectedFileMap.keys
    var batchTransferRequest by remember { mutableStateOf<BatchTransferRequest?>(null) }
    SideEffect {
        onMultiSelectModeChange(isAdmin && multiSelectMode)
    }
    LaunchedEffect(isAdmin) {
        if (!isAdmin) {
            transferRequest = null
            batchTransferRequest = null
            renameTarget = null
            deleteTarget = null
            showCreateDirectoryDialog = false
            showUploadMenu = false
            demoMultiSelectMode = false
            demoSelectedFiles = emptyMap()
            selectedSearchRoots = emptyList()
            selectedContentSearchActive = false
            viewModel?.dismissDestinationPicker()
            viewModel?.exitMultiSelect()
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
    val fileSearchScope = rememberCoroutineScope()

    fun cancelSearchResults() {
        searchSubmitted = false
        viewModel?.cancelSearch()
        demoSearchVersion++
        demoSearchJob?.cancel()
        demoSearchState = FileSearchUiState.Idle
    }

    fun submitFileSearch(query: String, scope: FileSearchScope) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            cancelSearchResults()
            return
        }
        searchSubmitted = true
        val selectedRoots = selectedSearchRoots
        val effectiveScope = if (multiSelectMode && selectedRoots.isNotEmpty()) {
            FileSearchScope.SELECTED
        } else {
            scope
        }
        if (viewModel != null) {
            if (effectiveScope == FileSearchScope.SELECTED) {
                viewModel.searchSelected(
                    normalizedQuery,
                    selectedRoots,
                    includeUploadTemporary = isAdmin,
                )
            } else {
                viewModel.search(
                    normalizedQuery,
                    effectiveScope,
                    includeUploadTemporary = isAdmin,
                )
            }
            return
        }
        val request = FileSearchRequest(
            query = normalizedQuery,
            scope = effectiveScope,
            basePath = if (effectiveScope == FileSearchScope.ROOT) WebDavPath.root() else activePath,
            selectedFiles = if (effectiveScope == FileSearchScope.SELECTED) {
                selectedRoots
            } else {
                emptyList()
            },
            includeUploadTemporary = isAdmin,
        )
        val version = ++demoSearchVersion
        demoSearchJob?.cancel()
        demoSearchState = FileSearchUiState.Loading(request, scannedDirectories = 0)
        demoSearchJob = fileSearchScope.launch {
            val nextState = try {
                val result = searchFilesRecursively(
                    request = request,
                    listDirectory = { path ->
                        mockFilesForPath(path.toString())
                            ?: throw IllegalStateException("暂时无法加载 ${path}")
                    },
                    onProgress = { progress ->
                        if (version == demoSearchVersion) {
                            demoSearchState = FileSearchUiState.Loading(
                                request = request,
                                scannedDirectories = progress.scannedDirectories,
                                files = progress.files,
                                incomplete = progress.incomplete,
                                progressVersion = progress.progressVersion,
                                visibleFileCount = progress.visibleFileCount,
                            )
                        }
                    },
                )
                if (result.files.isEmpty()) {
                    FileSearchUiState.Empty(request, result.incomplete)
                } else {
                    FileSearchUiState.Success(request, result.files, result.incomplete)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                FileSearchUiState.Error(
                    request,
                    error.message?.takeIf(String::isNotBlank) ?: "文件搜索失败",
                )
            }
            if (version == demoSearchVersion) demoSearchState = nextState
        }
    }

    fun closeFileSearch() {
        cancelSearchResults()
        searchQuery = ""
        searchActive = false
        selectedSearchRoots = emptyList()
        selectedContentSearchActive = false
    }

    fun enterFileMultiSelect() {
        if (!isAdmin) return
        if (viewModel == null) {
            demoMultiSelectMode = true
        } else {
            viewModel.enterMultiSelect()
        }
    }

    fun exitFileMultiSelect() {
        if (viewModel == null) {
            demoMultiSelectMode = false
            demoSelectedFiles = emptyMap()
        } else {
            viewModel.exitMultiSelect()
        }
    }

    fun toggleFileSelection(file: FileNode) {
        if (!isAdmin) return
        if (viewModel == null) {
            demoSelectedFiles = demoSelectedFiles.toMutableMap().apply {
                if (remove(file.path) == null) this[file.path] = file
            }
        } else {
            viewModel.toggleSelection(file)
        }
    }

    fun toggleAllFileSelections(files: List<FileNode>) {
        if (!isAdmin) return
        if (viewModel == null) {
            val selected = demoSelectedFiles.toMutableMap()
            val allSelected = files.isNotEmpty() && files.all { it.path in selected }
            if (allSelected) {
                files.forEach { selected.remove(it.path) }
            } else {
                files.forEach { selected[it.path] = it }
            }
            demoSelectedFiles = selected
        } else {
            viewModel.toggleSelectAll(files)
        }
    }

    fun invertFileSelections(files: List<FileNode>) {
        if (!isAdmin) return
        if (viewModel == null) {
            demoSelectedFiles = demoSelectedFiles.toMutableMap().apply {
                files.forEach { file ->
                    if (remove(file.path) == null) this[file.path] = file
                }
            }
        } else {
            viewModel.invertSelection(files)
        }
    }

    val rawSearchState = realSearchState ?: demoSearchState
    val activeSearchRequest = when (rawSearchState) {
        FileSearchUiState.Idle -> null
        is FileSearchUiState.Loading -> rawSearchState.request
        is FileSearchUiState.Success -> rawSearchState.request
        is FileSearchUiState.Empty -> rawSearchState.request
        is FileSearchUiState.Error -> rawSearchState.request
    }
    LaunchedEffect(isAdmin, activeSearchRequest) {
        val request = activeSearchRequest
        if (!isAdmin && request?.includeUploadTemporary == true) {
            if (request.scope == FileSearchScope.SELECTED) {
                closeFileSearch()
                exitFileMultiSelect()
            } else {
                val query = request.query
                cancelSearchResults()
                searchQuery = query
                searchActive = true
                submitFileSearch(query, request.scope)
            }
        }
    }
    val selectedContentRoots = selectedSearchRoots.takeIf(List<FileNode>::isNotEmpty)
        ?: activeSearchRequest?.selectedFiles.orEmpty()
    val searchingSelectedContent = multiSelectMode &&
        (selectedContentSearchActive ||
            selectedContentRoots.isNotEmpty() ||
            activeSearchRequest?.scope == FileSearchScope.SELECTED)
    LaunchedEffect(activeSearchRequest) {
        if (
            selectedSearchRoots.isEmpty() &&
            activeSearchRequest?.scope == FileSearchScope.SELECTED
        ) {
            selectedSearchRoots = activeSearchRequest.selectedFiles
        }
    }
    LaunchedEffect(
        selectedContentSearchActive,
        activeSearchRequest,
        selectedSearchRoots,
        multiSelectMode,
    ) {
        if (
            selectedContentSearchActive &&
            activeSearchRequest?.scope != FileSearchScope.SELECTED &&
            selectedSearchRoots.isEmpty()
        ) {
            selectedContentSearchActive = false
            searchSubmitted = false
            searchQuery = ""
            searchActive = false
        }
    }
    LaunchedEffect(searchActive, searchSubmitted, rawSearchState) {
        if (
            searchActive &&
            searchSubmitted &&
            searchQuery.isNotBlank() &&
            (!selectedContentSearchActive || selectedSearchRoots.isNotEmpty()) &&
            rawSearchState == FileSearchUiState.Idle
        ) {
            submitFileSearch(searchQuery, selectedSearchScope)
        }
    }
    val searchState = rawSearchState
    val directoryPickerState = if (
        viewModel == null && (transferRequest != null || batchTransferRequest != null)
    ) {
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
    val displayedFiles = (filePaneState.content as? FilePaneContent.Files)?.value.orEmpty()
    val selectableFiles = if (searchActive) {
        when (searchState) {
            is FileSearchUiState.Loading -> fixedPrefixView(
                searchState.files,
                searchState.visibleFileCount,
            )
            is FileSearchUiState.Success -> searchState.files
            else -> emptyList()
        }
    } else {
        displayedFiles
    }
    val selectedFiles = selectedFileMap.values.toList()
    val allDisplayedFilesSelected = selectableFiles.isNotEmpty() &&
        selectableFiles.all { it.path in selectedPaths }
    var folderHistory by remember { mutableStateOf<List<FilePaneState>>(emptyList()) }
    var predictiveFolderTransition by remember {
        mutableStateOf<PredictiveFolderTransition?>(null)
    }
    var cachedNavigationPane by remember { mutableStateOf<FilePaneState?>(null) }
    val predictiveFolderHandoff = predictiveFolderTransition?.destination == activePath
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
    val visibleFilePaneState = cachedNavigationPane?.takeIf { cachedPane ->
        cachedPane.path == filePaneState.path && filePaneState.content is FilePaneContent.Loading
    } ?: filePaneState
    val frozenBackPreview = predictiveFolderTransition?.let { transition ->
        cachedNavigationPane?.takeIf { it.path == transition.destination }
    }
    LaunchedEffect(activePath, filePaneState, predictiveFolderTransition) {
        if (predictiveFolderTransition == null) {
            val cachedPane = cachedNavigationPane
            val leftCachedPath = cachedPane?.path != activePath
            val finishedCachedLoad = cachedPane?.path == filePaneState.path &&
                filePaneState.content !is FilePaneContent.Loading
            if (cachedPane != null && (leftCachedPath || finishedCachedLoad)) {
                cachedNavigationPane = null
            }
        }
    }
    val folderSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val folderScaleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val folderEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val navigateToParent = { destination: WebDavPath ->
        folderHistory = folderHistory.dropLast(1)
        if (viewModel == null) {
            currentPath = destination.toString()
        } else {
            viewModel.openDirectory(destination)
        }
    }
    val navigateUp = {
        parentPath?.let(navigateToParent)
        Unit
    }
    val folderPredictiveBackEnabled = selectedFile == null &&
        transferRequest == null &&
        batchTransferRequest == null &&
        renameTarget == null &&
        deleteTarget == null &&
        !showCreateDirectoryDialog &&
        !multiSelectMode &&
        !searchActive &&
        previewState == FilePreviewUiState.Idle &&
        mutationState == FileMutationState.Idle &&
        !activePath.isRoot

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ScalePredictiveBackLayout(
            enabled = folderPredictiveBackEnabled,
            onBack = {
                parentPath?.let { destination ->
                    cachedNavigationPane = parentPreviewState
                    predictiveFolderTransition = PredictiveFolderTransition(
                        source = activePath,
                        destination = destination,
                    )
                    navigateToParent(destination)
                }
            },
            onBackFinished = { predictiveFolderTransition = null },
            contentKey = activePath,
            background = { backgroundModifier ->
                (frozenBackPreview ?: parentPreviewState)?.let { preview ->
                    FolderBackPreview(
                        pane = preview,
                        isAdmin = isAdmin,
                        modifier = backgroundModifier,
                    )
                }
            },
        ) { foregroundModifier ->
            Scaffold(
                modifier = foregroundModifier,
                topBar = {
                    if (searchActive && searchingSelectedContent) {
                        SearchTopAppBar(
                            query = searchQuery,
                            placeholder = "在已选内容中搜索",
                            closeContentDescription = "返回文件选择",
                            searchContentDescription = "搜索已选文件和文件夹",
                            onQueryChange = { query ->
                                searchQuery = query
                                cancelSearchResults()
                            },
                            onSearch = {
                                submitFileSearch(searchQuery, FileSearchScope.SELECTED)
                            },
                            onClose = ::closeFileSearch,
                            subtitle = when (val current = searchState) {
                                is FileSearchUiState.Loading ->
                                    "已选 ${selectedContentRoots.size} 项 · 已扫描 ${current.scannedDirectories} 个目录"
                                else -> "在 ${selectedContentRoots.size} 个已选项中搜索"
                            },
                        )
                    } else if (multiSelectMode && isAdmin) {
                        TopAppBar(
                            title = { Text("已选择 ${selectedPaths.size} 项") },
                            navigationIcon = {
                                IconButton(onClick = ::exitFileMultiSelect) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "取消选择",
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        if (selectedFiles.isEmpty()) {
                                            selectedSearchRoots = emptyList()
                                            selectedContentSearchActive = false
                                            exitFileMultiSelect()
                                        } else {
                                            selectedSearchRoots = selectedFiles
                                            selectedContentSearchActive = true
                                        }
                                        cancelSearchResults()
                                        searchQuery = ""
                                        searchActive = true
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "在已选文件中搜索",
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    } else if (searchActive) {
                        Column {
                            SearchTopAppBar(
                                query = searchQuery,
                                placeholder = "搜索文件和文件夹",
                                closeContentDescription = "关闭文件搜索",
                                searchContentDescription = "执行文件搜索",
                                onQueryChange = { query ->
                                    searchQuery = query
                                    cancelSearchResults()
                                },
                                onSearch = {
                                    submitFileSearch(searchQuery, selectedSearchScope)
                                },
                                onClose = ::closeFileSearch,
                                subtitle = (searchState as? FileSearchUiState.Loading)?.let { loading ->
                                    if (loading.scannedDirectories == 0) {
                                        "正在准备搜索"
                                    } else {
                                        "已扫描 ${loading.scannedDirectories} 个目录"
                                    }
                                },
                                additionalActions = {
                                    if (
                                        isAdmin &&
                                        (searchState as? FileSearchUiState.Success)?.files?.isNotEmpty() == true
                                    ) {
                                        IconButton(onClick = ::enterFileMultiSelect) {
                                            Icon(
                                                Icons.Default.Checklist,
                                                contentDescription = "选择文件搜索结果",
                                            )
                                        }
                                    }
                                },
                            )
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    val scopes = listOf(
                                        FileSearchScope.ROOT,
                                        FileSearchScope.CURRENT_DIRECTORY,
                                    )
                                    scopes.forEachIndexed { index, scope ->
                                        SegmentedButton(
                                            selected = selectedSearchScope == scope,
                                            onClick = {
                                                selectedSearchScope = scope
                                                cancelSearchResults()
                                                if (searchQuery.isNotBlank()) {
                                                    submitFileSearch(searchQuery, scope)
                                                }
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = scopes.size,
                                            ),
                                            label = { Text(scope.label()) },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        TopAppBar(
                            title = { Text("文件") },
                            subtitle = {
                                AnimatedContent(
                                    targetState = displayedPath,
                                    transitionSpec = {
                                        if (predictiveFolderHandoff) {
                                            EnterTransition.None.togetherWith(ExitTransition.None)
                                        } else {
                                            (fadeIn(folderEffectsSpec) +
                                                slideInVertically(folderSpatialSpec) { it / 2 })
                                                .togetherWith(
                                                    fadeOut(folderEffectsSpec) +
                                                        slideOutVertically(folderSpatialSpec) { -it / 2 },
                                                )
                                        }
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
                                    enter = if (predictiveFolderHandoff) {
                                        EnterTransition.None
                                    } else {
                                        fadeIn(folderEffectsSpec) + scaleIn(folderScaleSpec)
                                    },
                                    exit = if (predictiveFolderHandoff) {
                                        ExitTransition.None
                                    } else {
                                        fadeOut(folderEffectsSpec) + scaleOut(folderScaleSpec)
                                    },
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
                                IconButton(onClick = { searchActive = true }) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "搜索文件和文件夹",
                                    )
                                }
                                IconButton(onClick = {
                                    if (viewModel == null) {
                                        state = fileStateForPath(currentPath)
                                    } else {
                                        viewModel.refresh()
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "刷新文件列表")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                },
                bottomBar = {
                    if (multiSelectMode && isAdmin) {
                        MultiSelectBottomBar(
                            allSelected = allDisplayedFilesSelected,
                            hasSelection = selectedPaths.isNotEmpty(),
                            selectionControlsEnabled = !searchingSelectedContent,
                            onToggleSelectAll = { toggleAllFileSelections(selectableFiles) },
                            onInvertSelection = { invertFileSelections(selectableFiles) },
                            onMove = {
                                val files = selectedFiles
                                if (files.isNotEmpty()) {
                                    batchTransferRequest = BatchTransferRequest(files, TransferType.MOVE)
                                    if (searchActive) {
                                        closeFileSearch()
                                        exitFileMultiSelect()
                                    }
                                    if (viewModel == null) demoDestinationPath = activePath
                                    viewModel?.openDestinationPicker(activePath)
                                }
                            },
                            onCopy = {
                                val files = selectedFiles
                                if (files.isNotEmpty()) {
                                    batchTransferRequest = BatchTransferRequest(files, TransferType.COPY)
                                    if (searchActive) {
                                        closeFileSearch()
                                        exitFileMultiSelect()
                                    }
                                    if (viewModel == null) demoDestinationPath = activePath
                                    viewModel?.openDestinationPicker(activePath)
                                }
                            },
                            onDownload = {
                                val selected = selectedFiles
                                var demoFolderHadFiles = false
                                selected.forEach { item ->
                                    if (item.isDirectory) {
                                        if (viewModel == null) {
                                            val entries = demoFolderDownloadEntries(item)
                                            if (entries.isNotEmpty()) demoFolderHadFiles = true
                                            entries.forEach { (fileNode, relativePath) ->
                                                onDownload(fileNode, relativePath)
                                            }
                                        } else {
                                            viewModel.downloadFolder(item) { fileNode, relativePath ->
                                                onDownload(fileNode, relativePath)
                                            }
                                        }
                                    } else {
                                        onDownload(item, "")
                                    }
                                }
                                if (viewModel != null && selected.isNotEmpty()) {
                                    onMessage("已加入下载任务")
                                } else if (
                                    selected.any(FileNode::isDirectory) &&
                                    !demoFolderHadFiles
                                ) {
                                    onMessage("所选文件夹中没有可下载文件")
                                }
                                if (searchActive) closeFileSearch()
                                exitFileMultiSelect()
                            },
                            onDelete = {
                                val files = selectedFiles
                                if (files.isNotEmpty()) {
                                    if (searchActive) {
                                        closeFileSearch()
                                        exitFileMultiSelect()
                                    }
                                    if (viewModel == null) {
                                        onMessage("演示模式不执行云端文件操作")
                                    } else {
                                        viewModel.batchDelete(files)
                                    }
                                }
                            },
                        )
                    }
                },
                floatingActionButton = {
                    if (
                        isAdmin &&
                        mutationState == FileMutationState.Idle &&
                        !multiSelectMode &&
                        !searchActive
                    ) {
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
                            Box(contentAlignment = Alignment.BottomEnd) {
                                ExtendedFloatingActionButton(
                                    text = { Text("上传") },
                                    icon = {
                                        Icon(Icons.Default.UploadFile, contentDescription = "上传")
                                    },
                                    onClick = { showUploadMenu = true },
                                )
                                DropdownMenu(
                                    expanded = showUploadMenu,
                                    onDismissRequest = { showUploadMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("上传文件") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.UploadFile,
                                                contentDescription = "上传文件",
                                            )
                                        },
                                        onClick = {
                                            showUploadMenu = false
                                            onUploadFile(activePath)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("上传文件夹") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = "上传文件夹",
                                            )
                                        },
                                        onClick = {
                                            showUploadMenu = false
                                            onUploadFolder(activePath)
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                if (searchActive) {
                    FileSearchContent(
                        state = searchState,
                        isAdmin = isAdmin,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        onRetry = {
                            if (viewModel == null) {
                                submitFileSearch(searchQuery, selectedSearchScope)
                            } else {
                                viewModel.retrySearch()
                            }
                        },
                        multiSelectMode = multiSelectMode && !searchingSelectedContent,
                        selectedPaths = selectedPaths,
                        onManage = { file -> if (isAdmin) selectedFile = file },
                        onFileClick = { file ->
                            if (searchingSelectedContent) {
                                selectedFile = file
                            } else if (multiSelectMode) {
                                toggleFileSelection(file)
                            } else if (file.isDirectory) {
                                closeFileSearch()
                                folderHistory = emptyList()
                                if (viewModel == null) {
                                    currentPath = file.path
                                } else {
                                    viewModel.openDirectory(WebDavPath.parseDecoded(file.path))
                                }
                            } else {
                                selectedFile = file
                            }
                        },
                        onFileLongClick = { file ->
                            if (isAdmin && !multiSelectMode && !searchingSelectedContent) {
                                enterFileMultiSelect()
                                toggleFileSelection(file)
                            }
                        },
                    )
                } else {
                    AnimatedContent(
                        targetState = visibleFilePaneState,
                        contentKey = { pane -> pane.path to pane.content::class },
                        transitionSpec = {
                            if (
                                predictiveFolderTransition?.let { transition ->
                                    initialState.path == transition.source &&
                                        targetState.path == transition.destination
                                } == true
                            ) {
                                EnterTransition.None.togetherWith(ExitTransition.None)
                            } else if (initialState.path != targetState.path) {
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
                        val isTargetContent = pane == visibleFilePaneState
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
                            is FilePaneContent.Files -> PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    if (viewModel == null) {
                                        state = fileStateForPath(currentPath)
                                    } else {
                                        viewModel.refresh()
                                    }
                                },
                                modifier = contentModifier,
                            ) {
                                FileList(
                                    files = content.value,
                                    modifier = Modifier.fillMaxSize(),
                                    enabled = isTargetContent,
                                    isAdmin = isAdmin,
                                    animateItems = !predictiveFolderHandoff,
                                    multiSelectMode = multiSelectMode,
                                    selectedPaths = selectedPaths,
                                    onManage = { if (isAdmin) selectedFile = it },
                                    onFileClick = { file ->
                                        if (multiSelectMode) {
                                            toggleFileSelection(file)
                                        } else if (file.isDirectory) {
                                            folderHistory = (
                                                folderHistory + visibleFilePaneState
                                            ).takeLast(12)
                                            if (viewModel == null) {
                                                currentPath = file.path
                                            } else {
                                                viewModel.openDirectory(WebDavPath.parseDecoded(file.path))
                                            }
                                        } else {
                                            selectedFile = file
                                        }
                                    },
                                    onFileLongClick = { file ->
                                        if (isAdmin && !multiSelectMode) {
                                            onMultiSelectModeChange(true)
                                            enterFileMultiSelect()
                                            toggleFileSelection(file)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = multiSelectMode && !searchingSelectedContent) {
        exitFileMultiSelect()
    }

    BackHandler(enabled = searchActive && (!multiSelectMode || searchingSelectedContent)) {
        closeFileSearch()
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
                onDownload(file, "")
                selectedFile = null
            },
            onDownloadFolder = {
                if (viewModel == null) {
                    val entries = demoFolderDownloadEntries(file)
                    entries.forEach { (fileNode, relativePath) ->
                        onDownload(fileNode, relativePath)
                    }
                    if (entries.isEmpty()) onMessage("此文件夹中没有可下载文件")
                } else {
                    viewModel.downloadFolder(file) { fileNode, relativePath ->
                        onDownload(fileNode, relativePath)
                    }
                }
                selectedFile = null
            },
            onRename = {
                if (searchActive) closeFileSearch()
                selectedFile = null
                renameTarget = file
            },
            onMove = {
                if (searchActive) closeFileSearch()
                selectedFile = null
                transferRequest = TransferRequest(file, TransferType.MOVE)
                if (viewModel == null) {
                    demoDestinationPath = activePath
                } else {
                    viewModel.openDestinationPicker(activePath)
                }
            },
            onCopy = {
                if (searchActive) closeFileSearch()
                selectedFile = null
                transferRequest = TransferRequest(file, TransferType.COPY)
                if (viewModel == null) {
                    demoDestinationPath = activePath
                } else {
                    viewModel.openDestinationPicker(activePath)
                }
            },
            onDelete = {
                if (searchActive) closeFileSearch()
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

    batchTransferRequest?.takeIf { isAdmin }?.let { request ->
        DestinationDirectoryDialog(
            request = TransferRequest(request.files.first(), request.type),
            sources = request.files,
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
                batchTransferRequest = null
                viewModel?.dismissDestinationPicker()
            },
            onConfirm = { directory ->
                if (viewModel == null) {
                    onMessage("演示模式不执行批量${if (request.type == TransferType.MOVE) "移动" else "复制"}")
                } else if (request.type == TransferType.MOVE) {
                    viewModel.batchMove(request.files, directory)
                } else {
                    viewModel.batchCopy(request.files, directory)
                }
                batchTransferRequest = null
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
        is FileMutationState.Running -> MutationRunningDialog(state = mutation)
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

private data class BatchTransferRequest(val files: List<FileNode>, val type: TransferType)

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

@Composable
private fun MultiSelectBottomBar(
    allSelected: Boolean,
    hasSelection: Boolean,
    selectionControlsEnabled: Boolean = true,
    onToggleSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        MultiSelectAction(
            icon = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
            label = if (allSelected) "取消全选" else "全选",
            enabled = selectionControlsEnabled,
            onClick = onToggleSelectAll,
            modifier = Modifier.weight(1f),
        )
        MultiSelectAction(
            icon = Icons.Default.FlipToBack,
            label = "反选",
            enabled = selectionControlsEnabled,
            onClick = onInvertSelection,
            modifier = Modifier.weight(1f),
        )
        MultiSelectAction(
            icon = Icons.AutoMirrored.Filled.DriveFileMove,
            label = "移动",
            enabled = hasSelection,
            onClick = onMove,
            modifier = Modifier.weight(1f),
        )
        MultiSelectAction(
            icon = Icons.Default.ContentCopy,
            label = "复制",
            enabled = hasSelection,
            onClick = onCopy,
            modifier = Modifier.weight(1f),
        )
        MultiSelectAction(
            icon = Icons.Default.Download,
            label = "下载",
            enabled = hasSelection,
            onClick = onDownload,
            modifier = Modifier.weight(1f),
        )
        MultiSelectAction(
            icon = Icons.Default.Delete,
            label = "删除",
            enabled = hasSelection,
            destructive = true,
            onClick = onDelete,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MultiSelectAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = SemanticsRole.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 4.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderBackPreview(
    pane: FilePaneState,
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.clearAndSetSemantics { },
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
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text("新建文件夹") },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        onClick = {},
                    )
                    ExtendedFloatingActionButton(
                        text = { Text("上传") },
                        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        onClick = {},
                    )
                }
            }
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
                isAdmin = isAdmin,
                animateItems = false,
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

internal fun demoFolderDownloadEntries(folder: FileNode): List<Pair<FileNode, String>> {
    if (!folder.isDirectory) return emptyList()
    val root = runCatching { WebDavPath.parseDecoded(folder.path) }.getOrNull()
        ?: return emptyList()
    val directories = ArrayDeque<WebDavPath>().apply { add(root) }
    val visitedDirectories = mutableSetOf<WebDavPath>()
    val visitedResources = mutableSetOf<WebDavPath>()
    val result = mutableListOf<Pair<FileNode, String>>()
    while (directories.isNotEmpty()) {
        val directory = directories.removeFirst()
        if (!visitedDirectories.add(directory)) continue
        mockFilesForPath(directory.toString()).orEmpty().forEach { item ->
            val itemPath = runCatching { WebDavPath.parseDecoded(item.path) }.getOrNull()
                ?: return@forEach
            val isDirectChild = itemPath.decodedSegments.size ==
                directory.decodedSegments.size + 1 &&
                itemPath.decodedSegments.dropLast(1) == directory.decodedSegments
            if (!isDirectChild || !visitedResources.add(itemPath)) return@forEach
            if (item.isDirectory) {
                directories.addLast(itemPath)
            } else {
                val relativeFull = item.path.removePrefix(folder.path.trimEnd('/')).trimStart('/')
                val subdirectory = relativeFull.substringBeforeLast('/', "")
                val relativePath = if (subdirectory.isEmpty()) {
                    folder.name
                } else {
                    "${folder.name}/$subdirectory"
                }
                result += item to relativePath
            }
        }
    }
    return result
}

internal fun <T> fixedPrefixView(source: List<T>, count: Int): List<T> =
    object : AbstractList<T>() {
        override val size: Int = count.coerceIn(0, source.size)

        override fun get(index: Int): T {
            if (index !in 0 until size) throw IndexOutOfBoundsException("index=$index, size=$size")
            return source[index]
        }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileSearchContent(
    state: FileSearchUiState,
    isAdmin: Boolean,
    multiSelectMode: Boolean,
    selectedPaths: Set<String>,
    onRetry: () -> Unit,
    onManage: (FileNode) -> Unit,
    onFileClick: (FileNode) -> Unit,
    onFileLongClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        FileSearchUiState.Idle -> EmptyPane(
            message = "输入关键词开始搜索",
            icon = Icons.Default.Search,
            modifier = modifier,
        )
        is FileSearchUiState.Loading -> if (state.files.isEmpty()) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoadingIndicator()
                Text(
                    text = if (state.scannedDirectories == 0) {
                        "正在准备搜索"
                    } else {
                        "正在搜索，已扫描 ${state.scannedDirectories} 个目录"
                    },
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(modifier = modifier) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "仍在搜索",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                            Text(
                                text = "已找到 ${state.visibleFileCount} 项 · 已扫描 ${state.scannedDirectories} 个目录",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (state.incomplete) {
                    Text(
                        text = "部分目录无法访问，当前结果可能不完整",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FileList(
                    files = state.files,
                    onFileClick = onFileClick,
                    modifier = Modifier.weight(1f),
                    isAdmin = isAdmin,
                    multiSelectMode = multiSelectMode,
                    selectedPaths = selectedPaths,
                    onManage = onManage,
                    onFileLongClick = onFileLongClick,
                    showPath = true,
                    reserveAdminActionSpace = false,
                    visibleCount = state.visibleFileCount,
                )
            }
        }
        is FileSearchUiState.Empty -> EmptyPane(
            message = if (state.incomplete) {
                "未找到匹配项，部分目录无法访问"
            } else {
                "未找到匹配的文件或文件夹"
            },
            icon = Icons.Default.SearchOff,
            modifier = modifier,
        )
        is FileSearchUiState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = modifier,
        )
        is FileSearchUiState.Success -> Column(modifier = modifier) {
            if (state.incomplete) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "部分目录无法访问，搜索结果可能不完整",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            FileList(
                files = state.files,
                onFileClick = onFileClick,
                modifier = Modifier.weight(1f),
                isAdmin = isAdmin,
                multiSelectMode = multiSelectMode,
                selectedPaths = selectedPaths,
                onManage = onManage,
                onFileLongClick = onFileLongClick,
                showPath = true,
                reserveAdminActionSpace = false,
            )
        }
    }
}

private fun FileSearchScope.label(): String = when (this) {
    FileSearchScope.ROOT -> "整个云盘"
    FileSearchScope.CURRENT_DIRECTORY -> "当前目录"
    FileSearchScope.SELECTED -> "已选内容"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    files: List<FileNode>,
    onFileClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAdmin: Boolean = false,
    multiSelectMode: Boolean = false,
    selectedPaths: Set<String> = emptySet(),
    onManage: (FileNode) -> Unit = {},
    onFileLongClick: (FileNode) -> Unit = {},
    showPath: Boolean = false,
    reserveAdminActionSpace: Boolean = true,
    visibleCount: Int = files.size,
    animateItems: Boolean = true,
) {
    val itemEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val itemSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 8.dp,
            end = 12.dp,
            bottom = if (reserveAdminActionSpace && isAdmin && !multiSelectMode) 184.dp else 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            count = visibleCount.coerceAtMost(files.size),
            key = { index -> files[index].path },
        ) { index ->
            val file = files[index]
            val isSelected = file.path in selectedPaths
            ListItem(
                headlineContent = {
                    Text(
                        text = file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    val metadata = if (file.isDirectory) {
                        "文件夹 · ${formatDate(file.lastModified)}"
                    } else {
                        "${formatFileSize(file.size)} · ${formatDate(file.lastModified)}"
                    }
                    Text(
                        text = if (showPath) {
                            "$metadata\n${file.path}"
                        } else {
                            metadata
                        },
                        maxLines = if (showPath) 2 else 1,
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
                trailingContent = if (multiSelectMode) {
                    {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onFileClick(file) },
                        )
                    }
                } else if (file.isDirectory || isAdmin) {
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
                colors = ListItemDefaults.colors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = (if (animateItems) {
                    Modifier.animateItem(
                        fadeInSpec = itemEffectsSpec,
                        placementSpec = itemSpatialSpec,
                        fadeOutSpec = itemEffectsSpec,
                    )
                } else {
                    Modifier
                })
                    .clip(MaterialTheme.shapes.small)
                    .combinedClickable(
                        enabled = enabled,
                        onClick = { onFileClick(file) },
                        onLongClick = { onFileLongClick(file) },
                        onClickLabel = if (file.isDirectory) "打开文件夹" else "查看详情",
                        onLongClickLabel = "多选",
                    ),
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
    onDownloadFolder: () -> Unit = {},
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
            if (file.isDirectory && isAdmin) {
                Button(
                    onClick = onDownloadFolder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("下载文件夹")
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
private fun DestinationDirectoryDialog(
    request: TransferRequest,
    sources: List<FileNode> = listOf(request.file),
    state: DirectoryPickerState,
    onOpenDirectory: (WebDavPath) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (WebDavPath) -> Unit,
) {
    val sourcePaths = remember(sources.map(FileNode::path)) {
        sources.map { file -> WebDavPath.parseDecoded(file.path) }
    }
    val directorySources = sources.zip(sourcePaths)
        .filter { (file, _) -> file.isDirectory }
        .map { (_, path) -> path }
    val currentPath = state.currentPathOrNull()
    val destinations = currentPath?.let { path ->
        sourcePaths.mapNotNull { sourcePath ->
            sourcePath.name?.let { remoteName -> runCatching { path.child(remoteName) }.getOrNull() }
        }
    }.orEmpty()
    val validDestination = state is DirectoryPickerState.Success &&
        currentPath != null &&
        isValidTransferDestination(sourcePaths, currentPath)
    val directories = (state as? DirectoryPickerState.Success)?.directories.orEmpty()
        .mapNotNull { directory ->
            val path = runCatching { WebDavPath.parseDecoded(directory.path) }.getOrNull()
                ?: return@mapNotNull null
            val allowed = directorySources.none { sourceDirectory ->
                path == sourceDirectory || path.isDescendantOf(sourceDirectory)
            }
            if (allowed) directory to path else null
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (request.type == TransferType.MOVE) "选择移动位置" else "选择复制位置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (sources.size == 1) request.file.name else "已选择 ${sources.size} 项",
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
                    text = if (sources.size == 1) {
                        "目标：${destinations.firstOrNull() ?: "-"}"
                    } else {
                        "目标目录：${currentPath ?: "-"}"
                    },
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

internal fun isValidTransferDestination(
    sources: List<WebDavPath>,
    destinationDirectory: WebDavPath,
): Boolean {
    if (sources.isEmpty()) return false
    val sortedSources = sources.sortedBy { path ->
        path.decodedSegments.joinToString(separator = "\u0000")
    }
    val hasOverlappingSources = sortedSources.zipWithNext().any { (first, second) ->
        second.isDescendantOf(first)
    }
    if (hasOverlappingSources) return false
    val destinations = sources.map { source ->
        val name = source.name ?: return false
        runCatching { destinationDirectory.child(name) }.getOrNull() ?: return false
    }
    if (destinations.distinct().size != destinations.size) return false
    return sources.zip(destinations).all { (source, destination) ->
        destination != source &&
            !destination.isDescendantOf(source) &&
            !source.isDescendantOf(destination)
    }
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
            "${resourceLabel}不能超过 $MAX_RESOURCE_NAME_LENGTH 个字符"
        runCatching { parent.child(name) }.isFailure -> "${resourceLabel}包含无效字符"
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
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(state.operation.actionLabel()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.operation.targetDescription())
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
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
    is FileOperation.CreateDirectory -> "正在新建文件夹"
    is FileOperation.Move -> "正在移动"
    is FileOperation.Rename -> "正在重命名"
    is FileOperation.Copy -> "正在复制"
    is FileOperation.Delete -> "正在删除"
}

private fun FileOperation.targetDescription(): String = when (this) {
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
