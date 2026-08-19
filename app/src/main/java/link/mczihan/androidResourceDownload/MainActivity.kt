package link.mczihan.androidResourceDownload

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import link.mczihan.androidResourceDownload.app.AndroidResourceDownloadRoot
import link.mczihan.androidResourceDownload.feature.auth.OAuthCallbackBus

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var oauthCallbackBus: OAuthCallbackBus

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        requestLegacyLogPermission()
        oauthCallbackBus.publish(intent?.data)
        enableEdgeToEdge()
        setContent {
            AndroidResourceDownloadRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oauthCallbackBus.publish(intent?.data)
    }

    private fun requestLegacyLogPermission() {
        if (BuildConfig.DEBUG &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_LOG,
            )
        }
    }

    private companion object {
        const val REQUEST_WRITE_LOG = 1002
    }
}
