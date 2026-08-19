package link.mczihan.androidResourceDownload

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import link.mczihan.androidResourceDownload.core.logging.DownloadFileLoggingTree
import timber.log.Timber

@HiltAndroidApp
class AndroidResourceDownloadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            val fileLoggingTree = DownloadFileLoggingTree(this)
            Timber.plant(Timber.DebugTree(), fileLoggingTree)
            Timber.i("File logging enabled: /sdcard/Download/%s", fileLoggingTree.fileName)
        }
    }
}
