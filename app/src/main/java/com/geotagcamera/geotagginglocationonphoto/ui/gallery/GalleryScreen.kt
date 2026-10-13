package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import androidx.compose.material.icons.outlined.AddPhotoAlternate
import kotlinx.coroutines.flow.first
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.DriveFileMove
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.common.rememberPhotoPicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ProofColors


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onOpenPhoto: (Long) -> Unit = {},
    onVerifyExternal: (String) -> Unit = {},
    onOpenCamera: () -> Unit = {},
    viewModel: GalleryViewModel = viewModel()
) {
    val context = LocalContext.current
    val allPhotos by viewModel.photos.collectAsStateWithLifecycle()
    val loaded by viewModel.visiblePhotos.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val exportState by viewModel.export.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var pickFormat by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var manageProjects by remember { mutableStateOf(false) }
    val galleryMessage by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(galleryMessage) { galleryMessage?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> viewModel.importPhotos(uris) }
    val orgLabel by androidx.compose.runtime.produceState<String?>(null) {
        value = com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences(context).fields
            .first().orgLabel.ifBlank { null }
    }
    val tileStatus by viewModel.tileStatus.collectAsStateWithLifecycle()
    val consent by viewModel.deleteConsent.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val photos = loaded

    val pickToVerify = com.geotagcamera.geotagginglocationonphoto.ui.common.rememberVerifyPicker { uri -> onVerifyExternal(uri.toString()) }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(consent) {
        consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    // Drop selections for photos that no longer exist.
    LaunchedEffect(photos) {
        val ids = photos?.map { it.id }?.toSet() ?: return@LaunchedEffect
        if (!ids.containsAll(selected)) selected = selected intersect ids
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    AppScaffold(
        snackbarHostState = snackbar,
        title = if (selecting) "${selected.size} selected" else "Gallery",
        onBack = if (selecting) ({ selected = emptySet() }) else null,
        backIcon = Icons.Filled.Close,
        backDescription = "Clear selection",
        actions = {
            // Export and report act on the selection if there is one, else on what the filter shows.
            val scope = photos.orEmpty().let { list -> if (selecting) list.filter { it.id in selected } else list }
            if (selecting) {
                IconButton(onClick = { moveDialog = true }) {
                    Icon(Icons.Outlined.DriveFileMove, contentDescription = "Move to project")
                }
                IconButton(onClick = { selected = photos.orEmpty().map { it.id }.toSet() }) {
                    Icon(Icons.Outlined.SelectAll, contentDescription = "Select all")
                }
                IconButton(onClick = {
                    val uris = photos.orEmpty().filter { it.id in selected }.map { it.filePath }
                    if (uris.isNotEmpty()) shareMultiple(context, uris)
                }) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
            if (allPhotos != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export locations") },
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                            enabled = scope.isNotEmpty(),
                            onClick = { menuOpen = false; pickFormat = true }
                        )
                        DropdownMenuItem(
                            text = { Text("PDF report") },
                            leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                            enabled = scope.isNotEmpty(),
                            onClick = { menuOpen = false; viewModel.buildReport(scope, viewModel.scopeTitle(), orgLabel) }
                        )
                        DropdownMenuItem(
                            text = { Text("Import photos") },
                            leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
                            onClick = { menuOpen = false; importLauncher.launch(arrayOf("image/jpeg")) }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage projects") },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            onClick = { menuOpen = false; manageProjects = true }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selecting && !photos.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = pickToVerify,
                    text = { Text("Verify a photo") },
                    icon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null) }
                )
            }
        }
    ) { scaffoldPadding ->
        // With projects, a chip row sits under the top bar and the grid starts below it.
        val showFilters = projects.isNotEmpty() && !allPhotos.isNullOrEmpty()
        if (showFilters) {
            Box(Modifier.padding(top = scaffoldPadding.calculateTopPadding())) {
                ProjectFilterRow(projects, filter, viewModel::setFilter)
            }
        }
        val padding = if (!showFilters) scaffoldPadding else PaddingValues(
            top = scaffoldPadding.calculateTopPadding() + FILTER_ROW_HEIGHT,
            bottom = scaffoldPadding.calculateBottomPadding()
        )
        when {
            photos == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            photos.isEmpty() && filter != GalleryFilter.All -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), Alignment.Center) {
                Text(
                    "No photos here yet. Choose this project under the viewfinder before shooting, or select photos and move them here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            photos.isEmpty() -> EmptyGallery(
                modifier = Modifier.padding(padding),
                onOpenCamera = onOpenCamera,
                onVerify = pickToVerify
            )
            else -> {
                val groups = remember(photos) { photos.groupBy { dayLabel(it.capturedAtEpochMs) } }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groups.forEach { (day, dayPhotos) ->
                        item(key = "header_$day", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                day,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
                            )
                        }
                        items(dayPhotos, key = { it.id }) { photo ->
                            PhotoTile(
                                photo = photo,
                                status = tileStatus[photo.id] ?: TileStatus.UNKNOWN,
                                selecting = selecting,
                                isSelected = photo.id in selected,
                                onClick = {
                                    if (selecting) selected = selected.toggle(photo.id)
                                    else onOpenPhoto(photo.id)
                                },
                                onLongClick = { selected = selected.toggle(photo.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickFormat) {
        val scope = photos.orEmpty().let { list -> if (selecting) list.filter { it.id in selected } else list }
        ExportFormatDialog(
            count = scope.size,
            onPick = { format -> pickFormat = false; viewModel.exportLocations(format, scope, viewModel.scopeTitle()) },
            onDismiss = { pickFormat = false }
        )
    }
    if (moveDialog) {
        MoveToProjectDialog(
            projects = projects,
            count = selected.size,
            onMove = { id -> viewModel.moveToProject(selected, id); moveDialog = false; selected = emptySet() },
            onCreate = { name -> viewModel.createProjectAndMove(name, selected).also { if (it == null) selected = emptySet() } },
            onDismiss = { moveDialog = false }
        )
    }
    if (manageProjects) {
        ManageProjectsDialog(
            projects = projects,
            onRename = viewModel::renameProject,
            onArchive = viewModel::setArchived,
            onDelete = viewModel::deleteProject,
            onDismiss = { manageProjects = false }
        )
    }
    ExportProgressDialog(
        state = exportState,
        onShare = { ready ->
            runCatching { com.geotagcamera.geotagginglocationonphoto.export.ExportFiles.share(context, ready.file, ready.mimeType, ready.title) }
            viewModel.dismissExport()
        },
        onDismiss = viewModel::dismissExport
    )

    if (confirmDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(if (count == 1) "Delete this photo?" else "Delete $count photos?") },
            text = { Text("They will be removed from this device's gallery as well. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(photos.orEmpty().filter { it.id in selected })
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier, onOpenCamera: () -> Unit, onVerify: () -> Unit) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("No photos yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Photos you take appear here with their location stamp and proof.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = onOpenCamera,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Take a photo") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Verify a photo someone sent you") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    photo: PhotoEntity,
    status: TileStatus,
    selecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
    ) {
        AsyncImage(
            model = Uri.parse(photo.filePath),
            contentDescription = photo.address ?: "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(if (isSelected) 8.dp else 0.dp).clip(RoundedCornerShape(4.dp))
        )
        when (status) {
            TileStatus.VERIFIED -> StatusBadge(Icons.Filled.Verified, ProofColors.verified, "Verified")
            TileStatus.TAMPERED -> StatusBadge(Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, "Edited since capture")
            else -> Unit
        }
        if (selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StatusBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    description: String
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(5.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
    }
}

private val FILTER_ROW_HEIGHT = 52.dp

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

/** "Today", "Yesterday", or a date like "Mon, 12 Oct 2026". */
private fun dayLabel(epochMs: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = epochMs }
    val today = Calendar.getInstance()
    fun Calendar.sameDay(o: Calendar) = get(Calendar.YEAR) == o.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == o.get(Calendar.DAY_OF_YEAR)
    if (day.sameDay(today)) return "Today"
    if (day.sameDay(today.apply { add(Calendar.DAY_OF_YEAR, -1) })) return "Yesterday"
    return SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}

private fun shareMultiple(context: Context, uriStrings: List<String>) {
    val intent = if (uriStrings.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStrings.first()))
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uriStrings.map { Uri.parse(it) }))
        }
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share photos"))
}
