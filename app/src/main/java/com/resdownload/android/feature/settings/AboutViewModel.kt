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
import com.resdownload.android.data.notice.NoticeRepository
import com.resdownload.android.data.notice.parseReleaseNotesForVersion
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
        val releaseNotes: List<String> = emptyList(),
    ) : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val noticeRepository: NoticeRepository,
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
                val updateState = resolveUpdateState(currentVersion, manifest)
                if (updateState !is UpdateUiState.Available) {
                    updateState
                } else {
                    val releaseNotes = try {
                        noticeRepository.load()
                            ?.let { notice ->
                                parseReleaseNotesForVersion(notice, manifest.latestVersion)
                            }
                            .orEmpty()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        emptyList()
                    }
                    updateState.copy(releaseNotes = releaseNotes)
                }
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
    releaseNotes: List<String> = emptyList(),
): UpdateUiState {
    val comparison = compareAppVersions(currentVersion, manifest.latestVersion)
        ?: throw IllegalArgumentException("Invalid application version")
    return if (comparison < 0) {
        UpdateUiState.Available(
            currentVersion = currentVersion,
            latestVersion = manifest.latestVersion,
            updateUrl = manifest.updateUrl,
            releaseNotes = releaseNotes,
        )
    } else {
        UpdateUiState.UpToDate(currentVersion)
    }
}
