package link.mczihan.androidResourceDownload

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        publishOAuthCallback(intent)
        enableEdgeToEdge()
        setContent {
            AndroidResourceDownloadRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishOAuthCallback(intent)
    }

    private fun publishOAuthCallback(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme == "link.mczihan.androidresourcedownload" &&
            uri.host == "oauth" &&
            uri.path == "/callback"
        ) {
            oauthCallbackBus.publish(uri)
        }
    }
}
