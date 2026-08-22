package link.mczihan.androidResourceDownload.feature.auth

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.domain.model.Role

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    onGithubLogin: () -> Unit,
    onEmailLogin: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    message: String? = null,
    onPolicyAccepted: () -> Unit = {},
) {
    var agreementAccepted by rememberSaveable { mutableStateOf(false) }
    var showAgreementError by rememberSaveable { mutableStateOf(false) }
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    val visibilityEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val visibilitySpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()

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
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialShapes.Cookie6Sided.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                text = "资源云盘",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.headlineLargeEmphasized,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "使用 GitHub 或邮箱验证码登录",
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
                    .heightIn(min = ButtonDefaults.LargeContainerHeight),
                enabled = !busy,
                contentPadding = ButtonDefaults.LargeContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.LargeIconSpacing))
                Text(
                    text = "使用 GitHub 登录",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
            FilledTonalButton(
                onClick = {
                    if (agreementAccepted) onEmailLogin() else showAgreementError = true
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MediumContainerHeight),
                enabled = !busy,
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.MediumIconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                Text(
                    text = "使用邮箱验证码",
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "我已阅读并同意",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = { showPolicy = true },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "用户协议与隐私政策",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                Text(
                    "登录即表示你同意必要的账号验证与文件访问规则。" +
                        "使用纯数字 QQ 邮箱登录时，QQ 号将发送给腾讯 QQ 服务，" +
                        "仅用于加载头像和昵称；获取失败时显示默认头像和邮箱前缀。",
                )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmailVerificationScreen(
    onBack: () -> Unit,
    onVerified: (email: String, role: Role) -> Unit,
    modifier: Modifier = Modifier,
    onRequestCode: ((email: String) -> Unit)? = null,
    onLogin: ((email: String, code: String) -> Unit)? = null,
    busy: Boolean = false,
    message: String? = null,
    codeSentEmail: String? = null,
    codeExpiresInSeconds: Int? = null,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var localCodeSent by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var codeVisible by rememberSaveable { mutableStateOf(false) }
    val effectiveCodeSent = localCodeSent || codeSentEmail?.equals(email.trim(), ignoreCase = true) == true
    LaunchedEffect(codeSentEmail, email) {
        if (codeSentEmail?.equals(email.trim(), ignoreCase = true) == true) {
            localCodeSent = true
        }
    }
    val visibilityEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val visibilitySpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val submitLogin = {
        val role = roleForAllowedEmail(email)
        when {
            role == null -> errorMessage = "邮箱域名不受支持"
            !effectiveCodeSent -> errorMessage = "请先获取验证码"
            code.length != 6 -> errorMessage = "请输入 6 位验证码"
            onLogin != null -> onLogin(email.trim(), code)
            else -> onVerified(email.trim(), role)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("邮箱登录") },
                subtitle = {
                    Text(if (effectiveCodeSent) "输入验证码" else "验证邮箱地址")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = if (effectiveCodeSent) "检查你的邮箱" else "使用邮箱继续",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(
                    text = if (effectiveCodeSent) {
                        "验证码已发送至 ${email.trim()}"
                    } else {
                        "支持 qq.com 和 mczihan.link 邮箱"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        if (it.trim() != email.trim()) {
                            localCodeSent = false
                            code = ""
                            codeVisible = false
                        }
                        email = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("邮箱") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    isError = errorMessage != null && roleForAllowedEmail(email) == null,
                    supportingText = { Text("qq.com 或 mczihan.link") },
                )
                FilledTonalButton(
                    onClick = {
                        if (roleForAllowedEmail(email) == null) {
                            errorMessage = "请输入允许登录的邮箱"
                        } else if (onRequestCode != null) {
                            onRequestCode(email.trim())
                        } else {
                            localCodeSent = true
                            errorMessage = null
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonDefaults.MediumContainerHeight),
                    enabled = !busy,
                    contentPadding = ButtonDefaults.MediumContentPadding,
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.MediumIconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
                    Text(
                        text = if (effectiveCodeSent) "重新获取验证码" else "获取验证码",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }

                AnimatedVisibility(
                    visible = effectiveCodeSent,
                    enter = fadeIn(visibilityEffectsSpec) + expandVertically(visibilitySpatialSpec),
                    exit = fadeOut(visibilityEffectsSpec) + shrinkVertically(visibilitySpatialSpec),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InlineMessage(
                            text = verificationExpiryText(codeExpiresInSeconds),
                            isError = false,
                            icon = Icons.Default.CheckCircle,
                        )
                        OutlinedTextField(
                            value = code,
                            onValueChange = { value ->
                                code = value.filter(Char::isDigit).take(6)
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("6 位验证码") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { codeVisible = !codeVisible }) {
                                    Icon(
                                        imageVector = if (codeVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = if (codeVisible) {
                                            "隐藏验证码"
                                        } else {
                                            "显示验证码"
                                        },
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (codeVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { if (!busy) submitLogin() },
                            ),
                            supportingText = { Text("仅保留前 6 位数字") },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = busy,
                    enter = fadeIn(visibilityEffectsSpec) + expandVertically(visibilitySpatialSpec),
                    exit = fadeOut(visibilityEffectsSpec) + shrinkVertically(visibilitySpatialSpec),
                ) {
                    ProcessingStatus("正在处理登录请求")
                }
                AnimatedVisibility(
                    visible = errorMessage != null || message != null,
                    enter = fadeIn(visibilityEffectsSpec) + expandVertically(visibilitySpatialSpec),
                    exit = fadeOut(visibilityEffectsSpec) + shrinkVertically(visibilitySpatialSpec),
                ) {
                    (errorMessage ?: message)?.let { InlineMessage(it) }
                }
                Button(
                    onClick = submitLogin,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ButtonDefaults.LargeContainerHeight),
                    enabled = !busy,
                    contentPadding = ButtonDefaults.LargeContentPadding,
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.LargeIconSpacing))
                    Text("登录", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
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

private fun verificationExpiryText(expiresInSeconds: Int?): String = when {
    expiresInSeconds == null -> "验证码已发送，请检查邮箱"
    expiresInSeconds >= 60 && expiresInSeconds % 60 == 0 ->
        "验证码已发送，${expiresInSeconds / 60} 分钟内有效"
    else -> "验证码已发送，$expiresInSeconds 秒内有效"
}
