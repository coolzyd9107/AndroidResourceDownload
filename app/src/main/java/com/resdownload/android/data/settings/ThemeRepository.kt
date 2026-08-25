package com.resdownload.android.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.resdownload.android.core.theme.ThemeMode
import com.resdownload.android.core.theme.ThemeSettings
import com.resdownload.android.core.theme.ThemeSchemeVariant
import com.resdownload.android.core.theme.DEFAULT_THEME_SEED_ARGB
import com.resdownload.android.core.theme.normalizeThemeSeedArgb
import timber.log.Timber

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val themeSettings: Flow<ThemeSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.w(exception, "Unable to read theme preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(Preferences::toThemeSettings)

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    suspend fun setSeedColor(argb: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[SEED_COLOR_ARGB] = normalizeThemeSeedArgb(argb)
        }
    }

    suspend fun setSchemeVariant(variant: ThemeSchemeVariant) {
        context.settingsDataStore.edit { preferences ->
            preferences[SCHEME_VARIANT] = variant.name
        }
    }

    internal companion object {
        val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED: Preferences.Key<Boolean> =
            booleanPreferencesKey("dynamic_color_enabled")
        val SEED_COLOR_ARGB: Preferences.Key<Int> = intPreferencesKey("seed_color_argb")
        val SCHEME_VARIANT: Preferences.Key<String> = stringPreferencesKey("theme_scheme_variant")
    }
}

internal fun Preferences.toThemeSettings(): ThemeSettings = ThemeSettings(
    themeMode = this[ThemeRepository.THEME_MODE]
        ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
        ?: ThemeMode.SYSTEM,
    dynamicColorEnabled = this[ThemeRepository.DYNAMIC_COLOR_ENABLED] ?: true,
    seedColorArgb = normalizeThemeSeedArgb(
        this[ThemeRepository.SEED_COLOR_ARGB] ?: DEFAULT_THEME_SEED_ARGB,
    ),
    schemeVariant = this[ThemeRepository.SCHEME_VARIANT]
        ?.let { stored -> ThemeSchemeVariant.entries.firstOrNull { it.name == stored } }
        ?: ThemeSchemeVariant.TONAL_SPOT,
)
