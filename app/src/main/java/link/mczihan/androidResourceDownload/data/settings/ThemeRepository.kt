package link.mczihan.androidResourceDownload.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import timber.log.Timber

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.w(exception, "Unable to read theme preferences")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[THEME_MODE].toThemeMode() }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    private fun String?.toThemeMode(): ThemeMode =
        ThemeMode.values().firstOrNull { it.name == this } ?: ThemeMode.SYSTEM

    private companion object {
        val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("theme_mode")
    }
}
