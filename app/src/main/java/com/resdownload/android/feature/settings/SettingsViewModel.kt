package com.resdownload.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resdownload.android.data.notice.NoticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NoticeUiState {
    data object Loading : NoticeUiState
    data class Content(val text: String) : NoticeUiState
    data object Empty : NoticeUiState
    data object Error : NoticeUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val noticeRepository: NoticeRepository,
) : ViewModel() {
    private val _noticeState = MutableStateFlow<NoticeUiState>(NoticeUiState.Loading)
    val noticeState = _noticeState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refreshNotice()
    }

    fun refreshNotice() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _noticeState.value = NoticeUiState.Loading
            _noticeState.value = try {
                noticeRepository.load()?.let(NoticeUiState::Content) ?: NoticeUiState.Empty
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                NoticeUiState.Error
            }
        }
    }
}
