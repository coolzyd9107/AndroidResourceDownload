package com.resdownload.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.resdownload.android.core.theme.ThemeMode
import com.resdownload.android.core.theme.ThemeSettings
import com.resdownload.android.core.theme.ThemeSchemeVariant
import com.resdownload.android.core.theme.DEFAULT_THEME_SEED_ARGB
import com.resdownload.android.data.settings.ThemeRepository

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
