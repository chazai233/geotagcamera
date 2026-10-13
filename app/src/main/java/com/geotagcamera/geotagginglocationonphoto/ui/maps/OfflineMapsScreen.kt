package com.geotagcamera.geotagginglocationonphoto.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geotagcamera.geotagginglocationonphoto.location.map.MapEngine
import com.geotagcamera.geotagginglocationonphoto.location.map.OfflineArea
import com.geotagcamera.geotagginglocationonphoto.location.map.OfflineMaps
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.util.NetworkState
import java.util.Locale

/**
 * Save map areas so the stamp map works with no signal. Areas you shoot in
 * while online are cached automatically; this screen is for planned trips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(onBack: () -> Unit, viewModel: OfflineMapsViewModel = viewModel()) {
    val areas by viewModel.areas.collectAsStateWithLifecycle()
    val here by viewModel.here.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val network by viewModel.network.collectAsStateWithLifecycle(NetworkState.UNMETERED)
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<OfflineArea?>(null) }
    var confirmMetered by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    AppScaffold(title = "Offline maps", onBack = onBack, snackbarHostState = snackbar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ConnectionRow(network) }
            item {
                SaveAreaCard(
                    here = here,
                    network = network,
                    onRetryLocate = viewModel::locate,
                    onDownload = { radius ->
                        if (network == NetworkState.METERED) confirmMetered = radius else viewModel.download(radius)
                    }
                )
            }
            if (areas.isEmpty()) {
                item { EmptyAreas() }
            } else {
                item {
                    Text(
                        "Saved areas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 8.dp, start = 4.dp)
                    )
                }
                items(areas, key = { it.id }) { area ->
                    AreaRow(area, onPause = { viewModel.pause(area.id) }, onResume = { viewModel.resume(area.id) }, onDelete = { confirmDelete = area })
                }
            }
            item {
                Text(
                    "Maps: ${MapEngine.ATTRIBUTION}. Places you photograph while online are also kept offline automatically, up to about 150 MB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 8.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }

    confirmDelete?.let { area ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete ${area.name}?") },
            text = { Text("The stamp map won't work offline in this area until you download it again. Photos are not affected.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(area.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }

    confirmMetered?.let { radius ->
        val lat = (here as? HereState.Found)?.fix?.latitude ?: 0.0
        val (_, bytes) = OfflineMaps.estimate(lat, radius)
        AlertDialog(
            onDismissRequest = { confirmMetered = null },
            icon = { Icon(Icons.Outlined.SignalCellularAlt, contentDescription = null) },
            title = { Text("Use mobile data?") },
            text = { Text("You're not on Wi-Fi. This area is about ${formatBytes(bytes)}.") },
            confirmButton = { TextButton(onClick = { viewModel.download(radius); confirmMetered = null }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { confirmMetered = null }) { Text("Wait for Wi-Fi") } }
        )
    }
}

@Composable
private fun ConnectionRow(network: NetworkState) {
    val (icon, text) = when (network) {
        NetworkState.OFFLINE -> Icons.Outlined.CloudOff to "Offline. Saved areas and recently visited places still work."
        NetworkState.METERED -> Icons.Outlined.SignalCellularAlt to "On mobile data. Maps load as you shoot."
        NetworkState.UNMETERED -> Icons.Outlined.Wifi to "On Wi-Fi. A good time to save areas."
    }
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(top = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveAreaCard(here: HereState, network: NetworkState, onRetryLocate: () -> Unit, onDownload: (Int) -> Unit) {
    var radius by rememberSaveable { mutableStateOf(5) }
    Card {
        Text("Save this area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(4.dp))
        when (here) {
            HereState.Locating -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Finding your location", style = MaterialTheme.typography.bodyMedium)
            }
            HereState.Unavailable -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Location unavailable. Turn on location and allow access, then try again.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onRetryLocate) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Try again")
                }
            }
            is HereState.Found -> {
                Text(
                    here.placeName ?: "%.5f, %.5f".format(Locale.US, here.fix.latitude, here.fix.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(12.dp))
                Text("Radius", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    OfflineMaps.RADIUS_OPTIONS_KM.forEachIndexed { i, km ->
                        SegmentedButton(
                            selected = radius == km,
                            onClick = { radius = km },
                            shape = SegmentedButtonDefaults.itemShape(i, OfflineMaps.RADIUS_OPTIONS_KM.size),
                            label = { Text("$km km") }
                        )
                    }
                }
                val (_, bytes) = remember(radius, here.fix.latitude) { OfflineMaps.estimate(here.fix.latitude, radius) }
                Spacer(Modifier.size(8.dp))
                Text("About ${formatBytes(bytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                Button(onClick = { onDownload(radius) }, enabled = network != NetworkState.OFFLINE, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (network == NetworkState.OFFLINE) "Connect to download" else "Download")
                }
            }
        }
    }
}

@Composable
private fun AreaRow(area: OfflineArea, onPause: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when {
                area.complete -> Icons.Outlined.CloudDone to MaterialTheme.colorScheme.primary
                area.error != null && !area.downloading -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
                else -> Icons.Outlined.Map to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(area.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = when {
                    area.complete -> "Ready offline · ${area.radiusKm} km · ${formatBytes(area.completedBytes)}"
                    area.downloading -> "Downloading ${(area.progress * 100).toInt()}% · ${formatBytes(area.completedBytes)}"
                    else -> "Paused ${(area.progress * 100).toInt()}% · ${area.radiusKm} km"
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!area.complete) {
                if (area.downloading) IconAction(Icons.Outlined.Pause, "Pause", onPause)
                else IconAction(Icons.Outlined.PlayArrow, "Resume", onResume)
            }
            IconAction(Icons.Outlined.Delete, "Delete", onDelete)
        }
        if (!area.complete) {
            Spacer(Modifier.size(10.dp))
            LinearProgressIndicator(progress = { area.progress }, modifier = Modifier.fillMaxWidth())
        }
        area.error?.let {
            Spacer(Modifier.size(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmptyAreas() {
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(8.dp))
        Text("No saved areas yet", style = MaterialTheme.typography.titleSmall)
        Text(
            "Going somewhere without signal? Save the area first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        content = content
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(Locale.US, bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(Locale.US, bytes / 1e6)
    else -> "%.0f KB".format(Locale.US, (bytes / 1e3).coerceAtLeast(1.0))
}
