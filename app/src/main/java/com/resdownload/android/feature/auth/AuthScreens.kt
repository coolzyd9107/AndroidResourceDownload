package com.resdownload.android.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.resdownload.android.BuildConfig
import com.resdownload.android.R
import com.resdownload.android.core.ui.AppIcon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    onGithubLogin: () -> Unit,
    onQqLogin: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    message: String? = null,
    policyAccepted: Boolean = false,
    onPolicyAccepted: () -> Unit = {},
    onOpenQqPrivacyPolicy: () -> Unit = {},
) {
    var agreementAccepted by rememberSaveable { mutableStateOf(policyAccepted) }
    var showAgreementError by rememberSaveable { mutableStateOf(false) }
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    val visibilityEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val visibilitySpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()

    LaunchedEffect(policyAccepted) {
        agreementAccepted = policyAccepted
    }

    fun updateAgreement(accepted: Boolean) {
        agreementAccepted = accepted
        if (accepted) {
            showAgreementError = false
            onPolicyAccepted()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(
                contentDescription = "$appName 应用图标",
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = appName,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.headlineLargeEmphasized,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "使用 GitHub 或 QQ 登录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (BuildConfig.DEMO_MODE) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "演示模式",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.size(12.dp))
            Button(
                onClick = {
                    if (agreementAccepted) onGithubLogin() else showAgreementError = true
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MediumContainerHeight),
                enabled = !busy,
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.MediumIconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                Text(
                    text = "使用 GitHub 登录",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
            FilledTonalButton(
                onClick = {
                    if (agreementAccepted) onQqLogin() else showAgreementError = true
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MediumContainerHeight),
                enabled = !busy,
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qq),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.MediumIconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                Text(
                    text = "使用 QQ 登录",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }

            AnimatedVisibility(
                visible = busy,
                enter = fadeIn(visibilityEffectsSpec) + expandVertically(visibilitySpatialSpec),
                exit = fadeOut(visibilityEffectsSpec) + shrinkVertically(visibilitySpatialSpec),
            ) {
                ProcessingStatus("正在验证身份")
            }
            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn(visibilityEffectsSpec) + expandVertically(visibilitySpatialSpec),
                exit = fadeOut(visibilityEffectsSpec) + shrinkVertically(visibilitySpatialSpec),
            ) {
                message?.let { InlineMessage(it) }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = agreementAccepted,
                            role = SemanticsRole.Checkbox,
                            onValueChange = ::updateAgreement,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = agreementAccepted,
                        onCheckedChange = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "我已阅读并同意",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = { showPolicy = true },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "用户协议与隐私政策",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showAgreementError,
                enter = fadeIn(visibilityEffectsSpec) + expandVertically(visibilitySpatialSpec),
                exit = fadeOut(visibilityEffectsSpec) + shrinkVertically(visibilitySpatialSpec),
            ) {
                InlineMessage("请先同意用户协议与隐私政策")
            }
        }
    }

    if (showPolicy) {
        AlertDialog(
            onDismissRequest = { showPolicy = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text("用户协议与隐私政策") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "登录即表示你同意必要的账号验证与文件访问规则。" +
                            "QQ 登录由深圳市腾讯计算机系统有限公司提供的 QQ 互联 SDK 实现，" +
                            "SDK 会处理设备型号和手机 QQ 安装情况，用于拉起手机 QQ 完成账号授权。" +
                            "授权凭据将发送至本应用后端进行身份校验。",
                    )
                    TextButton(
                        onClick = onOpenQqPrivacyPolicy,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("查看 QQ 互联 SDK 隐私保护声明")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateAgreement(true)
                        showPolicy = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("同意")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPolicy = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("关闭")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingStatus(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
@Composable
private fun InlineMessage(
    text: String,
    isError: Boolean = true,
    icon: ImageVector = Icons.Default.ErrorOutline,
) {
    val containerColor: Color
    val contentColor: Color
    if (isError) {
        containerColor = MaterialTheme.colorScheme.errorContainer
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    } else {
        containerColor = MaterialTheme.colorScheme.primaryContainer
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}
