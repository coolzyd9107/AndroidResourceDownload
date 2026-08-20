package link.mczihan.androidResourceDownload.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.privacyConsentDataStore by preferencesDataStore(name = "privacy_consent")

@Singleton
class PrivacyConsentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val accepted: Flow<Boolean> = context.privacyConsentDataStore.data
        .catch { error ->
            if (error is IOException) {
                Timber.w(error, "Unable to read privacy consent")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            (preferences[ACCEPTED_POLICY_VERSION] ?: 0) >= CURRENT_POLICY_VERSION
        }

    suspend fun acceptCurrentPolicy() {
        context.privacyConsentDataStore.edit { preferences ->
            preferences[ACCEPTED_POLICY_VERSION] = CURRENT_POLICY_VERSION
        }
    }

    private companion object {
        const val CURRENT_POLICY_VERSION = 2
        val ACCEPTED_POLICY_VERSION = intPreferencesKey("accepted_policy_version")
    }
}
