package com.geotagcamera.geotagginglocationonphoto.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Everything the capture flow asks for in one system dialog.
 *
 * WRITE_EXTERNAL_STORAGE is only needed pre-scoped-storage (API 26-28) to
 * write the JPEG directly into the public Pictures dir — see
 * MediaStoreImageSaver. API 29+ publishes via MediaStore + IS_PENDING and
 * needs no storage permission at all.
 */
val CAPTURE_PERMISSIONS: Array<String>
    get() = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            // Lets Verify read the GPS in photos unredacted; no extra dialog on most phones.
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }.toTypedArray()

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** The viewfinder only needs the camera (plus storage on Android 9 and below). */
fun hasCameraPermission(context: Context): Boolean =
    context.granted(Manifest.permission.CAMERA) &&
        (Build.VERSION.SDK_INT > Build.VERSION_CODES.P || context.granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))

/** Precise or approximate both count: an approximate fix still geotags, just less exactly. */
fun hasLocationPermission(context: Context): Boolean =
    context.granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        context.granted(Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasCapturePermissions(context: Context): Boolean = hasCameraPermission(context) && hasLocationPermission(context)

/** Opens this app's page in system Settings, the only way back after "Don't ask again". */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
