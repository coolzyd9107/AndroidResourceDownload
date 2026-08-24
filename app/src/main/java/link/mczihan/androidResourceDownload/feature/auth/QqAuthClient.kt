package link.mczihan.androidResourceDownload.feature.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.tencent.connect.common.Constants
import com.tencent.open.utils.k as QqSdkConfig
import com.tencent.tauth.IUiListener
import com.tencent.tauth.Tencent
import com.tencent.tauth.UiError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import link.mczihan.androidResourceDownload.BuildConfig
import org.json.JSONObject

sealed interface QqAuthResult {
    data class Success(val credential: QqCredential) : QqAuthResult
    data class Error(val message: String) : QqAuthResult
    data object Cancelled : QqAuthResult
}

data class QqCredential(
    val accessToken: String,
    val openId: String,
) {
    override fun toString(): String = "QqCredential(accessToken=<redacted>, openId=<redacted>)"
}

@Singleton
class QqAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _events = MutableStateFlow<QqAuthResult?>(null)
    val events: StateFlow<QqAuthResult?> = _events.asStateFlow()

    private val pending = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        complete(QqAuthResult.Error("QQ 授权已超时，请重试"))
    }
    private var tencent: Tencent? = null
    private val listener = object : IUiListener {
        override fun onComplete(response: Any?) {
            val credential = (response as? JSONObject)?.toCredential()
            complete(
                credential?.let(QqAuthResult::Success)
                    ?: QqAuthResult.Error("QQ 授权结果无效，请重试"),
            )
        }

        override fun onError(error: UiError?) {
            val suffix = error?.errorCode?.let { "（错误码 $it）" }.orEmpty()
            complete(QqAuthResult.Error("QQ 授权失败$suffix"))
        }

        override fun onCancel() {
            complete(QqAuthResult.Cancelled)
        }

        override fun onWarning(code: Int) = Unit
    }

    init {
        clearProviderStorage()
    }

    /** Returns an error message, or null after the QQ client was launched. */
    fun launch(activity: Activity): String? {
        if (!pending.compareAndSet(false, true)) return "QQ 登录正在进行"
        if (!validAppId(BuildConfig.QQ_APP_ID)) return failLaunch("QQ AppID 配置无效")

        Tencent.setIsPermissionGranted(true, Build.MODEL)
        Tencent.resetTargetAppInfoCache()
        val instance = try {
            Tencent.createInstance(
                BuildConfig.QQ_APP_ID,
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
            )
        } catch (_: Exception) {
            null
        } ?: return failLaunch("QQ 登录组件初始化失败")
        tencent = instance

        if (!instance.isQQInstalled(context)) {
            return failLaunch("未安装手机 QQ，请安装后重试")
        }
        if (!instance.isSupportSSOLogin(activity)) {
            return failLaunch("当前手机 QQ 版本不支持授权登录，请升级后重试")
        }
        val webLoginForced = runCatching {
            QqSdkConfig.a(context, BuildConfig.QQ_APP_ID).b(QQ_WEB_LOGIN_CONFIG)
        }.getOrDefault(true)
        if (webLoginForced || !hasNativeAuthActivity()) {
            return failLaunch("当前环境无法使用手机 QQ 客户端授权")
        }

        clearProviderSession()
        mainHandler.postDelayed(timeout, LOGIN_TIMEOUT_MILLIS)
        val launchMode = try {
            instance.login(activity, QQ_SCOPE, listener, false)
        } catch (_: Exception) {
            return failLaunch("无法拉起手机 QQ")
        }
        return when (launchMode) {
            LOGIN_FAILED -> failLaunch("无法拉起手机 QQ")
            LOGIN_H5 -> failLaunch("当前环境无法使用手机 QQ 授权")
            else -> null
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != Constants.REQUEST_LOGIN) return false
        val handled = Tencent.onActivityResultData(requestCode, resultCode, data, listener)
        if (!handled) complete(QqAuthResult.Error("未收到有效的 QQ 授权结果，请重试"))
        return handled
    }

    fun clearSession() {
        pending.set(false)
        mainHandler.removeCallbacks(timeout)
        _events.value = null
        clearProviderSession()
    }

    fun consume(result: QqAuthResult) {
        _events.compareAndSet(result, null)
    }

    private fun complete(result: QqAuthResult) {
        val wasPending = pending.compareAndSet(true, false)
        mainHandler.removeCallbacks(timeout)
        val providerStorageCleared = clearProviderSession()
        if (!wasPending) return
        _events.value = if (result is QqAuthResult.Success && !providerStorageCleared) {
            QqAuthResult.Error("无法安全清理 QQ 授权凭据，请重试")
        } else {
            result
        }
    }

    private fun failLaunch(message: String): String {
        pending.set(false)
        mainHandler.removeCallbacks(timeout)
        clearProviderSession()
        return message
    }

    private fun clearProviderSession(): Boolean {
        runCatching { tencent?.logout(context) }
        // OpenSDK saves provider tokens before invoking the app callback; remove them synchronously.
        return clearProviderStorage()
    }

    private fun clearProviderStorage(): Boolean = runCatching {
        context.getSharedPreferences(QQ_TOKEN_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }.getOrDefault(false)

    private fun hasNativeAuthActivity(): Boolean = Intent()
        .setClassName(QQ_PACKAGE, QQ_AUTH_ACTIVITY)
        .resolveActivity(context.packageManager) != null

    private fun JSONObject.toCredential(): QqCredential? {
        if (optInt("ret", -1) != 0) return null
        val accessToken = optString("access_token").trim()
        val openId = optString("openid").trim()
        if (!validCredentialPart(accessToken, 16, 4_096)) return null
        if (!validCredentialPart(openId, 8, 256)) return null
        return QqCredential(accessToken, openId)
    }

    private fun validCredentialPart(value: String, minLength: Int, maxLength: Int): Boolean =
        value.length in minLength..maxLength && value.none { it.isWhitespace() || it.isISOControl() }

    private fun validAppId(value: String): Boolean =
        value.isNotEmpty() && value.all { it in '0'..'9' }

    private companion object {
        const val QQ_SCOPE = "get_user_info"
        const val LOGIN_FAILED = -1
        const val LOGIN_H5 = 2
        const val LOGIN_TIMEOUT_MILLIS = 5 * 60 * 1_000L
        const val QQ_TOKEN_PREFERENCES = "token_info_file"
        const val QQ_WEB_LOGIN_CONFIG = "C_LoginWeb"
        const val QQ_PACKAGE = "com.tencent.mobileqq"
        const val QQ_AUTH_ACTIVITY = "com.tencent.open.agent.AgentActivity"
    }
}
