package link.mczihan.androidResourceDownload.feature.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.data.auth.AuthRepository
import link.mczihan.androidResourceDownload.domain.model.AuthSession
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider

sealed interface AuthUiState {
    data object Restoring : AuthUiState
    data object Anonymous : AuthUiState
    data object SendingCode : AuthUiState
    data class AwaitingCode(val email: String, val expiresInSeconds: Int) : AuthUiState
    data object Authenticating : AuthUiState
    data class Authenticated(val session: AuthSession) : AuthUiState
    data class Error(val message: String, val recoverableState: AuthUiState = Anonymous) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val pendingOAuthStore: PendingOAuthStore,
    private val credentialProvider: WebDavCredentialProvider,
    private val oauthCallbackBus: OAuthCallbackBus,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Restoring)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        restore()
        viewModelScope.launch {
            oauthCallbackBus.events.collect { uri ->
                uri?.let {
                    oauthCallbackBus.consume(it)
                    handleGithubCallback(it)
                }
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _state.value = AuthUiState.Restoring
            val restoredState = try {
                repository.restoreSession()?.let(AuthUiState::Authenticated) ?: AuthUiState.Anonymous
            } catch (error: Exception) {
                AuthUiState.Error(error.userMessage(), AuthUiState.Anonymous)
            }
            if (_state.value is AuthUiState.Restoring) _state.value = restoredState
        }
    }

    fun requestCode(email: String) {
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
        if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) return null
        val transaction = PendingOAuth(
            state = Pkce.generateState(),
            verifier = Pkce.generateVerifier(),
            redirectUri = BuildConfig.OAUTH_REDIRECT_URI,
            createdAtMillis = System.currentTimeMillis(),
        )
        pendingOAuthStore.save(transaction)
        return "https://github.com/login/oauth/authorize" +
            "?client_id=${transaction.encode(BuildConfig.GITHUB_CLIENT_ID)}" +
            "&redirect_uri=${transaction.encode(transaction.redirectUri)}" +
            "&state=${transaction.encode(transaction.state)}" +
            "&code_challenge=${transaction.encode(Pkce.challengeFor(transaction.verifier))}" +
            "&code_challenge_method=S256"
    }

    fun handleGithubCallback(uri: Uri) {
        if (uri.scheme != "link.mczihan.androidresourcedownload" ||
            uri.host != "oauth" || uri.path != "/callback"
        ) return setError("GitHub 回调地址无效")
        uri.getQueryParameter("error")?.let { return setError("GitHub 登录被取消") }
        val state = uri.getQueryParameter("state") ?: return setError("GitHub 回调缺少状态")
        val code = uri.getQueryParameter("code") ?: return setError("GitHub 回调缺少授权码")
        val pending = pendingOAuthStore.consumeIfValid(state, System.currentTimeMillis(), OAUTH_MAX_AGE)
            ?: return setError("GitHub 登录状态已失效")
        viewModelScope.launch {
            _state.value = AuthUiState.Authenticating
            _state.value = try {
                repository.loginWithGitHub(
                    code,
                    pending.redirectUri,
                    pending.verifier,
                ).asAuthenticatedState()
            } catch (error: Exception) {
                AuthUiState.Error(error.userMessage())
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { repository.logout() }
            credentialProvider.clear()
            pendingOAuthStore.clear()
            _state.value = AuthUiState.Anonymous
        }
    }

    fun reportError(message: String) = setError(message)

    private suspend fun AuthSession.asAuthenticatedState(): AuthUiState.Authenticated {
        credentialProvider.clear()
        return AuthUiState.Authenticated(this)
    }

    private fun setError(message: String) {
        _state.value = AuthUiState.Error(message)
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "请求失败，请稍后重试"

    private fun PendingOAuth.encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val OAUTH_MAX_AGE = 10 * 60 * 1_000L
    }
}
