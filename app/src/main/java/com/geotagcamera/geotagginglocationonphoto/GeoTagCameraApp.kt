package com.geotagcamera.geotagginglocationonphoto

import android.app.Application
import com.geotagcamera.geotagginglocationonphoto.location.map.OfflineMaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeoTagCameraApp : Application() {

    /** Lives as long as the process; for work that must outlast any one screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        com.geotagcamera.geotagginglocationonphoto.support.CrashCatcher.install(this)
        // Picks up offline map downloads that were cut off when the app last closed.
        appScope.launch { runCatching { OfflineMaps.load(this@GeoTagCameraApp) } }
    }
}
