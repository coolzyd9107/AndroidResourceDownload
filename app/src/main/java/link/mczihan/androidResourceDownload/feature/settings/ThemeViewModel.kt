package link.mczihan.androidResourceDownload.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import link.mczihan.androidResourceDownload.core.theme.ThemeSettings
import link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant
import link.mczihan.androidResourceDownload.core.theme.DEFAULT_THEME_SEED_ARGB
import link.mczihan.androidResourceDownload.data.settings.ThemeRepository

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ViewModel() {
    val themeSettings: StateFlow<ThemeSettings> = themeRepository.themeSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ThemeSettings(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setDynamicColorEnabled(enabled) }
    }

    fun setSeedColor(argb: Int) {
        viewModelScope.launch { themeRepository.setSeedColor(argb) }
    }

    fun setSchemeVariant(variant: ThemeSchemeVariant) {
        viewModelScope.launch { themeRepository.setSchemeVariant(variant) }
    }

    fun resetSeedColor() = setSeedColor(DEFAULT_THEME_SEED_ARGB)
}
