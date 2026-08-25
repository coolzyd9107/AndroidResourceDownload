package com.resdownload.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.resdownload.android.BuildConfig

internal const val FRONTEND_DEVELOPER_URL = "https://github.com/coolzyd9107"
internal const val BACKEND_DEVELOPER_URL = "https://github.com/zhuzhuzihan"
internal const val SOURCE_REPOSITORY_URL =
    "https://github.com/zhuzhuzihan/AndroidResourceDownload"

private const val FRONTEND_AVATAR_URL =
    "https://avatars.githubusercontent.com/u/35447134?v=4"
private const val BACKEND_AVATAR_URL =
    "https://avatars.githubusercontent.com/u/68634388?v=4"

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    noticeState: NoticeUiState = NoticeUiState.Loading,
    onRetryNotice: () -> Unit = {},
    updateState: UpdateUiState = UpdateUiState.Idle,
    onCheckUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onOpenUrl: (String) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    var showNotice by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回设置",
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
            DeveloperSection(
                onOpenUrl = onOpenUrl,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
            Text(
                text = "应用信息",
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            ListItem(
                headlineContent = { Text("公告") },
                supportingContent = {
                    Text(
                        when (noticeState) {
                            NoticeUiState.Loading -> "正在获取最新公告"
                            is NoticeUiState.Content -> "查看最新公告"
                            NoticeUiState.Empty -> "暂无公告"
                            NoticeUiState.Error -> "获取失败"
                        },
                    )
                },
                leadingContent = { SettingsIcon(Icons.Default.Campaign) },
                modifier = Modifier.clickable { showNotice = true },
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
        }
    }

    if (showNotice) {
        AlertDialog(
            onDismissRequest = { showNotice = false },
            title = { Text("公告") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (val state = noticeState) {
                        NoticeUiState.Loading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("正在获取最新公告")
                        }
                        is NoticeUiState.Content -> Text(state.text)
                        NoticeUiState.Empty -> Text("暂无公告")
                        NoticeUiState.Error -> Text("公告获取失败，请检查网络后重试。")
                    }
                }
            },
            confirmButton = {
                if (noticeState == NoticeUiState.Error || noticeState == NoticeUiState.Empty) {
                    TextButton(onClick = onRetryNotice) { Text("重试") }
                } else {
                    TextButton(onClick = { showNotice = false }) { Text("关闭") }
                }
            },
            dismissButton = if (
                noticeState == NoticeUiState.Error || noticeState == NoticeUiState.Empty
            ) {
                {
                    TextButton(onClick = { showNotice = false }) { Text("关闭") }
                }
            } else {
                null
            },
        )
    }

    if (!showNotice) {
        when (val state = updateState) {
            is UpdateUiState.Available -> AlertDialog(
                onDismissRequest = onDismissUpdate,
                title = { Text("发现新版本") },
                text = {
                    Text("当前版本 ${state.currentVersion}\n最新版本 ${state.latestVersion}")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (onOpenUrl(state.updateUrl)) onDismissUpdate()
                        },
                    ) { Text("下载") }
                },
                dismissButton = {
                    TextButton(onClick = onDismissUpdate) { Text("取消") }
                },
            )
            is UpdateUiState.UpToDate -> AlertDialog(
                onDismissRequest = onDismissUpdate,
                title = { Text("已是最新版本") },
                text = { Text("当前版本 ${state.currentVersion}") },
                confirmButton = {
                    TextButton(onClick = onDismissUpdate) { Text("确定") }
                },
            )
            is UpdateUiState.Error -> AlertDialog(
                onDismissRequest = onDismissUpdate,
                title = { Text("检查更新失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onCheckUpdate) { Text("重试") }
                },
                dismissButton = {
                    TextButton(onClick = onDismissUpdate) { Text("取消") }
                },
            )
            UpdateUiState.Idle,
            UpdateUiState.Checking,
                -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
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
                modifier = Modifier.padding(vertical = 20.dp),
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .size(72.dp)
                .semantics { contentDescription = "$username 的 GitHub 头像" },
            shape = MaterialTheme.shapes.medium,
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
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = username,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onOpenUrl(profileUrl) }
                        .semantics {
                            contentDescription = "打开 $username 的 GitHub 主页"
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "GitHub",
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                }
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
