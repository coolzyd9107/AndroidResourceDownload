package com.resdownload.android.feature.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.resdownload.android.core.theme.DEFAULT_THEME_SEED_ARGB
import com.resdownload.android.core.theme.ThemeMode
import com.resdownload.android.core.theme.ThemeSeedPreset
import com.resdownload.android.core.theme.ThemeSchemeVariant
import com.resdownload.android.core.theme.ThemeTone
import com.resdownload.android.core.theme.normalizeThemeSeedArgb
import com.resdownload.android.core.theme.seedColorScheme
import com.resdownload.android.core.theme.themeSeedFromTone
import com.resdownload.android.core.theme.themeToneFromArgb
import com.resdownload.android.domain.model.User
import com.resdownload.android.feature.profile.UserAccountSection

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun SettingsScreen(
    user: User,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    dynamicColorEnabled: Boolean = true,
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    themeSeedColorArgb: Int = DEFAULT_THEME_SEED_ARGB,
    themeSchemeVariant: ThemeSchemeVariant = ThemeSchemeVariant.TONAL_SPOT,
    onDynamicColorEnabledChange: (Boolean) -> Unit = {},
    onThemeSeedColorChange: (Int) -> Unit = {},
    onThemeSchemeVariantChange: (ThemeSchemeVariant) -> Unit = {},
    onResetThemeColor: () -> Unit = {},
    noticeState: NoticeUiState = NoticeUiState.Loading,
    onRetryNotice: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNotice by rememberSaveable { mutableStateOf(false) }
    var showLogout by rememberSaveable { mutableStateOf(false) }
    var showCustomColor by rememberSaveable { mutableStateOf(false) }
    var showSchemePicker by rememberSaveable { mutableStateOf(false) }
    val previewDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            UserAccountSection(
                user = user,
                onLogout = { showLogout = true },
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
            Text(
                text = "外观",
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val modes = ThemeMode.entries
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
            ListItem(
                headlineContent = { Text("莫奈自动取色") },
                supportingContent = {
                    Text(
                        when {
                            !dynamicColorAvailable -> "当前系统不支持，已使用自定义主题色"
                            dynamicColorEnabled -> "使用系统壁纸生成应用配色"
                            else -> "使用下方选择的主题色"
                        },
                    )
                },
                leadingContent = { SettingsIcon(Icons.Default.AutoAwesome) },
                trailingContent = {
                    val effectiveDynamicColor = dynamicColorAvailable && dynamicColorEnabled
                    Switch(
                        checked = effectiveDynamicColor,
                        onCheckedChange = null,
                        enabled = dynamicColorAvailable,
                        thumbContent = {
                            Icon(
                                imageVector = if (effectiveDynamicColor) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.Close
                                },
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        },
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                },
                modifier = Modifier
                    .semantics { contentDescription = "莫奈自动取色开关" }
                    .toggleable(
                        value = dynamicColorAvailable && dynamicColorEnabled,
                        enabled = dynamicColorAvailable,
                        role = Role.Switch,
                        onValueChange = onDynamicColorEnabledChange,
                    ),
            )
            AnimatedVisibility(
                visible = !dynamicColorAvailable || !dynamicColorEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ThemeColorEditor(
                    selectedSeedColorArgb = themeSeedColorArgb,
                    schemeVariant = themeSchemeVariant,
                    darkTheme = previewDarkTheme,
                    onSeedColorChange = onThemeSeedColorChange,
                    onSchemeVariant = { showSchemePicker = true },
                    onReset = onResetThemeColor,
                    onCustomColor = { showCustomColor = true },
                )
            }
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
                supportingContent = { Text("查看资源云盘的各项信息") },
                leadingContent = { SettingsIcon(Icons.Default.Info) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
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
    if (showCustomColor) {
        CustomThemeColorDialog(
            initialSeedColorArgb = themeSeedColorArgb,
            schemeVariant = themeSchemeVariant,
            darkTheme = previewDarkTheme,
            onDismiss = { showCustomColor = false },
            onConfirm = { seedColorArgb ->
                onThemeSeedColorChange(seedColorArgb)
                showCustomColor = false
            },
        )
    }

    if (showSchemePicker) {
        ThemeSchemeVariantDialog(
            selected = themeSchemeVariant,
            onDismiss = { showSchemePicker = false },
            onSelect = { variant ->
                onThemeSchemeVariantChange(variant)
                showSchemePicker = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeColorEditor(
    selectedSeedColorArgb: Int,
    schemeVariant: ThemeSchemeVariant,
    darkTheme: Boolean,
    onSeedColorChange: (Int) -> Unit,
    onSchemeVariant: () -> Unit,
    onReset: () -> Unit,
    onCustomColor: () -> Unit,
) {
    val normalizedSeed = normalizeThemeSeedArgb(selectedSeedColorArgb)
    val selectedPreset = ThemeSeedPreset.entries.firstOrNull {
        it.seedColorArgb == normalizedSeed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeColorHeading()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemeColorActions(schemeVariant, onSchemeVariant, onReset)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ThemeColorHeading()
                    Spacer(Modifier.weight(1f))
                    ThemeColorActions(schemeVariant, onSchemeVariant, onReset)
                }
            }
        }
        if (schemeVariant != ThemeSchemeVariant.MONOCHROME) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeSeedPreset.entries.forEach { preset ->
                    ThemePaletteTile(
                        seedColorArgb = preset.seedColorArgb,
                        schemeVariant = schemeVariant,
                        darkTheme = darkTheme,
                        selected = selectedPreset == preset,
                        contentDescription = "主题色 ${preset.label()}",
                        onClick = { onSeedColorChange(preset.seedColorArgb) },
                    )
                }
                ThemePaletteTile(
                    seedColorArgb = normalizedSeed,
                    schemeVariant = schemeVariant,
                    darkTheme = darkTheme,
                    selected = selectedPreset == null,
                    contentDescription = "自定义主题色",
                    showAdd = selectedPreset != null,
                    onClick = onCustomColor,
                )
            }
        }
    }
}

@Composable
private fun ThemeColorHeading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text("主题色彩", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ThemeColorActions(
    schemeVariant: ThemeSchemeVariant,
    onSchemeVariant: () -> Unit,
    onReset: () -> Unit,
) {
    FilledTonalButton(
        onClick = onSchemeVariant,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(schemeVariant.displayName)
    }
    IconButton(onClick = onReset) {
        Icon(Icons.Default.Refresh, contentDescription = "恢复默认主题色")
    }
}

@Composable
private fun ThemePaletteTile(
    seedColorArgb: Int,
    schemeVariant: ThemeSchemeVariant,
    darkTheme: Boolean,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    showAdd: Boolean = false,
) {
    val previewScheme = remember(seedColorArgb, schemeVariant, darkTheme) {
        seedColorScheme(seedColorArgb, darkTheme = darkTheme, variant = schemeVariant)
    }
    Surface(
        modifier = Modifier
            .size(72.dp)
            .semantics { this.contentDescription = contentDescription }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (showAdd) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                PalettePreview(
                    colors = listOf(
                        previewScheme.primary,
                        previewScheme.primaryContainer,
                        previewScheme.secondaryContainer,
                        previewScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.size(52.dp),
                )
            }
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSchemeVariantDialog(
    selected: ThemeSchemeVariant,
    onDismiss: () -> Unit,
    onSelect: (ThemeSchemeVariant) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color scheme") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ThemeSchemeVariant.entries.forEach { variant ->
                    val isSelected = variant == selected
                    ListItem(
                        headlineContent = { Text(variant.displayName) },
                        leadingContent = {
                            RadioButton(selected = isSelected, onClick = null)
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                Color.Transparent
                            },
                        ),
                        modifier = Modifier.selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(variant) },
                        ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun PalettePreview(colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(CircleShape)) {
        colors.take(4).forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = -90f + (index * 90f),
                sweepAngle = 90f,
                useCenter = true,
            )
        }
    }
}

@Composable
private fun CustomThemeColorDialog(
    initialSeedColorArgb: Int,
    schemeVariant: ThemeSchemeVariant,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialTone = remember(initialSeedColorArgb) { themeToneFromArgb(initialSeedColorArgb) }
    var hue by rememberSaveable(initialSeedColorArgb) { mutableFloatStateOf(initialTone.hue) }
    var chroma by rememberSaveable(initialSeedColorArgb) {
        mutableFloatStateOf(initialTone.chroma.coerceIn(0f, 100f))
    }
    var tone by rememberSaveable(initialSeedColorArgb) {
        mutableFloatStateOf(initialTone.tone.coerceIn(20f, 80f))
    }
    val seedColorArgb = themeSeedFromTone(ThemeTone(hue, chroma, tone))
    val previewScheme = remember(seedColorArgb, schemeVariant, darkTheme) {
        seedColorScheme(seedColorArgb, darkTheme = darkTheme, variant = schemeVariant)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Colorize, contentDescription = null) },
        title = { Text("自定义主题色") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PalettePreview(
                    colors = listOf(
                        previewScheme.primary,
                        previewScheme.primaryContainer,
                        previewScheme.secondaryContainer,
                        previewScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.size(88.dp),
                )
                Text(
                    text = "#%06X".format(seedColorArgb and 0xFFFFFF),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeToneSlider(
                    label = "色相",
                    value = hue,
                    valueRange = 0f..360f,
                    valueText = "${hue.toInt()}°",
                    steps = 71,
                    onValueChange = { hue = it },
                )
                if (schemeVariant.usesSourceChromaAndTone) {
                    ThemeToneSlider(
                        label = "色彩浓度",
                        value = chroma.coerceIn(0f, 100f),
                        valueRange = 0f..100f,
                        valueText = chroma.toInt().toString(),
                        steps = 19,
                        onValueChange = { chroma = it },
                    )
                    ThemeToneSlider(
                        label = "明度",
                        value = tone.coerceIn(20f, 80f),
                        valueRange = 20f..80f,
                        valueText = tone.toInt().toString(),
                        steps = 11,
                        onValueChange = { tone = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(seedColorArgb) }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ThemeToneSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}

@Composable
internal fun SettingsIcon(
    imageVector: ImageVector,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
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

private fun ThemeSeedPreset.label(): String = when (this) {
    ThemeSeedPreset.FOREST -> "森林"
    ThemeSeedPreset.INDIGO -> "靛蓝"
    ThemeSeedPreset.CORAL -> "珊瑚"
    ThemeSeedPreset.SKY -> "晴空"
    ThemeSeedPreset.OLIVE -> "橄榄"
    ThemeSeedPreset.CYAN -> "青色"
    ThemeSeedPreset.MINT -> "薄荷"
    ThemeSeedPreset.ROSE -> "玫瑰"
    ThemeSeedPreset.VIOLET -> "紫罗兰"
}
