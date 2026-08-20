package link.mczihan.androidResourceDownload.feature.auth

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
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.data.auth.AuthRepository
import link.mczihan.androidResourceDownload.data.profile.QqNicknameRepository
import link.mczihan.androidResourceDownload.data.settings.PrivacyConsentRepository
import link.mczihan.androidResourceDownload.domain.model.AuthSession
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.service.DownloadQueueController

sealed interface AuthUiState {
    data object Restoring : AuthUiState
    data object Anonymous : AuthUiState
    data object SendingCode : AuthUiState
    data class AwaitingCode(val email: String, val expiresInSeconds: Int) : AuthUiState
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
    private val qqNicknameRepository: QqNicknameRepository,
    private val privacyConsentRepository: PrivacyConsentRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Restoring)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    val privacyConsentAccepted = privacyConsentRepository.accepted.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        false,
    )
    private val githubCallbackMutex = Mutex()

    init {
        viewModelScope.launch {
            restoreSession()
            oauthCallbackBus.events.collect { uri ->
                uri?.let {
                    oauthCallbackBus.consume(it)
                    handleGithubCallback(it)
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

    fun requestCode(email: String) {
        if (_state.value is AuthUiState.LoggingOut) return
        val normalizedEmail = email.trim()
        viewModelScope.launch {
            _state.value = AuthUiState.SendingCode
            _state.value = try {
                AuthUiState.AwaitingCode(
                    email = normalizedEmail,
                    expiresInSeconds = repository.requestEmailCode(normalizedEmail).expiresInSeconds,
                )
            } catch (error: Exception) {
                AuthUiState.Error(error.userMessage())
            }
        }
    }

    fun loginWithEmail(email: String, code: String) {
        if (_state.value is AuthUiState.LoggingOut) return
        val previousState = _state.value.let { state ->
            if (state is AuthUiState.Error) state.recoverableState else state
        }
        val recoverableState = (previousState as? AuthUiState.AwaitingCode)
            ?.takeIf { it.email.equals(email.trim(), ignoreCase = true) }
            ?: AuthUiState.Anonymous
        viewModelScope.launch {
            _state.value = AuthUiState.Authenticating
            _state.value = try {
                repository.loginWithEmail(email, code).asAuthenticatedState()
            } catch (error: Exception) {
                AuthUiState.Error(error.userMessage(), recoverableState)
            }
        }
    }

    fun beginGithub(): String? {
        if (_state.value is AuthUiState.LoggingOut) return null
        if (BuildConfig.API_BASE_URL.contains("example.invalid")) return null
        val baseUrl = runCatching {
            BuildConfig.API_BASE_URL.trim().let {
                if (it.endsWith('/')) it else "$it/"
            }.toHttpUrl()
        }.getOrNull() ?: return null
        val transaction = PendingOAuth(
            appState = Pkce.generateState(),
            verifier = Pkce.generateVerifier(),
            createdAtMillis = System.currentTimeMillis(),
        )
        pendingOAuthStore.save(transaction)
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
        if (uri.scheme != "link.mczihan.androidresourcedownload" ||
            uri.host != "oauth" || uri.path != "/callback"
        ) return setError("GitHub 回调地址无效")
        val state = uri.getQueryParameter("app_state") ?: return setError("GitHub 回调缺少状态")
        val pending = pendingOAuthStore.read()
            ?.takeIf { it.appState == state && System.currentTimeMillis() - it.createdAtMillis in 0..OAUTH_MAX_AGE }
            ?: return setError("GitHub 登录状态已失效")
        uri.getQueryParameter("error")?.let {
            pendingOAuthStore.clear()
            return setError("GitHub 登录被取消")
        }
        val code = uri.getQueryParameter("code") ?: return setError("GitHub 回调缺少授权码")
        viewModelScope.launch {
            githubCallbackMutex.withLock {
                if (_state.value is AuthUiState.Authenticated) return@withLock
                _state.value = AuthUiState.Authenticating
                _state.value = try {
                    repository.completeGitHubLogin(
                        code,
                        pending.verifier,
                    ).asAuthenticatedState().also { pendingOAuthStore.clear() }
                } catch (error: Exception) {
                    AuthUiState.Error(error.userMessage())
                }
            }
        }
    }

    fun logout() {
        val session = (_state.value as? AuthUiState.Authenticated)?.session ?: return
        val ownerId = session.user.id
        ownerId?.let(downloadQueueController::block)
        _state.value = AuthUiState.LoggingOut
        viewModelScope.launch {
            credentialProvider.clear()
            qqNicknameRepository.clear()
            pendingOAuthStore.clear()
            val stopJob = launch {
                runCatching { downloadQueueController.stop(ownerId) }
            }
            runCatching { repository.logout() }
            stopJob.join()
            _state.value = AuthUiState.Anonymous
        }
    }

    fun reportError(message: String) = setError(message)

    fun acceptPrivacyPolicy() {
        viewModelScope.launch { privacyConsentRepository.acceptCurrentPolicy() }
    }

    private suspend fun AuthSession.asAuthenticatedState(): AuthUiState.Authenticated {
        credentialProvider.clear()
        return AuthUiState.Authenticated(this)
    }

    private fun setError(message: String) {
        _state.value = AuthUiState.Error(message)
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "请求失败，请稍后重试"

    private companion object {
        const val OAUTH_MAX_AGE = 10 * 60 * 1_000L
    }
}
