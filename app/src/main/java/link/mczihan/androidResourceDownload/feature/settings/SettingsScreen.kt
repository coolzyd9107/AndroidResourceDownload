package link.mczihan.androidResourceDownload.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.core.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    noticeState: NoticeUiState = NoticeUiState.Loading,
    onRetryNotice: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showNotice by rememberSaveable { mutableStateOf(false) }
    var showLogout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "外观",
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val modes = ThemeMode.values()
                val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size,
                            ),
                            contentPadding = if (compact) {
                                PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            } else {
                                SegmentedButtonDefaults.ContentPadding
                            },
                            label = {
                                if (compact) {
                                    Icon(
                                        imageVector = mode.icon(),
                                        contentDescription = mode.fullLabel(),
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Text(mode.shortLabel())
                                }
                            },
                            icon = {
                                if (!compact) {
                                    Icon(
                                        imageVector = mode.icon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                headlineContent = { Text("关于") },
                supportingContent = { Text("版本与开源信息") },
                leadingContent = { SettingsIcon(Icons.Default.Info) },
                modifier = Modifier.clickable { showAbout = true },
            )
            ListItem(
                headlineContent = { Text("退出登录") },
                leadingContent = {
                    SettingsIcon(Icons.AutoMirrored.Filled.Logout, isError = true)
                },
                modifier = Modifier.clickable { showLogout = true },
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
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
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

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于资源下载") },
            text = {
                Text("版本 ${BuildConfig.VERSION_NAME}\n用于访问团队文件和管理下载任务。")
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("确定")
                }
            },
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录？") },
            text = { Text("退出后需要重新验证身份。") },
            confirmButton = {
                TextButton(onClick = onLogout) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SettingsIcon(
    imageVector: ImageVector,
    isError: Boolean = false,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
        }
    }
}

private fun ThemeMode.shortLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun ThemeMode.fullLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "始终浅色"
    ThemeMode.DARK -> "始终深色"
}

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
    ThemeMode.LIGHT -> Icons.Default.LightMode
    ThemeMode.DARK -> Icons.Default.DarkMode
}
