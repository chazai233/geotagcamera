package com.geotagcamera.geotagginglocationonphoto.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private const val SOURCE_URL = "https://github.com/konkomaji/geotagcamera"

/**
 * About & legal (design section 05, screen 10). Copy is written to match the
 * eventual Play data-safety declaration word for word so the two can't drift,
 * and to never overclaim: a signature proves the image is unedited and which
 * key signed it, never who was holding the phone.
 */
@Composable
fun AboutLegalScreen(onBack: () -> Unit, viewModel: AboutLegalViewModel = viewModel()) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    AppScaffold(title = "About & legal", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "GeoTag Camera ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Body("Free and open source. No ads, no accounts, no tracking.")

            Section("No data collected") {
                Body("No accounts, no analytics, no ad SDK, no crash reporting. The optional map thumbnail and weather are off by default and only fetched for the current location when you turn them on.")
            }

            Section("What the signature proves") {
                Body("Each photo is hashed and signed with a key kept in this device's secure keystore. Verifying proves the image, including its stamp, is unchanged since that key signed it, and shows which key it was. It does not prove who was holding the phone, and the location fields in the file's metadata are not part of the signature.")
            }

            Section("Location & photos") {
                Body("Location is used only while the camera is open, to stamp where a photo was taken. Never in the background. Street addresses come from your phone's built-in geocoding service, which on most phones asks Google Play services. Photos are saved to your own gallery under Pictures/GeoTagCamera.")
            }

            Section("Optional network features") {
                Body("Map thumbnail: OpenStreetMap data via Stadia Maps, sent the map tile around your position. Weather: Open-Meteo, sent your position rounded to about 1 km. Neither ever blocks a photo when you're offline.")
            }

            Section("Licenses") {
                Body(
                    "GeoTag Camera: GNU GPL v3.\n" +
                        "Poppins and Roboto Mono fonts: SIL Open Font License 1.1.\n" +
                        "AndroidX, CameraX, Kotlin, Coil: Apache License 2.0.\n" +
                        "Map data © OpenStreetMap contributors (ODbL), tiles © Stadia Maps, © OpenMapTiles.\n" +
                        "Weather data by Open-Meteo.com (CC BY 4.0)."
                )
            }

            Section("Links") {
                LinkRow("Source code", SOURCE_URL, context)
                LinkRow("Privacy policy", "$SOURCE_URL/blob/main/PRIVACY.md", context)
                LinkRow("Third-party notices", "$SOURCE_URL/blob/main/THIRD_PARTY_NOTICES.md", context)
                LinkRow("Report a problem", "$SOURCE_URL/issues", context)
            }

            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Delete all app data", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(6.dp))
            Body("Clears this app's photo index, caches, logo and settings. Your saved photos stay in your gallery.")
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            onConfirm = { viewModel.deleteAllData { showDeleteDialog = false } },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val armed = typed.trim().equals("DELETE", ignoreCase = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all app data?") },
        text = {
            Column {
                Text("This clears the capture index, caches, org logo and settings. Photos already in your gallery are untouched. Type DELETE to confirm.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text("Type DELETE") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = armed) {
                Text("Delete", color = if (armed) MaterialTheme.colorScheme.error else Color.Gray)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun LinkRow(label: String, url: String, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open $label") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
