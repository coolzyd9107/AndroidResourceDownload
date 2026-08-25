package com.resdownload.android.feature.auth

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.resdownload.android.BuildConfig
import com.resdownload.android.data.auth.AuthRepository
import com.resdownload.android.data.settings.PrivacyConsentRepository
import com.resdownload.android.domain.model.AuthSession
import com.resdownload.android.domain.webdav.WebDavCredentialProvider
import com.resdownload.android.service.DownloadQueueController
import com.resdownload.android.service.UploadQueueController

sealed interface AuthUiState {
    data object Restoring : AuthUiState
    data object Anonymous : AuthUiState
    data object Authenticating : AuthUiState
    data object LoggingOut : AuthUiState
    data class Authenticated(val session: AuthSession) : AuthUiState
    data class Error(val message: String, val recoverableState: AuthUiState = Anonymous) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val pendingOAuthStore: PendingOAuthStore,
    private val credentialProvider: WebDavCredentialProvider,
    private val oauthCallbackBus: OAuthCallbackBus,
    private val downloadQueueController: DownloadQueueController,
    private val uploadQueueController: UploadQueueController,
    private val privacyConsentRepository: PrivacyConsentRepository,
    private val qqAuthClient: QqAuthClient,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Restoring)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    val privacyConsentAccepted = privacyConsentRepository.accepted.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false,
    )
    private val authenticationMutex = Mutex()
    private var activeProvider: AuthProvider? = null
    private var policyAcceptedInProcess = false

    init {
        viewModelScope.launch { restoreSession() }
        viewModelScope.launch {
            oauthCallbackBus.events.collect { uri ->
                uri?.let {
                    oauthCallbackBus.consume(it)
                    handleGithubCallback(it)
                }
            }
        }
        viewModelScope.launch {
            qqAuthClient.events.collect { result ->
                result?.let {
                    qqAuthClient.consume(it)
                    handleQqResult(it)
                }
            }
        }
    }

    fun restore() {
        viewModelScope.launch { restoreSession() }
    }

    private suspend fun restoreSession() {
        _state.value = AuthUiState.Restoring
        val restoredState = try {
            repository.restoreSession()?.let(AuthUiState::Authenticated) ?: AuthUiState.Anonymous
        } catch (error: Exception) {
            AuthUiState.Error(error.userMessage(), AuthUiState.Anonymous)
        }
        if (_state.value is AuthUiState.Restoring) _state.value = restoredState
    }

    fun beginQq(activity: Activity) {
        if (_state.value is AuthUiState.Authenticated ||
            _state.value is AuthUiState.Authenticating ||
            _state.value is AuthUiState.LoggingOut
        ) return
        if (!privacyConsentAccepted.value && !policyAcceptedInProcess) {
            return setError("请先同意用户协议与隐私政策")
        }
        if (backendBaseUrl() == null) {
            return setError("未配置有效的后端 API 地址")
        }
        if (activeProvider == AuthProvider.GITHUB) {
            pendingOAuthStore.clear()
        }
        activeProvider = AuthProvider.QQ
        _state.value = AuthUiState.Authenticating
        qqAuthClient.launch(activity)?.let { error ->
            activeProvider = null
            setError(error)
        }
    }

    fun beginGithub(): String? {
        if (_state.value is AuthUiState.Authenticated ||
            _state.value is AuthUiState.Authenticating ||
            _state.value is AuthUiState.LoggingOut
        ) return null
        val baseUrl = backendBaseUrl() ?: run {
            setError("未配置有效的后端 API 地址")
            return null
        }
        val transaction = PendingOAuth(
            appState = Pkce.generateState(),
            verifier = Pkce.generateVerifier(),
            createdAtMillis = System.currentTimeMillis(),
        )
        if (runCatching { pendingOAuthStore.save(transaction) }.isFailure) {
            setError("无法保存 GitHub 登录状态")
            return null
        }
        activeProvider = AuthProvider.GITHUB
        return baseUrl.newBuilder()
            .addPathSegments("api/v1/auth/github/start")
            .addQueryParameter("code_challenge", Pkce.challengeFor(transaction.verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("app_state", transaction.appState)
            .build()
            .toString()
    }

    fun handleGithubCallback(uri: Uri) {
        if (_state.value is AuthUiState.Authenticated || _state.value is AuthUiState.LoggingOut) return
        if (activeProvider == AuthProvider.QQ) return
        if (uri.scheme != BuildConfig.OAUTH_CALLBACK_SCHEME ||
            uri.host != "oauth" || uri.path != "/callback"
        ) return setError("GitHub 回调地址无效")
        val state = uri.getQueryParameter("app_state") ?: return setError("GitHub 回调缺少状态")
        val pending = pendingOAuthStore.read()
            ?.takeIf { it.appState == state && System.currentTimeMillis() - it.createdAtMillis in 0..OAUTH_MAX_AGE }
            ?: return setError("GitHub 登录状态已失效")
        activeProvider = AuthProvider.GITHUB
        uri.getQueryParameter("error")?.let {
            pendingOAuthStore.clear()
            activeProvider = null
            return setError("GitHub 登录被取消")
        }
        val code = uri.getQueryParameter("code") ?: return setError("GitHub 回调缺少授权码")
        viewModelScope.launch {
            authenticationMutex.withLock {
                if (_state.value is AuthUiState.Authenticated || activeProvider != AuthProvider.GITHUB) {
                    return@withLock
                }
                _state.value = AuthUiState.Authenticating
                _state.value = try {
                    repository.completeGitHubLogin(
                        code,
                        pending.verifier,
                    ).asAuthenticatedState().also { pendingOAuthStore.clear() }
                } catch (error: Exception) {
                    AuthUiState.Error(error.userMessage())
                } finally {
                    activeProvider = null
                }
            }
        }
    }

    fun logout() {
        val session = (_state.value as? AuthUiState.Authenticated)?.session ?: return
        val ownerId = session.user.id
        downloadQueueController.block(ownerId)
        uploadQueueController.block(ownerId)
        _state.value = AuthUiState.LoggingOut
        viewModelScope.launch {
            val stopJob = launch {
                runCatching { downloadQueueController.stop(ownerId) }
            }
            val stopUploadJob = launch {
                runCatching { uploadQueueController.stop(ownerId) }
            }
            stopJob.join()
            stopUploadJob.join()
            credentialProvider.clear()
            qqAuthClient.clearSession()
            pendingOAuthStore.clear()
            activeProvider = null
            runCatching { repository.logout() }
            _state.value = AuthUiState.Anonymous
        }
    }

    fun reportError(message: String) = setError(message)

    fun reportGithubLaunchFailure() {
        if (activeProvider != AuthProvider.GITHUB) return
        pendingOAuthStore.clear()
        activeProvider = null
        setError("无法打开 GitHub 授权页面")
    }

    fun acceptPrivacyPolicy() {
        policyAcceptedInProcess = true
        viewModelScope.launch { privacyConsentRepository.acceptCurrentPolicy() }
    }

    private suspend fun AuthSession.asAuthenticatedState(): AuthUiState.Authenticated {
        credentialProvider.clear()
        return AuthUiState.Authenticated(this)
    }

    private fun handleQqResult(result: QqAuthResult) {
        if (_state.value is AuthUiState.Authenticated || _state.value is AuthUiState.LoggingOut) return
        if (activeProvider == AuthProvider.GITHUB) return
        activeProvider = AuthProvider.QQ
        when (result) {
            QqAuthResult.Cancelled -> {
                activeProvider = null
                setError("QQ 登录已取消")
            }
            is QqAuthResult.Error -> {
                activeProvider = null
                setError(result.message)
            }
            is QqAuthResult.Success -> viewModelScope.launch {
                authenticationMutex.withLock {
                    if (_state.value is AuthUiState.Authenticated ||
                        _state.value is AuthUiState.LoggingOut ||
                        activeProvider != AuthProvider.QQ
                    ) return@withLock
                    _state.value = AuthUiState.Authenticating
                    _state.value = try {
                        repository.loginWithQq(
                            accessToken = result.credential.accessToken,
                            openId = result.credential.openId,
                        ).asAuthenticatedState()
                    } catch (error: Exception) {
                        AuthUiState.Error(error.userMessage())
                    } finally {
                        activeProvider = null
                    }
                }
            }
        }
    }

    private fun backendBaseUrl() = runCatching {
        BuildConfig.API_BASE_URL.trim().let {
            if (it.endsWith('/')) it else "$it/"
        }.toHttpUrl()
    }.getOrNull()?.takeIf {
        it.isHttps && !it.host.endsWith("example.invalid", ignoreCase = true)
    }

    private fun setError(message: String) {
        _state.value = AuthUiState.Error(message)
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "请求失败，请稍后重试"

    private companion object {
        const val OAUTH_MAX_AGE = 10 * 60 * 1_000L
    }

    private enum class AuthProvider {
        GITHUB,
        QQ,
    }
}
