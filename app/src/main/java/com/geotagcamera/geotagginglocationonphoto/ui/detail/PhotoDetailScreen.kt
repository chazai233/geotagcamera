package com.geotagcamera.geotagginglocationonphoto.ui.detail

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.security.PhotoIntegrity
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.common.ProofCard
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoDetailScreen(
    photoId: Long,
    onBack: () -> Unit,
    onVerify: (String) -> Unit = {},
    viewModel: PhotoDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val consent by viewModel.deleteConsent.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val deviceKey = remember { PhotoIntegrity.devicePublicKeyBase64OrNull() }

    LaunchedEffect(photoId) { viewModel.load(photoId) }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(consent) {
        consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    val photo = state.photo
    AppScaffold(
        title = "Photo",
        onBack = onBack,
        actions = {
            if (photo != null) {
                IconButton(onClick = { shareImage(context, photo.filePath) }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            photo == null -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text("This photo is no longer available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = Uri.parse(photo.filePath),
                    contentDescription = photo.address ?: "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                state.outcome?.let { ProofCard(it, deviceKey) }

                val lat = state.exifLat ?: photo.latitude
                val lng = state.exifLng ?: photo.longitude
                DetailsCard {
                    val taken = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(photo.capturedAtEpochMs))
                    DetailRow(Icons.Outlined.Schedule, "Taken", taken)
                    DetailRow(
                        Icons.Outlined.MyLocation, "Coordinates",
                        String.format(Locale.US, "%.6f, %.6f", lat, lng) +
                            (photo.accuracyMeters?.let { "  ·  ±${it.toInt()} m" } ?: ""),
                        copyable = true
                    )
                    photo.address?.let {
                        DetailRow(Icons.Outlined.Place, "Address", it + if (photo.addressFromCache) "\n(from offline cache)" else "")
                    }
                    photo.orgLabel?.let { DetailRow(Icons.Outlined.Business, "Organisation", it) }
                    if (photo.fieldWorkerSignature) DetailRow(Icons.Outlined.Draw, "Signature", "Field worker signature attached")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { openInMaps(context, lat, lng) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open in maps")
                    }
                    OutlinedButton(onClick = { onVerify(photo.filePath) }, modifier = Modifier.weight(1f)) {
                        Text("Verify again")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete this photo?") },
            text = { Text("It will be removed from this device's gallery as well. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 6.dp)
    ) { content() }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, copyable: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (copyable) Modifier.clickable(onClickLabel = "Copy") { clipboard.setText(AnnotatedString(value)) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (copyable) {
            Icon(
                Icons.Outlined.ContentCopy, contentDescription = "Copy",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun openInMaps(context: Context, lat: Double, lng: Double) {
    val geo = String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", lat, lng, lat, lng)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo))) }
        .onFailure {
            val web = String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f", lat, lng, lat, lng)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web))) }
        }
}

private fun shareImage(context: Context, uriString: String) {
    val uri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share photo"))
}
