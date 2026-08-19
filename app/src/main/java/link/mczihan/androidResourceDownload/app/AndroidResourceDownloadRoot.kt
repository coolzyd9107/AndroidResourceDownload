package link.mczihan.androidResourceDownload.app

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.core.theme.AndroidResourceDownloadTheme
import link.mczihan.androidResourceDownload.data.mock.initialMockDownloads
import link.mczihan.androidResourceDownload.data.mock.mockTaskForFile
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import link.mczihan.androidResourceDownload.feature.auth.EmailVerificationScreen
import link.mczihan.androidResourceDownload.feature.auth.AuthUiState
import link.mczihan.androidResourceDownload.feature.auth.AuthViewModel
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
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    var sessionUser by remember { mutableStateOf<User?>(null) }

    AndroidResourceDownloadTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavController()
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
                                    authViewModel.reportError("未配置 GitHub Client ID")
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
                            authState is AuthUiState.Authenticating,
                        message = (authState as? AuthUiState.Error)?.message,
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
                            authState is AuthUiState.Authenticating,
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
                            themeMode = themeMode,
                            onThemeModeChange = themeViewModel::setThemeMode,
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
                        ProfileScreen(
                            user = user,
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
                        if (BuildConfig.DEMO_MODE) {
                            tasks = listOf(mockTaskForFile(file)) + tasks
                            showMessage("已加入下载任务")
                        } else {
                            showMessage("下载队列将在下一阶段接入")
                        }
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
                    onCheckUpdate = {
                        if (BuildConfig.DEMO_MODE) {
                            showUpdateDialog = true
                        } else {
                            showMessage("更新功能将在后续阶段接入")
                        }
                    },
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
