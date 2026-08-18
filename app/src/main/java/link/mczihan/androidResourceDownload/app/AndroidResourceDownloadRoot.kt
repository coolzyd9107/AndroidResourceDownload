package link.mczihan.androidResourceDownload.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.theme.AndroidResourceDownloadTheme
import link.mczihan.androidResourceDownload.data.mock.initialMockDownloads
import link.mczihan.androidResourceDownload.data.mock.mockTaskForFile
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.model.LoginMethod
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import link.mczihan.androidResourceDownload.feature.auth.EmailVerificationScreen
import link.mczihan.androidResourceDownload.feature.auth.LoginScreen
import link.mczihan.androidResourceDownload.feature.downloads.DownloadsScreen
import link.mczihan.androidResourceDownload.feature.files.FilesScreen
import link.mczihan.androidResourceDownload.feature.profile.ProfileScreen
import link.mczihan.androidResourceDownload.feature.settings.SettingsScreen
import link.mczihan.androidResourceDownload.feature.settings.ThemeViewModel
import link.mczihan.androidResourceDownload.feature.update.MockUpdateDialog

private object RootRoute {
    const val Login = "login"
    const val Email = "email"
    const val Main = "main"
    const val Profile = "profile"
}

private enum class ShellRoute(
    val route: String,
    val label: String,
) {
    Files("files", "文件"),
    Downloads("downloads", "下载"),
    Settings("settings", "设置"),
}

@Composable
fun AndroidResourceDownloadRoot(
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    var sessionUser by remember { mutableStateOf<User?>(null) }

    AndroidResourceDownloadTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = RootRoute.Login,
            ) {
                composable(RootRoute.Login) {
                    LoginScreen(
                        onGithubLogin = {
                            sessionUser = User(
                                id = "mock-github-user",
                                name = "GitHub 用户",
                                email = "demo@qq.com",
                                role = Role.USER,
                                loginMethod = LoginMethod.GITHUB,
                            )
                            navController.navigate(RootRoute.Main) {
                                popUpTo(RootRoute.Login) { inclusive = true }
                            }
                        },
                        onEmailLogin = { navController.navigate(RootRoute.Email) },
                    )
                }
                composable(RootRoute.Email) {
                    EmailVerificationScreen(
                        onBack = { navController.popBackStack() },
                        onVerified = { email, role ->
                            sessionUser = User(
                                id = "mock-email-${email.hashCode()}",
                                name = if (role == Role.ADMIN) "管理员" else "邮箱用户",
                                email = email,
                                role = role,
                                loginMethod = LoginMethod.EMAIL,
                            )
                            navController.navigate(RootRoute.Main) {
                                popUpTo(RootRoute.Login) { inclusive = true }
                            }
                        },
                    )
                }
                composable(RootRoute.Main) {
                    val user = sessionUser
                    if (user == null) {
                        LaunchedEffect(Unit) {
                            navController.navigate(RootRoute.Login) {
                                popUpTo(RootRoute.Main) { inclusive = true }
                            }
                        }
                    } else {
                        MainShell(
                            user = user,
                            themeMode = themeMode,
                            onThemeModeChange = themeViewModel::setThemeMode,
                            onProfile = { navController.navigate(RootRoute.Profile) },
                            onLogout = {
                                sessionUser = null
                                navController.navigate(RootRoute.Login) {
                                    popUpTo(RootRoute.Main) { inclusive = true }
                                }
                            },
                        )
                    }
                }
                composable(RootRoute.Profile) {
                    val user = sessionUser
                    if (user == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        ProfileScreen(
                            user = user,
                            onBack = { navController.popBackStack() },
                            onLogout = {
                                sessionUser = null
                                navController.navigate(RootRoute.Login) {
                                    popUpTo(RootRoute.Main) { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainShell(
    user: User,
    themeMode: link.mczihan.androidResourceDownload.core.theme.ThemeMode,
    onThemeModeChange: (link.mczihan.androidResourceDownload.core.theme.ThemeMode) -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(initialMockDownloads()) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                ShellRoute.values().forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    ShellRoute.Files -> Icons.Default.Folder
                                    ShellRoute.Downloads -> Icons.Default.Download
                                    ShellRoute.Settings -> Icons.Default.Settings
                                },
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { shellPadding ->
        NavHost(
            navController = navController,
            startDestination = ShellRoute.Files.route,
            modifier = Modifier.padding(shellPadding),
        ) {
            composable(ShellRoute.Files.route) {
                FilesScreen(
                    role = user.role,
                    onProfile = onProfile,
                    onDownload = { file ->
                        tasks = listOf(mockTaskForFile(file)) + tasks
                        showMessage("已加入下载任务")
                    },
                    onMessage = ::showMessage,
                )
            }
            composable(ShellRoute.Downloads.route) {
                DownloadsScreen(
                    tasks = tasks,
                    onStatusChange = { taskId, status ->
                        tasks = tasks.map { task ->
                            if (task.id == taskId) {
                                task.withMockStatus(status)
                            } else {
                                task
                            }
                        }
                    },
                    onMessage = ::showMessage,
                )
            }
            composable(ShellRoute.Settings.route) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onCheckUpdate = { showUpdateDialog = true },
                    onLogout = onLogout,
                )
            }
        }
    }

    if (showUpdateDialog) {
        MockUpdateDialog(
            onDismiss = { showUpdateDialog = false },
            onUpdate = {
                showUpdateDialog = false
                tasks = listOf(
                    mockTaskForFile(
                        link.mczihan.androidResourceDownload.domain.model.FileNode(
                            name = "android-client-1.1.0.apk",
                            path = "/应用发布/android-client-1.1.0.apk",
                            isDirectory = false,
                            size = 38_624_256L,
                            mimeType = "application/vnd.android.package-archive",
                        ),
                    ),
                ) + tasks
                showMessage("更新包已加入下载任务")
            },
        )
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
