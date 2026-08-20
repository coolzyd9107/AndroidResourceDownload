package link.mczihan.androidResourceDownload.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.net.URI
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayEmail = user.email?.trim().takeUnless { it.isNullOrEmpty() } ?: "未提供邮箱"
    val displayName = user.name?.trim().takeUnless { it.isNullOrEmpty() }
        ?: when (user.loginType) {
            LoginType.GITHUB -> "GitHub 用户"
            LoginType.EMAIL -> user.email
                ?.substringBefore('@')
                ?.trim()
                .takeUnless { it.isNullOrEmpty() }
                ?: "邮箱用户"
        }
    val avatarUrl = user.profileAvatarUrl()
    val context = LocalContext.current
    val avatarRequest = remember(context, avatarUrl) {
        avatarUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }
    var avatarLoadFailed by remember(avatarRequest) { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .semantics {
                        contentDescription = if (avatarRequest == null || avatarLoadFailed) {
                            "默认用户头像"
                        } else {
                            "用户头像"
                        }
                    },
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                if (avatarRequest == null) {
                    DefaultAvatar()
                } else {
                    SubcomposeAsyncImage(
                        model = avatarRequest,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = { DefaultAvatar() },
                        error = { DefaultAvatar() },
                        onLoading = { avatarLoadFailed = false },
                        onSuccess = { avatarLoadFailed = false },
                        onError = { avatarLoadFailed = true },
                    )
                }
            }
            Text(displayName, style = MaterialTheme.typography.headlineSmall)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = if (user.role == Role.ADMIN) "管理员" else "普通用户",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileLine("邮箱", displayEmail)
                ProfileLine(
                    "登录方式",
                    when (user.loginType) {
                        LoginType.GITHUB -> "GitHub"
                        LoginType.EMAIL -> "邮箱验证码"
                    },
                )
            }
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("退出登录")
                }
            }
        }
    }
}

@Composable
private fun DefaultAvatar() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

internal fun User.profileAvatarUrl(): String? {
    if (loginType == LoginType.EMAIL) {
        val parts = email?.trim()?.split('@')
        if (parts?.size == 2 && parts[1].equals("qq.com", ignoreCase = true)) {
            val qqNumber = parts[0]
            return if (qqNumber.isNotEmpty() && qqNumber.all { it in '0'..'9' }) {
                "https://q1.qlogo.cn/g?b=qq&nk=$qqNumber&s=640"
            } else {
                null
            }
        }
    }

    val normalizedUrl = avatarUrl?.trim()?.takeIf { it.none(Char::isWhitespace) } ?: return null
    val uri = runCatching { URI(normalizedUrl) }.getOrNull() ?: return null
    return normalizedUrl.takeIf {
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("avatars.githubusercontent.com", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443)
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
