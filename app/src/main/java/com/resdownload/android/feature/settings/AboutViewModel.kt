package com.resdownload.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.resdownload.android.BuildConfig
import com.resdownload.android.data.update.UpdateManifest
import com.resdownload.android.data.update.UpdateRepository
import com.resdownload.android.data.update.compareAppVersions

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(
        val currentVersion: String,
        val latestVersion: String,
        val updateUrl: String,
    ) : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
) : ViewModel() {
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState = _updateState.asStateFlow()
    private var updateJob: Job? = null

    fun checkForUpdate() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            _updateState.value = try {
                val manifest = updateRepository.load()
                val currentVersion = BuildConfig.VERSION_NAME
                resolveUpdateState(currentVersion, manifest)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UpdateUiState.Error("无法检查更新，请检查网络后重试")
            }
        }
    }

    fun dismissUpdateResult() {
        if (_updateState.value != UpdateUiState.Checking) {
            _updateState.value = UpdateUiState.Idle
        }
    }
}

internal fun resolveUpdateState(
    currentVersion: String,
    manifest: UpdateManifest,
): UpdateUiState {
    val comparison = compareAppVersions(currentVersion, manifest.latestVersion)
        ?: throw IllegalArgumentException("Invalid application version")
    return if (comparison < 0) {
        UpdateUiState.Available(
            currentVersion = currentVersion,
            latestVersion = manifest.latestVersion,
            updateUrl = manifest.updateUrl,
        )
    } else {
        UpdateUiState.UpToDate(currentVersion)
    }
}
