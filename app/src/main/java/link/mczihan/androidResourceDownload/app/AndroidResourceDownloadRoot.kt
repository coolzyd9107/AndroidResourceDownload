package link.mczihan.androidResourceDownload.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.core.theme.AndroidResourceDownloadTheme
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import link.mczihan.androidResourceDownload.core.theme.ThemeSettings
import link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant
import link.mczihan.androidResourceDownload.data.mock.initialMockDownloads
import link.mczihan.androidResourceDownload.data.mock.mockTaskForFile
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import link.mczihan.androidResourceDownload.feature.auth.EmailVerificationScreen
import link.mczihan.androidResourceDownload.feature.auth.AuthUiState
import link.mczihan.androidResourceDownload.feature.auth.AuthViewModel
import link.mczihan.androidResourceDownload.feature.auth.LoginScreen
import link.mczihan.androidResourceDownload.feature.downloads.DownloadsScreen
import link.mczihan.androidResourceDownload.feature.downloads.DownloadsViewModel
import link.mczihan.androidResourceDownload.feature.files.FilesScreen
import link.mczihan.androidResourceDownload.feature.profile.ProfileScreen
import link.mczihan.androidResourceDownload.feature.profile.ProfileViewModel
import link.mczihan.androidResourceDownload.feature.settings.SettingsScreen
import link.mczihan.androidResourceDownload.feature.settings.SettingsViewModel
import link.mczihan.androidResourceDownload.feature.settings.ThemeViewModel
import link.mczihan.androidResourceDownload.feature.uploads.UploadsScreen
import link.mczihan.androidResourceDownload.feature.uploads.UploadsViewModel

private object RootRoute {
    const val Login = "login"
    const val Email = "email"
    const val Main = "main"
    const val Profile = "profile"
}

