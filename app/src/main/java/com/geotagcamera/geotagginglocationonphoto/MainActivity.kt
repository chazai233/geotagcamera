package com.geotagcamera.geotagginglocationonphoto

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geotagcamera.geotagginglocationonphoto.ui.nav.GeoTagCameraApp
import com.geotagcamera.geotagginglocationonphoto.ui.theme.GeoTagCameraTheme

class MainActivity : ComponentActivity() {

    // Held above the nav graph so a "verify this photo" share can be consumed exactly once.
    private var shareUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Every screen draws behind the system bars and pads itself by their
        // insets (required from targetSdk 35 anyway). Transparent bars; each
        // theme picks light or dark bar icons for its own background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: after rotation the activity is recreated with
        // the same intent, and re-reading it would push Verify a second time.
        if (savedInstanceState == null) shareUri = extractSharedImageUri(intent)
        setContent {
            GeoTagCameraTheme {
                GeoTagCameraApp(
                    shareUri = shareUri,
                    onShareConsumed = { shareUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareUri = extractSharedImageUri(intent)
    }

    private fun extractSharedImageUri(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        // Only content:// from another app. A file:// path or a Uri pointing at
        // this app's own provider would be read with this app's privileges.
        if (uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        if (uri.authority?.startsWith(packageName) == true) return null
        return uri.toString()
    }
}
