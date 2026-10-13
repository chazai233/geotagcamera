package com.geotagcamera.geotagginglocationonphoto.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * The one shared image chooser (design section 08). Uses `GetContent`, which
 * adds zero permissions by construction — the system picker/SAF hands back a
 * readable content:// Uri without READ_MEDIA_IMAGES. Reused by Verify (pick a
 * photo to check), the Gallery FAB (Phase 9), and the Settings org-logo upload
 * (Phase 10). Returns a launcher lambda; call it to open the picker.
 */
@Composable
fun rememberVerifyPicker(onPicked: (Uri) -> Unit): () -> Unit {
    // Document picker, not the photo picker: the photo picker always strips GPS,
    // so a signed location could never be checked. Media-location access is asked
    // for first (Android 10+) so gallery images open unredacted.
    val context = androidx.compose.ui.platform.LocalContext.current
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        docs.launch(arrayOf("image/*"))
    }
    return {
        val needs = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_MEDIA_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needs) permission.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION) else docs.launch(arrayOf("image/*"))
    }
}

@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onPicked)
    }
    return { launcher.launch("image/*") }
}