private enum class ShellRoute(
    val route: String,
    val label: String,
    val adminOnly: Boolean = false,
) {
    Files("files", "文件"),
    Uploads("uploads", "上传", adminOnly = true),
    Downloads("downloads", "下载"),
    Settings("settings", "设置"),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AndroidResourceDownloadRoot(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val themeSettings by themeViewModel.themeSettings.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val privacyConsentAccepted by authViewModel.privacyConsentAccepted.collectAsStateWithLifecycle()
    var sessionUser by remember { mutableStateOf<User?>(null) }

    AndroidResourceDownloadTheme(
        themeMode = themeSettings.themeMode,
        dynamicColorEnabled = themeSettings.dynamicColorEnabled,
        seedColorArgb = themeSettings.seedColorArgb,
        schemeVariant = themeSettings.schemeVariant,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavController()
            val rootOffsetSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val rootSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val rootEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val rootDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
            LaunchedEffect(authState) {
                if (!BuildConfig.DEMO_MODE) {
                    when (authState) {
                        is AuthUiState.Authenticated -> navController.navigate(RootRoute.Main) {
                            popUpTo(RootRoute.Login) { inclusive = true }
                            launchSingleTop = true
                        }
                        AuthUiState.Anonymous -> navController.navigate(RootRoute.Login) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                        else -> Unit
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = RootRoute.Login,
                enterTransition = {
                    fadeIn(rootEffectsSpec) +
                        slideInHorizontally(rootOffsetSpec) { width -> rootDirection * width / 10 }
                },
                exitTransition = {
                    fadeOut(rootEffectsSpec) + scaleOut(rootSpatialSpec, targetScale = 0.98f)
                },
                popEnterTransition = {
                    fadeIn(rootEffectsSpec) + scaleIn(rootSpatialSpec, initialScale = 0.98f)
                },
                popExitTransition = {
                    fadeOut(rootEffectsSpec) +
                        slideOutHorizontally(rootOffsetSpec) { width -> rootDirection * width / 5 }
                },
            ) {
                composable(RootRoute.Login) {
                    val context = LocalContext.current
                    LoginScreen(
                        onGithubLogin = {
                            if (BuildConfig.DEMO_MODE) {
                                sessionUser = User(
                                    id = "mock-github-user",
                                    name = "GitHub 用户",
                                    email = "demo@qq.com",
                                    role = Role.USER,
                                    loginType = LoginType.GITHUB,
                                )
                                navController.navigate(RootRoute.Main) {
                                    popUpTo(RootRoute.Login) { inclusive = true }
                                }
                            } else {
                                val authorizationUrl = authViewModel.beginGithub()
                                if (authorizationUrl == null) {
                                    authViewModel.reportError("未配置有效的后端 API 地址")
                                } else {
                                    CustomTabsIntent.Builder().build().launchUrl(
                                        context,
                                        Uri.parse(authorizationUrl),
                                    )
                                }
                            }
                        },
                        onEmailLogin = {
                            if (BuildConfig.DEMO_MODE) {
                                navController.navigate(RootRoute.Email)
                            } else {
                                navController.navigate(RootRoute.Email)
                            }
                        },
                        busy = authState is AuthUiState.Restoring ||
                            authState is AuthUiState.Authenticating ||
                            authState is AuthUiState.LoggingOut,
                        message = (authState as? AuthUiState.Error)?.message,
                        onPolicyAccepted = authViewModel::acceptPrivacyPolicy,
                    )
                }
                composable(RootRoute.Email) {
                    EmailVerificationScreen(
                        onBack = { navController.popBackStack() },
                        onVerified = { email, role ->
                            if (BuildConfig.DEMO_MODE) {
                                sessionUser = User(
                                    id = "mock-email-${email.hashCode()}",
                                    name = if (role == Role.ADMIN) "管理员" else "邮箱用户",
                                    email = email,
                                    role = role,
                                    loginType = LoginType.EMAIL,
                                )
                                navController.navigate(RootRoute.Main) {
                                    popUpTo(RootRoute.Login) { inclusive = true }
                                }
                            }
                        },
                        onRequestCode = if (BuildConfig.DEMO_MODE) null else authViewModel::requestCode,
                        onLogin = if (BuildConfig.DEMO_MODE) null else authViewModel::loginWithEmail,
                        busy = authState is AuthUiState.SendingCode ||
                            authState is AuthUiState.Authenticating ||
                            authState is AuthUiState.LoggingOut,
                        message = (authState as? AuthUiState.Error)?.message,
                        codeSentEmail = when (val state = authState) {
                            is AuthUiState.AwaitingCode -> state.email
                            is AuthUiState.Error -> (state.recoverableState as? AuthUiState.AwaitingCode)?.email
                            else -> null
                        },
                    )
                }
                composable(RootRoute.Main) {
                    val user = if (BuildConfig.DEMO_MODE) {
                        sessionUser
                    } else {
                        (authState as? AuthUiState.Authenticated)?.session?.user
                    }
                    if (user == null) {
                        LaunchedEffect(Unit) {
                            navController.navigate(RootRoute.Login) {
                                popUpTo(RootRoute.Main) { inclusive = true }
                            }
                        }
                    } else {
                        MainShell(
                            user = user,
                            themeSettings = themeSettings,
                            onThemeModeChange = themeViewModel::setThemeMode,
                            onDynamicColorEnabledChange = themeViewModel::setDynamicColorEnabled,
                            onSeedColorChange = themeViewModel::setSeedColor,
                            onSchemeVariantChange = themeViewModel::setSchemeVariant,
                            onResetSeedColor = themeViewModel::resetSeedColor,
                            onProfile = { navController.navigate(RootRoute.Profile) },
                            onLogout = {
                                if (BuildConfig.DEMO_MODE) {
                                    sessionUser = null
                                    navController.navigate(RootRoute.Login) {
                                        popUpTo(RootRoute.Main) { inclusive = true }
                                    }
                                } else {
                                    authViewModel.logout()
                                }
                            },
                        )
                    }
                }
                composable(RootRoute.Profile) {
                    val user = if (BuildConfig.DEMO_MODE) {
                        sessionUser
                    } else {
                        (authState as? AuthUiState.Authenticated)?.session?.user
                    }
                    if (user == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val profileViewModel = hiltViewModel<ProfileViewModel>()
                        val qqNickname by profileViewModel.qqNickname.collectAsStateWithLifecycle()
                        LaunchedEffect(
                            user.id,
                            user.email,
                            user.loginType,
                            privacyConsentAccepted,
                        ) {
                            if (privacyConsentAccepted) {
                                profileViewModel.load(user)
                            } else {
                                profileViewModel.clear()
                            }
                        }
                        ProfileScreen(
                            user = user,
                            qqNickname = qqNickname,
                            allowQqLookup = privacyConsentAccepted,
                            onBack = { navController.popBackStack() },
                            onLogout = {
                                if (BuildConfig.DEMO_MODE) {
                                    sessionUser = null
                                    navController.navigate(RootRoute.Login) {
                                        popUpTo(RootRoute.Main) { inclusive = true }
                                    }
                                } else {
                                    authViewModel.logout()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainShell(
    user: User,
    themeSettings: ThemeSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorEnabledChange: (Boolean) -> Unit,
    onSeedColorChange: (Int) -> Unit,
    onSchemeVariantChange: (ThemeSchemeVariant) -> Unit,
    onResetSeedColor: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var filesMultiSelectMode by remember { mutableStateOf(false) }
    val showFilesMultiSelectBar = currentRoute == ShellRoute.Files.route && filesMultiSelectMode
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var demoTasks by remember {
        mutableStateOf(if (BuildConfig.DEMO_MODE) initialMockDownloads() else emptyList())
    }
    val context = LocalContext.current
    val downloadsViewModel = if (BuildConfig.DEMO_MODE) null else hiltViewModel<DownloadsViewModel>()
    val persistedTasks = downloadsViewModel?.tasks?.collectAsStateWithLifecycle()?.value.orEmpty()
    val currentSpeeds = downloadsViewModel?.currentSpeeds
        ?.collectAsStateWithLifecycle()
        ?.value
        .orEmpty()
    val uploadsViewModel = if (BuildConfig.DEMO_MODE || user.role != Role.ADMIN) {
        null
    } else {
        hiltViewModel<UploadsViewModel>()
    }
    val uploadTasks = uploadsViewModel?.tasks?.collectAsStateWithLifecycle()?.value.orEmpty()
    val uploadSpeeds = uploadsViewModel?.currentSpeeds
        ?.collectAsStateWithLifecycle()
        ?.value
        .orEmpty()
    val preparingUploads = uploadsViewModel?.preparingSelections
        ?.collectAsStateWithLifecycle()
        ?.value
        ?: 0
    var pendingFileUploadDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFileUploadOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFolderUploadDestination by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFolderUploadOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPermissionDownload by remember { mutableStateOf<Pair<FileNode, String>?>(null) }
    var pendingPermissionRetryId by remember { mutableStateOf<String?>(null) }
    var pendingPermissionStartQueue by remember { mutableStateOf(false) }
    var requestedQueueStoragePermission by remember { mutableStateOf(false) }
    val tabSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val tabOffsetSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val tabEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val tabDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun openShellRoute(route: ShellRoute) {
        navController.navigate(route.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val uploadFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val destination = pendingFileUploadDestination?.let { path ->
            runCatching { WebDavPath.parseDecoded(path) }.getOrNull()
        }
        val owner = pendingFileUploadOwner
        pendingFileUploadDestination = null
        pendingFileUploadOwner = null
        if (uris.isNotEmpty() && destination != null && owner == user.id && user.role == Role.ADMIN) {
            uploadsViewModel?.enqueueFiles(uris, destination)
            openShellRoute(ShellRoute.Uploads)
        }
    }

    val uploadFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val destination = pendingFolderUploadDestination?.let { path ->
            runCatching { WebDavPath.parseDecoded(path) }.getOrNull()
        }
        val owner = pendingFolderUploadOwner
        pendingFolderUploadDestination = null
        pendingFolderUploadOwner = null
        if (uri != null && destination != null && owner == user.id && user.role == Role.ADMIN) {
            uploadsViewModel?.enqueueTree(uri, destination)
            openShellRoute(ShellRoute.Uploads)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingPermissionDownload
        val retryId = pendingPermissionRetryId
        val startQueue = pendingPermissionStartQueue
        pendingPermissionDownload = null
        pendingPermissionRetryId = null
        pendingPermissionStartQueue = false
        if (granted) {
            pending?.let { (file, relativePath) -> downloadsViewModel?.enqueue(file, relativePath) }
            retryId?.let { downloadsViewModel?.retry(it) }
            if (startQueue) downloadsViewModel?.startPending()
        } else {
            showMessage("需要存储权限才能保存到系统下载目录")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingPermissionDownload?.let { (file, relativePath) -> downloadsViewModel?.enqueue(file, relativePath) }
        pendingPermissionDownload = null
        if (!granted) showMessage("通知权限未开启，下载仍会在队列中执行")
    }

    LaunchedEffect(persistedTasks, downloadsViewModel) {
        val hasRestoredQueue = persistedTasks.any { task ->
            task.status == DownloadStatus.PENDING || task.status == DownloadStatus.RUNNING
        }
        if (!BuildConfig.DEMO_MODE &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            hasRestoredQueue &&
            !requestedQueueStoragePermission &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestedQueueStoragePermission = true
            pendingPermissionStartQueue = true
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(downloadsViewModel, user.id) {
        downloadsViewModel?.bindOwner(user.id)
    }
    LaunchedEffect(uploadsViewModel, user.id) {
        uploadsViewModel?.bindOwner(user.id)
    }
    LaunchedEffect(downloadsViewModel) {
        downloadsViewModel?.messages?.collect(::showMessage)
    }
    LaunchedEffect(uploadsViewModel) {
        uploadsViewModel?.messages?.collect(::showMessage)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!showFilesMultiSelectBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    ShellRoute.values()
                        .filter { destination -> !destination.adminOnly || user.role == Role.ADMIN }
                        .forEach { destination ->
                        val selected = currentRoute == destination.route
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.16f else 1f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            label = "navigationIconScale",
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                openShellRoute(destination)
                            },
                            icon = {
                                Icon(
                                    imageVector = when (destination) {
                                        ShellRoute.Files -> Icons.Default.Folder
                                        ShellRoute.Uploads -> Icons.Default.Upload
                                        ShellRoute.Downloads -> Icons.Default.Download
                                        ShellRoute.Settings -> Icons.Default.Settings
                                    },
                                    contentDescription = destination.label,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { shellPadding ->
        NavHost(
            navController = navController,
            startDestination = ShellRoute.Files.route,
            modifier = Modifier.padding(shellPadding),
            popEnterTransition = {
                fadeIn(tabEffectsSpec) +
                    slideInHorizontally(tabOffsetSpec) { width -> -tabDirection * width / 12 }
            },
            popExitTransition = {
                fadeOut(tabEffectsSpec) +
                    slideOutHorizontally(tabOffsetSpec) { width -> tabDirection * width / 5 }
            },
        ) {
            composable(
                route = ShellRoute.Files.route,
                enterTransition = {
                    fadeIn(tabEffectsSpec) + scaleIn(tabSpatialSpec, initialScale = 0.96f)
                },
                exitTransition = {
                    fadeOut(tabEffectsSpec) + scaleOut(tabSpatialSpec, targetScale = 0.98f)
                },
            ) {
                FilesScreen(
                    role = user.role,
                    onProfile = onProfile,
                    onMultiSelectModeChange = { filesMultiSelectMode = it },
                    onDownload = { file, relativePath ->
                        if (BuildConfig.DEMO_MODE) {
                            demoTasks = listOf(mockTaskForFile(file)) + demoTasks
                            showMessage("已加入下载任务")
                        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingPermissionDownload = file to relativePath
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingPermissionDownload = file to relativePath
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            downloadsViewModel?.enqueue(file, relativePath)
                        }
                    },
                    onMessage = ::showMessage,
                    onUploadFile = { destination ->
                        if (BuildConfig.DEMO_MODE) {
                            showMessage("演示模式不执行云端文件操作")
                        } else {
                            pendingFileUploadDestination = destination.toString()
                            pendingFileUploadOwner = user.id
                            uploadFileLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    onUploadFolder = { destination ->
                        if (BuildConfig.DEMO_MODE) {
                            showMessage("演示模式不执行云端文件操作")
                        } else {
                            pendingFolderUploadDestination = destination.toString()
                            pendingFolderUploadOwner = user.id
                            uploadFolderLauncher.launch(null)
                        }
                    },
                )
            }
            composable(
                route = ShellRoute.Uploads.route,
                enterTransition = {
                    fadeIn(tabEffectsSpec) + scaleIn(tabSpatialSpec, initialScale = 0.96f)
                },
                exitTransition = {
                    fadeOut(tabEffectsSpec) + scaleOut(tabSpatialSpec, targetScale = 0.98f)
                },
            ) {
                if (user.role != Role.ADMIN) {
                    LaunchedEffect(Unit) { openShellRoute(ShellRoute.Files) }
                } else {
                    LifecycleResumeEffect(uploadsViewModel) {
                        uploadsViewModel?.startSpeedTracking()
                        uploadsViewModel?.startPending()
                        onPauseOrDispose { uploadsViewModel?.stopSpeedTracking() }
                    }
                    UploadsScreen(
                        tasks = uploadTasks,
                        currentSpeeds = uploadSpeeds,
                        preparingSelections = preparingUploads,
                        onRetry = { taskId -> uploadsViewModel?.retry(taskId) },
                        onCancel = { taskId -> uploadsViewModel?.cancel(taskId) },
                        onDelete = { taskId -> uploadsViewModel?.delete(taskId) },
                        onCancelAll = { uploadsViewModel?.cancelAll() },
                        onClearTerminal = { uploadsViewModel?.clearTerminal() },
                    )
                }
            }
            composable(
                route = ShellRoute.Downloads.route,
                enterTransition = {
                    fadeIn(tabEffectsSpec) + scaleIn(tabSpatialSpec, initialScale = 0.96f)
                },
                exitTransition = {
                    fadeOut(tabEffectsSpec) + scaleOut(tabSpatialSpec, targetScale = 0.98f)
                },
            ) {
                LifecycleResumeEffect(downloadsViewModel, user.id) {
                    downloadsViewModel?.startSpeedTracking()
                    val reconciliation = if (BuildConfig.DEMO_MODE) {
                        null
                    } else {
                        downloadsViewModel?.removeMissingSuccessful(user.id)
                    }
                    onPauseOrDispose {
                        reconciliation?.cancel()
                        downloadsViewModel?.stopSpeedTracking()
                    }
                }
                DownloadsScreen(
                    tasks = if (BuildConfig.DEMO_MODE) demoTasks else persistedTasks,
                    currentSpeeds = if (BuildConfig.DEMO_MODE) emptyMap() else currentSpeeds,
                    onStatusChange = { taskId, status ->
                        if (BuildConfig.DEMO_MODE) {
                            demoTasks = demoTasks.map { task ->
                                if (task.id == taskId) {
                                    task.withMockStatus(status)
                                } else {
                                    task
                                }
                            }
                        } else {
                            when (status) {
                                DownloadStatus.RUNNING -> {
                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        pendingPermissionRetryId = taskId
                                        storagePermissionLauncher.launch(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        )
                                    } else {
                                        downloadsViewModel?.retry(taskId)
                                    }
                                }
                                DownloadStatus.PAUSED -> downloadsViewModel?.pause(taskId)
                                DownloadStatus.CANCELLED -> downloadsViewModel?.cancel(taskId)
                                else -> Unit
                            }
                        }
                    },
                    onOpen = { task ->
                        if (BuildConfig.DEMO_MODE) {
                            showMessage("正在打开 ${task.fileName}")
                        } else {
                            downloadsViewModel?.open(task)
                        }
                    },
                    onDelete = { taskId ->
                        if (BuildConfig.DEMO_MODE) {
                            demoTasks = demoTasks.filterNot { task ->
                                task.id == taskId && task.status in setOf(
                                    DownloadStatus.SUCCESS,
                                    DownloadStatus.FAILED,
                                    DownloadStatus.CANCELLED,
                                )
                            }
                        } else {
                            downloadsViewModel?.delete(taskId)
                        }
                    },
                    onDeleteWithOption = { taskId, deleteLocalFile ->
                        if (!BuildConfig.DEMO_MODE) {
                            downloadsViewModel?.delete(taskId, deleteLocalFile)
                        }
                    },
                    onCancelAll = {
                        if (!BuildConfig.DEMO_MODE) {
                            downloadsViewModel?.cancelAll()
                        }
                    },
                    onClearTerminal = { deleteLocalFiles ->
                        if (!BuildConfig.DEMO_MODE) {
                            downloadsViewModel?.clearTerminal(deleteLocalFiles)
                        }
                    },
                )
            }
            composable(
                route = ShellRoute.Settings.route,
                enterTransition = {
                    fadeIn(tabEffectsSpec) + scaleIn(tabSpatialSpec, initialScale = 0.96f)
                },
                exitTransition = {
                    fadeOut(tabEffectsSpec) + scaleOut(tabSpatialSpec, targetScale = 0.98f)
                },
            ) {
                val settingsViewModel = hiltViewModel<SettingsViewModel>()
                val noticeState by settingsViewModel.noticeState.collectAsStateWithLifecycle()
                val updateState by settingsViewModel.updateState.collectAsStateWithLifecycle()
                LifecycleResumeEffect(settingsViewModel) {
                    settingsViewModel.refreshNotice()
                    onPauseOrDispose { }
                }
                SettingsScreen(
                    themeMode = themeSettings.themeMode,
                    onThemeModeChange = onThemeModeChange,
                    dynamicColorEnabled = themeSettings.dynamicColorEnabled,
                    themeSeedColorArgb = themeSettings.seedColorArgb,
                    themeSchemeVariant = themeSettings.schemeVariant,
                    onDynamicColorEnabledChange = onDynamicColorEnabledChange,
                    onThemeSeedColorChange = onSeedColorChange,
                    onThemeSchemeVariantChange = onSchemeVariantChange,
                    onResetThemeColor = onResetSeedColor,
                    noticeState = noticeState,
                    onRetryNotice = settingsViewModel::refreshNotice,
                    updateState = updateState,
                    onCheckUpdate = settingsViewModel::checkForUpdate,
                    onDismissUpdate = settingsViewModel::dismissUpdateResult,
                    onOpenUpdateUrl = { url ->
                        runCatching {
                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                        }
                            .onFailure { showMessage("无法打开下载链接") }
                            .isSuccess
                    },
                    onLogout = onLogout,
                )
            }
        }
    }
}

private fun DownloadTask.withMockStatus(status: DownloadStatus): DownloadTask {
    val total = totalBytes
    val bytes = if (status == DownloadStatus.SUCCESS && total != null) total else downloadedBytes
    return copy(
        status = status,
        downloadedBytes = bytes,
        updatedAt = System.currentTimeMillis(),
    )
}
