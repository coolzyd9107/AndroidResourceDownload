package link.mczihan.androidResourceDownload.feature.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.data.file.FileRepository
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.webdav.WebDavException
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath

sealed interface FilesUiState {
    val path: WebDavPath

    data class Loading(override val path: WebDavPath) : FilesUiState
    data class Success(override val path: WebDavPath, val files: List<FileNode>) : FilesUiState
    data class Empty(override val path: WebDavPath) : FilesUiState
    data class Error(override val path: WebDavPath, val message: String, val unauthorized: Boolean = false) : FilesUiState
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: FileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<FilesUiState>(FilesUiState.Loading(WebDavPath.root()))
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val path = _state.value.path
        viewModelScope.launch {
            _state.value = FilesUiState.Loading(path)
            _state.value = try {
                repository.list(path).let { files ->
                    if (files.isEmpty()) FilesUiState.Empty(path) else FilesUiState.Success(path, files)
                }
            } catch (error: WebDavException.AuthenticationRequired) {
                FilesUiState.Error(path, "WebDAV 凭据已失效，请重新登录", unauthorized = true)
            } catch (error: Exception) {
                FilesUiState.Error(path, error.message ?: "文件列表加载失败")
            }
        }
    }

    fun openDirectory(path: WebDavPath) {
        _state.value = FilesUiState.Loading(path)
        load()
    }

    fun navigateUp() {
        val segments = _state.value.path.decodedSegments
        if (segments.isEmpty()) return
        _state.value = FilesUiState.Loading(WebDavPath.fromDecodedSegments(segments.dropLast(1)))
        load()
    }

    fun retry() = load()
}
