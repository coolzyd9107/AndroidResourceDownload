package link.mczihan.androidResourceDownload.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.net.URI
import link.mczihan.androidResourceDownload.core.common.qqNumberFromEmail
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserAccountSection(
    user: User,
    qqNickname: String? = null,
    allowQqLookup: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val displayEmail = user.email?.trim().takeUnless { it.isNullOrEmpty() } ?: "未提供邮箱"
    val displayName = user.accountDisplayName(qqNickname)
    val loginMethod = when (user.loginType) {
        LoginType.GITHUB -> "GitHub"
        LoginType.EMAIL -> "邮箱验证码"
    }
    val avatarUrl = user.profileAvatarUrl(allowQqLookup)
    val context = LocalContext.current
    val avatarRequest = remember(context, avatarUrl) {
        avatarUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .apply {
                    if (it.startsWith("https://q1.qlogo.cn/")) {
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
                .build()
        }
    }
    val avatarImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            }
            .build()
    }
    DisposableEffect(avatarImageLoader) {
        onDispose(avatarImageLoader::shutdown)
    }
    var avatarLoadFailed by remember(avatarRequest) { mutableStateOf(false) }
    val nameEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .semantics {
                            contentDescription = if (avatarRequest == null || avatarLoadFailed) {
                                "默认用户头像"
                            } else {
                                "用户头像"
                            }
                        },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    if (avatarRequest == null) {
                        DefaultAvatar()
                    } else {
                        SubcomposeAsyncImage(
                            model = avatarRequest,
                            imageLoader = avatarImageLoader,
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
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedContent(
                        targetState = displayName,
                        transitionSpec = {
                            fadeIn(nameEffectsSpec).togetherWith(fadeOut(nameEffectsSpec))
                        },
                        label = "accountDisplayName",
                    ) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (user.role == Role.ADMIN) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ) {
                        Text(
                            text = if (user.role == Role.ADMIN) "管理员" else "普通用户",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = if (user.role == Role.ADMIN) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AccountDetail(
                icon = Icons.Default.Email,
                label = "邮箱",
                value = displayEmail,
            )
            AccountDetail(
                icon = Icons.Default.Key,
                label = "登录方式",
                value = loginMethod,
            )
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
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountDetail(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun User.accountDisplayName(qqNickname: String?): String = when (loginType) {
    LoginType.GITHUB -> name?.trim().takeUnless { it.isNullOrEmpty() } ?: "GitHub 用户"
    LoginType.EMAIL -> qqNickname?.trim().takeUnless { it.isNullOrEmpty() }
        ?: email?.substringBefore('@')?.trim().takeUnless { it.isNullOrEmpty() }
        ?: name?.trim().takeUnless { it.isNullOrEmpty() }
        ?: "邮箱用户"
}

internal fun User.profileAvatarUrl(allowQqLookup: Boolean = true): String? {
    if (allowQqLookup && loginType == LoginType.EMAIL) {
        qqNumberFromEmail(email)?.let { qqNumber ->
            return "https://q1.qlogo.cn/g?b=qq&nk=$qqNumber&s=640"
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
