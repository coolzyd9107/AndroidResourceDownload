package link.mczihan.androidResourceDownload.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.domain.model.Role

@Composable
fun LoginScreen(
    onGithubLogin: () -> Unit,
    onEmailLogin: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    message: String? = null,
) {
    var agreementAccepted by rememberSaveable { mutableStateOf(false) }
    var showAgreementError by rememberSaveable { mutableStateOf(false) }
    var showPolicy by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "资源下载",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "任意 GitHub 账号均可登录，访问权限由服务端分配。邮箱登录仅支持 qq.com 和 mczihan.link。",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (BuildConfig.DEMO_MODE) {
                    "当前为演示模式，登录后使用演示账号和数据。"
                } else {
                    "真实登录已启用：邮箱验证码与 GitHub 授权由后端验证。"
                },
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (BuildConfig.DEMO_MODE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    if (agreementAccepted) onGithubLogin() else showAgreementError = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("使用 GitHub 登录")
            }
            OutlinedButton(
                onClick = {
                    if (agreementAccepted) onEmailLogin() else showAgreementError = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = !busy,
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("使用邮箱验证码")
            }
            message?.let {
                Text(
                    text = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = agreementAccepted,
                    onCheckedChange = {
                        agreementAccepted = it
                        if (it) showAgreementError = false
                    },
                )
                Text(
                    text = "我已阅读并同意",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                TextButton(
                    onClick = { showPolicy = true },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        text = "用户协议与隐私政策",
                        maxLines = 1,
                    )
                }
            }
            if (showAgreementError) {
                Text(
                    text = "请先同意用户协议与隐私政策",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showPolicy) {
        AlertDialog(
            onDismissRequest = { showPolicy = false },
            title = { Text("用户协议与隐私政策") },
            text = {
                Text(
                    "登录即表示你同意必要的账号验证与文件访问规则。" +
                        "使用纯数字 QQ 邮箱登录时，QQ 号将发送给腾讯 QQ 头像服务，仅用于加载头像；" +
                        "加载失败时显示默认头像。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        agreementAccepted = true
                        showAgreementError = false
                        showPolicy = false
                    },
                ) {
                    Text("同意")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPolicy = false }) {
                    Text("关闭")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var localCodeSent by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveCodeSent = localCodeSent || codeSentEmail?.equals(email.trim(), ignoreCase = true) == true

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("邮箱登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("邮箱验证", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "支持 qq.com 和 mczihan.link 邮箱",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = email,
                onValueChange = {
                    if (it.trim() != email.trim()) {
                        localCodeSent = false
                        code = ""
                    }
                    email = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = errorMessage != null && roleForAllowedEmail(email) == null,
            )
            OutlinedButton(
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
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(if (effectiveCodeSent) "重新获取验证码" else "获取验证码")
            }
            if (effectiveCodeSent) {
                Text(
                    text = "验证码已发送，请在 5 分钟内输入",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                value = code,
                onValueChange = { value ->
                    code = value.filter(Char::isDigit).take(6)
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("6 位验证码") },
                singleLine = true,
                enabled = effectiveCodeSent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            (errorMessage ?: message)?.let { text ->
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = {
                    val role = roleForAllowedEmail(email)
                    when {
                        role == null -> errorMessage = "邮箱域名不受支持"
                        !effectiveCodeSent -> errorMessage = "请先获取验证码"
                        code.length != 6 -> errorMessage = "请输入 6 位验证码"
                        onLogin != null -> onLogin(email.trim(), code)
                        else -> onVerified(email.trim(), role)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(if (busy) "登录中…" else "登录")
            }
        }
    }
}
