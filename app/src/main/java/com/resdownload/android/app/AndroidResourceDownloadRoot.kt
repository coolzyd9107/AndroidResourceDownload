package com.resdownload.android.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.resdownload.android.BuildConfig
import com.resdownload.android.core.theme.AndroidResourceDownloadTheme
import com.resdownload.android.core.theme.ThemeMode
import com.resdownload.android.core.theme.ThemeSettings
import com.resdownload.android.core.theme.ThemeSchemeVariant
import com.resdownload.android.core.ui.AppSnackbarHost
import com.resdownload.android.core.ui.ScalePredictiveBackLayout
import com.resdownload.android.data.mock.initialMockDownloads
import com.resdownload.android.data.mock.mockTaskForFile
import com.resdownload.android.domain.model.DownloadStatus
import com.resdownload.android.domain.model.DownloadTask
import com.resdownload.android.domain.model.FileNode
import com.resdownload.android.domain.model.LoginType
import com.resdownload.android.domain.model.Role
import com.resdownload.android.domain.model.User
import com.resdownload.android.domain.webdav.WebDavPath
import com.resdownload.android.feature.auth.AuthUiState
import com.resdownload.android.feature.auth.AuthViewModel
import com.resdownload.android.feature.auth.LoginScreen
import com.resdownload.android.feature.downloads.DownloadsScreen
import com.resdownload.android.feature.downloads.DownloadsViewModel
import com.resdownload.android.feature.files.FilesScreen
import com.resdownload.android.feature.settings.AboutScreen
import com.resdownload.android.feature.settings.AboutViewModel
import com.resdownload.android.feature.settings.SettingsScreen
import com.resdownload.android.feature.settings.SettingsViewModel
import com.resdownload.android.feature.settings.ThemeViewModel
import com.resdownload.android.feature.settings.UpdateUiState
import com.resdownload.android.feature.uploads.UploadsScreen
import com.resdownload.android.feature.uploads.UploadsViewModel

private object RootRoute {
    const val Login = "login"
    const val Main = "main"
}

private const val QQ_PRIVACY_POLICY_URL =
    "https://wiki.connect.qq.com/qq%E4%BA%92%E8%81%94sdk%E9%9A%90%E7%A7%81%E4%BF%9D%E6%8A%A4%E5%A3%B0%E6%98%8E"
private const val TAB_NAVIGATION_COALESCE_MILLIS = 240L

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

