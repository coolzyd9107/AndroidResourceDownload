package link.mczihan.androidResourceDownload.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.common.qqNumberFromEmail
import link.mczihan.androidResourceDownload.data.profile.QqNicknameRepository
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.User

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val qqNicknameRepository: QqNicknameRepository,
) : ViewModel() {
    private val _qqNickname = MutableStateFlow<String?>(null)
    val qqNickname = _qqNickname.asStateFlow()
    private var loadedQqNumber: String? = null
    private var loadJob: Job? = null

    fun load(user: User) {
        val qqNumber = user.email
            .takeIf { user.loginType == LoginType.EMAIL }
            .let(::qqNumberFromEmail)
        if (qqNumber == loadedQqNumber) return
        loadedQqNumber = qqNumber
        loadJob?.cancel()
        _qqNickname.value = null
        if (qqNumber == null) return
        loadJob = viewModelScope.launch {
            val nickname = qqNicknameRepository.nickname(qqNumber)
            if (loadedQqNumber == qqNumber) _qqNickname.value = nickname
        }
    }

    fun clear() {
        loadedQqNumber = null
        loadJob?.cancel()
        _qqNickname.value = null
    }
}
