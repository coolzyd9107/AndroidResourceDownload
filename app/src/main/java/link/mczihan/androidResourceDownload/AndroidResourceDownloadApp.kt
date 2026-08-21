package link.mczihan.androidResourceDownload

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.data.upload.UploadRepository
import timber.log.Timber

@HiltAndroidApp
class AndroidResourceDownloadApp : Application() {
    @Inject lateinit var uploadRepository: UploadRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        applicationScope.launch {
            runCatching { uploadRepository.reconcilePermissionReservations() }
                .onFailure { error -> Timber.w(error, "Unable to reconcile upload URI permissions") }
        }
    }
}