private fun shellTransitionDirection(initialRoute: String?, targetRoute: String?): Int {
    fun position(route: String?): Int = when (route) {
        ShellRoute.Files.route -> ShellRoute.Files.ordinal
        ShellRoute.Uploads.route -> ShellRoute.Uploads.ordinal
        ShellRoute.Downloads.route -> ShellRoute.Downloads.ordinal
        ShellRoute.Settings.route -> ShellRoute.Settings.ordinal
        else -> 0
    }

    return position(targetRoute).compareTo(position(initialRoute))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellNavigationBar(
    currentRoute: String?,
    role: Role,
    onDestinationSelected: (ShellRoute) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        ShellRoute.values()
            .filter { destination -> !destination.adminOnly || role == Role.ADMIN }
            .forEach { destination ->
                val selected = currentRoute == destination.route
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.16f else 1f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "navigationIconScale",
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = { onDestinationSelected(destination) },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AndroidResourceDownloadRoot(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val themeSettings by themeViewModel.themeSettings.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
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
                    var showAbout by rememberSaveable { mutableStateOf(false) }
                    var predictiveAboutExit by remember { mutableStateOf(false) }
                    val aboutViewModel = hiltViewModel<AboutViewModel>()
                    val updateState by aboutViewModel.updateState.collectAsStateWithLifecycle()
                    val aboutBackEnabled = showAbout && when (updateState) {
                        UpdateUiState.Idle, UpdateUiState.Checking -> true
                        is UpdateUiState.Available,
                        is UpdateUiState.Error,
                        is UpdateUiState.UpToDate,
                            -> false
                    }
                    val aboutOffsetSpec = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow)
                    val aboutEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                    Box(modifier = Modifier.fillMaxSize()) {
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
                                    if (authorizationUrl != null) {
                                        if (!context.launchCustomTab(authorizationUrl)) {
                                            authViewModel.reportGithubLaunchFailure()
                                        }
                                    }
                                }
                            },
                            onQqLogin = {
                                if (BuildConfig.DEMO_MODE) {
                                    sessionUser = User(
                                        id = "mock-qq-user",
                                        name = "QQ 用户",
                                        email = null,
                                        role = Role.USER,
                                        loginType = LoginType.QQ,
                                    )
                                    navController.navigate(RootRoute.Main) {
                                        popUpTo(RootRoute.Login) { inclusive = true }
                                    }
                                } else {
                                    context.findActivity()?.let(authViewModel::beginQq)
                                        ?: authViewModel.reportError("无法获取当前页面，QQ 登录未启动")
                                }
                            },
                            onOpenAbout = {
                                predictiveAboutExit = false
                                showAbout = true
                            },
                            modifier = if (showAbout) {
                                Modifier.clearAndSetSemantics { }
                            } else {
                                Modifier
                            },
                            busy = authState is AuthUiState.Restoring ||
                                authState is AuthUiState.Authenticating ||
                                authState is AuthUiState.LoggingOut,
                            message = (authState as? AuthUiState.Error)?.message,
                            onPolicyAccepted = authViewModel::acceptPrivacyPolicy,
                            onOpenQqPrivacyPolicy = {
                                if (!context.launchBrowser(QQ_PRIVACY_POLICY_URL)) {
                                    authViewModel.reportError("无法打开 QQ 互联 SDK 隐私保护声明")
                                }
                            },
                        )

                        ScalePredictiveBackLayout(
                            enabled = aboutBackEnabled,
                            onBack = {
                                predictiveAboutExit = true
                                showAbout = false
                            },
                            contentKey = showAbout,
                            background = { backgroundModifier -> Box(backgroundModifier) },
                        ) { foregroundModifier ->
                            AnimatedVisibility(
                                visible = showAbout,
                                modifier = foregroundModifier,
                                enter = fadeIn(aboutEffectsSpec) +
                                    slideInHorizontally(aboutOffsetSpec) { width ->
                                        rootDirection * width / 8
                                    },
                                exit = if (predictiveAboutExit) {
                                    ExitTransition.None
                                } else {
                                    fadeOut(aboutEffectsSpec) +
                                        slideOutHorizontally(aboutOffsetSpec) { width ->
                                            rootDirection * width / 8
                                        }
                                },
                            ) {
                                AboutScreen(
                                    onNavigateBack = {
                                        predictiveAboutExit = false
                                        showAbout = false
                                    },
                                    navigateBackContentDescription = "返回登录",
                                    updateState = updateState,
                                    onCheckUpdate = aboutViewModel::checkForUpdate,
                                    onDismissUpdate = aboutViewModel::dismissUpdateResult,
                                    onOpenUrl = context::launchBrowser,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
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
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var showAbout by rememberSaveable(user.id) { mutableStateOf(false) }
    var predictiveAboutExit by remember { mutableStateOf(false) }
    var requestedTabTransition by remember { mutableStateOf<Pair<String, String>?>(null) }
    var filesMultiSelectMode by remember { mutableStateOf(false) }
    var transferMultiSelectMode by remember { mutableStateOf(false) }
    val showMultiSelectBar = when (currentRoute) {
        ShellRoute.Files.route -> filesMultiSelectMode
        ShellRoute.Downloads.route, ShellRoute.Uploads.route -> transferMultiSelectMode
        else -> false
    }
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
    var requestedQueueStoragePermission by rememberSaveable(user.id) { mutableStateOf(false) }
    val tabOffsetSpec = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow)
    val tabEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val tabDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val aboutViewModel = hiltViewModel<AboutViewModel>()
    val updateState by aboutViewModel.updateState.collectAsStateWithLifecycle()
    val aboutBackEnabled = showAbout && when (updateState) {
        UpdateUiState.Idle, UpdateUiState.Checking -> true
        is UpdateUiState.Available,
        is UpdateUiState.Error,
        is UpdateUiState.UpToDate,
            -> false
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun openBrowserUrl(url: String): Boolean = context.launchBrowser(url).also { launched ->
        if (!launched) showMessage("无法打开浏览器")
    }

    fun openShellRoute(route: ShellRoute, animatePop: Boolean = false): Boolean {
        if (navController.currentDestination?.route == route.route) return false
        val initialRoute = navController.currentDestination?.route ?: return false
        val transitionRequest = initialRoute to route.route
        requestedTabTransition = transitionRequest.takeIf { animatePop }
        showAbout = false
        predictiveAboutExit = false
        navController.navigate(route.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        if (animatePop) {
            scope.launch {
                delay(TAB_NAVIGATION_COALESCE_MILLIS)
                if (requestedTabTransition == transitionRequest) requestedTabTransition = null
            }
        }
        return true
    }

    val tabNavigationRequests = remember { Channel<ShellRoute>(Channel.CONFLATED) }
    LaunchedEffect(navController, tabNavigationRequests) {
        for (route in tabNavigationRequests) {
            if (openShellRoute(route, animatePop = true)) delay(TAB_NAVIGATION_COALESCE_MILLIS)
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
        val work = downloadsViewModel?.takeDeferredPermissionWork()
        if (granted) {
            work?.downloads.orEmpty().forEach { (file, relativePath) ->
                downloadsViewModel?.enqueue(file, relativePath)
            }
            work?.retryIds.orEmpty().forEach { downloadsViewModel?.retry(it) }
            if (work?.startPending == true) downloadsViewModel?.startPending()
        } else {
            showMessage("需要存储权限才能保存到系统下载目录")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val work = downloadsViewModel?.takeDeferredPermissionWork()
        work?.downloads.orEmpty().forEach { (file, relativePath) ->
            downloadsViewModel?.enqueue(file, relativePath)
        }
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
            val shouldLaunch = downloadsViewModel?.deferPermissionStartPending() == true
            if (shouldLaunch) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    LaunchedEffect(downloadsViewModel, user.id) {
        downloadsViewModel?.bindOwner(user.id)
    }
    LaunchedEffect(uploadsViewModel, user.id) {
        uploadsViewModel?.bindOwner(user.id)
    }
    LifecycleResumeEffect(downloadsViewModel, uploadsViewModel, user.id) {
        val reconciliation = if (BuildConfig.DEMO_MODE) {
            null
        } else {
            downloadsViewModel?.removeMissingSuccessful(user.id)
        }
        uploadsViewModel?.startPending()
        onPauseOrDispose { reconciliation?.cancel() }
    }
    LaunchedEffect(downloadsViewModel) {
        downloadsViewModel?.messages?.collect(::showMessage)
    }
    LaunchedEffect(uploadsViewModel) {
        uploadsViewModel?.messages?.collect(::showMessage)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (showAbout) Modifier.clearAndSetSemantics { } else Modifier,
            snackbarHost = { AppSnackbarHost(snackbarHostState) },
            bottomBar = {
                if (
                    !showMultiSelectBar &&
                    ShellRoute.values().any { destination -> destination.route == currentRoute }
                ) {
                    ShellNavigationBar(
                        currentRoute = currentRoute,
                        role = user.role,
                        onDestinationSelected = { destination ->
                            if (!showAbout) tabNavigationRequests.trySend(destination)
                        },
                    )
                }
            },
        ) { shellPadding ->
            NavHost(
                navController = navController,
                startDestination = ShellRoute.Files.route,
                modifier = Modifier.padding(shellPadding),
                enterTransition = {
                    val direction = shellTransitionDirection(
                        initialState.destination.route,
                        targetState.destination.route,
                    )
                    slideInHorizontally(tabOffsetSpec) { width ->
                        tabDirection * direction * width
                    }
                },
                exitTransition = {
                    val direction = shellTransitionDirection(
                        initialState.destination.route,
                        targetState.destination.route,
                    )
                    slideOutHorizontally(tabOffsetSpec) { width ->
                        -tabDirection * direction * width
                    }
                },
                popEnterTransition = {
                    val initialRoute = initialState.destination.route
                    val targetRoute = targetState.destination.route
                    val direction = shellTransitionDirection(initialRoute, targetRoute)
                    if (requestedTabTransition == (initialRoute to targetRoute)) {
                        slideInHorizontally(tabOffsetSpec) { width ->
                            tabDirection * direction * width
                        }
                    } else {
                        EnterTransition.None
                    }
                },
                popExitTransition = {
                    val initialRoute = initialState.destination.route
                    val targetRoute = targetState.destination.route
                    val direction = shellTransitionDirection(initialRoute, targetRoute)
                    if (requestedTabTransition == (initialRoute to targetRoute)) {
                        slideOutHorizontally(tabOffsetSpec) { width ->
                            -tabDirection * direction * width
                        }
                    } else {
                        ExitTransition.None
                    }
                },
            ) {
                composable(route = ShellRoute.Files.route) {
                FilesScreen(
                    role = user.role,
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
                            val shouldLaunch = downloadsViewModel
                                ?.deferPermissionDownload(file, relativePath) == true
                            if (shouldLaunch) {
                                storagePermissionLauncher.launch(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                )
                            }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            val shouldLaunch = downloadsViewModel
                                ?.deferPermissionDownload(file, relativePath) == true
                            if (shouldLaunch) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            }
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
            composable(route = ShellRoute.Uploads.route) {
                if (user.role != Role.ADMIN) {
                    LaunchedEffect(Unit) { openShellRoute(ShellRoute.Files) }
                } else {
                    LifecycleResumeEffect(uploadsViewModel) {
                        uploadsViewModel?.startSpeedTracking()
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
                        onMultiSelectModeChange = { transferMultiSelectMode = it },
                    )
                }
            }
            composable(route = ShellRoute.Downloads.route) {
                LifecycleResumeEffect(downloadsViewModel, user.id) {
                    downloadsViewModel?.startSpeedTracking()
                    onPauseOrDispose {
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
                                        val shouldLaunch = downloadsViewModel
                                            ?.deferPermissionRetry(taskId) == true
                                        if (shouldLaunch) {
                                            storagePermissionLauncher.launch(
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            )
                                        }
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
                        if (BuildConfig.DEMO_MODE) {
                            demoTasks = demoTasks.filterNot { task ->
                                task.id == taskId && task.status in setOf(
                                    DownloadStatus.SUCCESS,
                                    DownloadStatus.FAILED,
                                    DownloadStatus.CANCELLED,
                                )
                            }
                        } else {
                            downloadsViewModel?.delete(taskId, deleteLocalFile)
                        }
                    },
                    onCancelAll = {
                        if (BuildConfig.DEMO_MODE) {
                            demoTasks = demoTasks.map { task ->
                                if (task.status in setOf(
                                        DownloadStatus.PENDING,
                                        DownloadStatus.RUNNING,
                                        DownloadStatus.PAUSED,
                                    )
                                ) {
                                    task.withMockStatus(DownloadStatus.CANCELLED)
                                } else {
                                    task
                                }
                            }
                        } else {
                            downloadsViewModel?.cancelAll()
                        }
                    },
                    onClearTerminal = { deleteLocalFiles ->
                        if (BuildConfig.DEMO_MODE) {
                            demoTasks = demoTasks.filterNot { task ->
                                task.status in setOf(
                                    DownloadStatus.SUCCESS,
                                    DownloadStatus.FAILED,
                                    DownloadStatus.CANCELLED,
                                )
                            }
                        } else {
                            downloadsViewModel?.clearTerminal(deleteLocalFiles)
                        }
                    },
                    onMultiSelectModeChange = { transferMultiSelectMode = it },
                )
            }
                composable(route = ShellRoute.Settings.route) {
                    val settingsViewModel = hiltViewModel<SettingsViewModel>()
                    val noticeState by settingsViewModel.noticeState.collectAsStateWithLifecycle()
                    SettingsScreen(
                        user = user,
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
                        onOpenAbout = {
                            predictiveAboutExit = false
                            showAbout = true
                        },
                        onLogout = onLogout,
                    )
                }
            }
        }

        if (currentRoute == ShellRoute.Settings.route) {
            ScalePredictiveBackLayout(
                enabled = aboutBackEnabled,
                onBack = {
                    predictiveAboutExit = true
                    showAbout = false
                },
                contentKey = showAbout,
                background = { backgroundModifier -> Box(backgroundModifier) },
            ) { foregroundModifier ->
                AnimatedVisibility(
                    visible = showAbout,
                    modifier = foregroundModifier,
                    enter = fadeIn(tabEffectsSpec) +
                        slideInHorizontally(tabOffsetSpec) { width ->
                            tabDirection * width / 8
                        },
                    exit = if (predictiveAboutExit) {
                        ExitTransition.None
                    } else {
                        fadeOut(tabEffectsSpec) +
                            slideOutHorizontally(tabOffsetSpec) { width ->
                                tabDirection * width / 8
                            }
                    },
                ) {
                    AboutScreen(
                        onNavigateBack = {
                            predictiveAboutExit = false
                            showAbout = false
                        },
                        updateState = updateState,
                        onCheckUpdate = aboutViewModel::checkForUpdate,
                        onDismissUpdate = aboutViewModel::dismissUpdateResult,
                        onOpenUrl = ::openBrowserUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.launchBrowser(url: String): Boolean = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}.isSuccess

private fun Context.launchCustomTab(url: String): Boolean = runCatching {
    CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
}.isSuccess

private fun DownloadTask.withMockStatus(status: DownloadStatus): DownloadTask {
    val total = totalBytes
    val bytes = if (status == DownloadStatus.SUCCESS && total != null) total else downloadedBytes
    return copy(
        status = status,
        downloadedBytes = bytes,
        updatedAt = System.currentTimeMillis(),
    )
}
