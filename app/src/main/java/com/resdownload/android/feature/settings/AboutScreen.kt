package com.resdownload.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.resdownload.android.BuildConfig
import com.resdownload.android.R
import com.resdownload.android.core.ui.AppIcon
import com.resdownload.android.core.ui.ExpressiveDialog
import com.resdownload.android.core.ui.ExpressiveDialogAction
import com.resdownload.android.core.ui.ExpressiveDialogTone

internal const val FRONTEND_DEVELOPER_URL = "https://github.com/coolzyd9107"
internal const val BACKEND_DEVELOPER_URL = "https://github.com/zhuzhuzihan"
internal const val SOURCE_REPOSITORY_URL =
    "https://github.com/zhuzhuzihan/AndroidResourceDownload"
internal const val DONATION_URL = "https://myweb.mczihan.link/donate"

private const val FRONTEND_AVATAR_URL =
    "https://avatars.githubusercontent.com/u/35447134?v=4"
private const val BACKEND_AVATAR_URL =
    "https://avatars.githubusercontent.com/u/68634388?v=4"

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    navigateBackContentDescription: String = "返回设置",
    updateState: UpdateUiState = UpdateUiState.Idle,
    onCheckUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onOpenUrl: (String) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = navigateBackContentDescription,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            AppIdentity(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
            DeveloperSection(
                onOpenUrl = onOpenUrl,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = "应用信息",
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                headlineContent = { Text("检查更新") },
                supportingContent = {
                    Text(
                        when (val state = updateState) {
                            UpdateUiState.Idle -> "当前版本 ${BuildConfig.VERSION_NAME}"
                            UpdateUiState.Checking -> "正在检查更新"
                            is UpdateUiState.Available -> "发现新版本 ${state.latestVersion}"
                            is UpdateUiState.UpToDate -> "当前版本 ${state.currentVersion}"
                            is UpdateUiState.Error -> "检查失败"
                        },
                    )
                },
                leadingContent = { SettingsIcon(Icons.Default.SystemUpdate) },
                trailingContent = if (updateState == UpdateUiState.Checking) {
                    {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    null
                },
                modifier = Modifier.clickable(
                    enabled = updateState != UpdateUiState.Checking,
                    onClick = onCheckUpdate,
                ),
            )
            ListItem(
                headlineContent = { Text("在GitHub查看源代码") },
                supportingContent = { Text("浏览项目代码、提交问题或参与开发") },
                leadingContent = { SettingsIcon(Icons.Default.Code) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                },
                modifier = Modifier.clickable { onOpenUrl(SOURCE_REPOSITORY_URL) },
            )
            ListItem(
                headlineContent = { Text("向我们捐赠") },
                supportingContent = { Text("支持项目持续开发与维护") },
                leadingContent = { SettingsIcon(Icons.Default.VolunteerActivism) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                },
                modifier = Modifier.clickable { onOpenUrl(DONATION_URL) },
            )
        }
    }

    when (val state = updateState) {
        is UpdateUiState.Available -> ExpressiveDialog(
            onDismissRequest = onDismissUpdate,
            title = "发现新版本",
            icon = Icons.Default.SystemUpdate,
            content = {
                Text(
                    text = "当前版本 ${state.currentVersion}  ·  最新版本 ${state.latestVersion}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.releaseNotes.isNotEmpty()) {
                    Text(
                        text = "v${state.latestVersion} 更新内容",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.releaseNotes.forEach { note ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = note,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "暂未获取到该版本的更新日志",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            actions = {
                ExpressiveDialogAction(label = "取消", onClick = onDismissUpdate)
                ExpressiveDialogAction(
                    label = "下载",
                    onClick = {
                        if (onOpenUrl(state.updateUrl)) onDismissUpdate()
                    },
                    primary = true,
                )
            },
        )
        is UpdateUiState.UpToDate -> ExpressiveDialog(
            onDismissRequest = onDismissUpdate,
            title = "已是最新版本",
            icon = Icons.Default.CheckCircle,
            tone = ExpressiveDialogTone.POSITIVE,
            content = { Text("当前版本 ${state.currentVersion}") },
            actions = {
                ExpressiveDialogAction(
                    label = "确定",
                    onClick = onDismissUpdate,
                    primary = true,
                )
            },
        )
        is UpdateUiState.Error -> ExpressiveDialog(
            onDismissRequest = onDismissUpdate,
            title = "检查更新失败",
            icon = Icons.Default.ErrorOutline,
            tone = ExpressiveDialogTone.DESTRUCTIVE,
            content = { Text(state.message) },
            actions = {
                ExpressiveDialogAction(label = "取消", onClick = onDismissUpdate)
                ExpressiveDialogAction(
                    label = "重试",
                    onClick = onCheckUpdate,
                    primary = true,
                )
            },
        )
        UpdateUiState.Idle,
        UpdateUiState.Checking,
            -> Unit
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppIdentity(modifier: Modifier = Modifier) {
    val appName = stringResource(R.string.app_name)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(
            contentDescription = "$appName 应用图标",
            modifier = Modifier.size(88.dp),
        )
        Text(
            text = appName,
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeveloperSection(
    onOpenUrl: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            DeveloperRow(
                username = "coolzyd9107",
                responsibility = "前端开发者",
                avatarUrl = FRONTEND_AVATAR_URL,
                profileUrl = FRONTEND_DEVELOPER_URL,
                roleContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                roleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onOpenUrl = onOpenUrl,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            DeveloperRow(
                username = "zhuzhuzihan",
                responsibility = "后端开发者",
                avatarUrl = BACKEND_AVATAR_URL,
                profileUrl = BACKEND_DEVELOPER_URL,
                roleContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                roleContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeveloperRow(
    username: String,
    responsibility: String,
    avatarUrl: String,
    profileUrl: String,
    roleContainerColor: Color,
    roleContentColor: Color,
    onOpenUrl: (String) -> Boolean,
) {
    val context = LocalContext.current
    val avatarRequest = remember(context, avatarUrl) {
        ImageRequest.Builder(context)
            .data(avatarUrl)
            .crossfade(true)
            .build()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button) { onOpenUrl(profileUrl) }
            .semantics {
                contentDescription = "打开 $username 的 GitHub 主页"
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "$username 的 GitHub 头像" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            SubcomposeAsyncImage(
                model = avatarRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { DefaultDeveloperAvatar() },
                error = { DefaultDeveloperAvatar() },
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = username,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = CircleShape,
                color = roleContainerColor,
            ) {
                Text(
                    text = responsibility,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = roleContentColor,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

@Composable
private fun DefaultDeveloperAvatar() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
