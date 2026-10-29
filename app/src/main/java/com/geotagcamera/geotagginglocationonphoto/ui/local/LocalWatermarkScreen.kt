package com.geotagcamera.geotagginglocationonphoto.ui.local

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.stamp.DateFormats
import com.geotagcamera.geotagginglocationonphoto.stamp.StampOverlay
import com.geotagcamera.geotagginglocationonphoto.stamp.buildStampSpec
import com.geotagcamera.geotagginglocationonphoto.ui.capture.CaptureContextSheet
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.settings.DateStep
import com.geotagcamera.geotagginglocationonphoto.ui.settings.TimeStep
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** 给手机里已有的照片加水印。 */
@Composable
fun LocalWatermarkScreen(viewModel: LocalWatermarkViewModel = viewModel()) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val logo by viewModel.orgLogo.collectAsStateWithLifecycle()
    val save by viewModel.save.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<Uri?>(null) }
    var showFields by remember { mutableStateOf(false) }
    var pickTimeFor by remember { mutableStateOf<Uri?>(null) }
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }
    var askApplyAll by remember { mutableStateOf<Pair<Uri, Long>?>(null) }

    // 文档选择器（不会去掉 GPS）；Android 10+ 先请求读取照片位置的权限
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.add(uris)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        docs.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/heic"))
    }
    val pick: () -> Unit = {
        val needs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (needs) permission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        else docs.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/heic"))
    }

    LaunchedEffect(photos) {
        if (photos.none { it.uri == selected }) selected = photos.firstOrNull()?.uri
    }
    val current = photos.firstOrNull { it.uri == selected }
    val missing = photos.count { it.timeMs == null }

    AppScaffold(
        title = "本地照片加水印",
        actions = {
            if (photos.isNotEmpty()) TextButton(onClick = { viewModel.clear() }) { Text("清空") }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            EmptyState(loading, pick, Modifier.padding(padding))
            return@AppScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 预览：和成片同一套绘制 ----
            current?.let { p ->
                val spec = remember(p, fields, logo) {
                    buildStampSpec(
                        fix = p.fix,
                        addressParts = p.address,
                        capturedAtEpochMs = p.timeMs ?: System.currentTimeMillis(),
                        fields = fields,
                        mapTile = null,
                        orgLogo = if (fields.showOrgLogo) logo else null,
                        hasSignature = false
                    )
                }
                val ratio = (p.width.toFloat() / p.height).coerceIn(0.3f, 3f)
                Box(Modifier.fillMaxWidth().heightIn(max = 440.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(model = p.uri, contentDescription = p.name, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        StampOverlay(spec, Modifier.fillMaxSize())
                    }
                }
            }

            // ---- 缩略图：红点 = 缺拍摄时间 ----
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.uri.toString() }) { p ->
                    val isSel = p.uri == selected
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                BorderStroke(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selected = p.uri }
                    ) {
                        AsyncImage(model = p.uri, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (p.timeMs == null) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(10.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                }
                item {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = "继续添加", onClick = pick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Add, contentDescription = "继续添加")
                    }
                }
            }

            // ---- 这张照片的信息 ----
            current?.let { p ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                        headlineContent = { Text("拍摄时间") },
                        supportingContent = {
                            val t = p.timeMs
                            when {
                                t == null -> Text("照片里没有拍摄时间，请设置", color = MaterialTheme.colorScheme.error)
                                p.timeFromExif -> Text(DateFormats.format("yyyy-MM-dd HH:mm:ss", t) + "（来自照片 EXIF）")
                                else -> Text(DateFormats.format("yyyy-MM-dd HH:mm:ss", t) + "（手动设置）")
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = { pickTimeFor = p.uri }) { Text(if (p.timeMs == null) "设置" else "修改") }
                                if (p.customTimeMs != null && p.exifTimeMs != null) {
                                    TextButton(onClick = { viewModel.useExifTime(p.uri) }) { Text("用 EXIF") }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Place, contentDescription = null) },
                        headlineContent = { Text("位置") },
                        supportingContent = {
                            val f = p.fix
                            Text(
                                if (f == null) "照片没有 GPS 信息，水印不显示坐标"
                                else String.format(Locale.US, "%.6f, %.6f", f.latitude, f.longitude) +
                                    (p.address?.addressLine?.let { "\n$it" } ?: "")
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                        headlineContent = { Text("水印字段（所有照片共用）") },
                        supportingContent = {
                            val lines = fields.customFields.filter { it.enabled }.map { f ->
                                (if (f.label.isNotBlank()) f.label + "：" else "") + f.value.trim().ifEmpty { "未填写" }
                            }
                            Text(if (lines.isEmpty()) "没有开启的字段，可在设置里添加" else lines.joinToString("\n"))
                        },
                        trailingContent = { Text("修改", color = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { showFields = true }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "水印样式、日期格式等沿用设置里的配置。保存后原图不变，新图存到 Pictures/GeoTagCamera/本地水印。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.remove(p.uri) }) { Icon(Icons.Outlined.Close, contentDescription = "移除这张") }
                }
            }

            Button(
                onClick = { viewModel.saveAll() },
                enabled = missing == 0 && save !is LocalSaveState.Saving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (missing > 0) "还有 $missing 张没有拍摄时间" else "加水印并保存（${photos.size} 张）")
            }
        }
    }

    if (showFields) {
        CaptureContextSheet(
            projects = emptyList(),
            activeProjectId = null,
            note = "",
            onSelectProject = {},
            onCreateProject = { null },
            onNoteChange = {},
            onDismiss = { showFields = false },
            customFields = fields.customFields,
            onFieldValues = { viewModel.setFieldValues(it) },
            onAddPreset = { id, v -> viewModel.addPreset(id, v) },
            showProjects = false
        )
    }

    pickTimeFor?.let { uri ->
        val initial = photos.firstOrNull { it.uri == uri }?.timeMs ?: System.currentTimeMillis()
        if (pickedDateUtc == null) DateStep(initialMs = initial, onDismiss = { pickTimeFor = null }, onPicked = { utc -> pickedDateUtc = utc })
        pickedDateUtc?.let { utc ->
            TimeStep(
                initialMs = initial,
                onDismiss = { pickedDateUtc = null; pickTimeFor = null },
                onPicked = { time ->
                    val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                    val ms = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    pickedDateUtc = null
                    pickTimeFor = null
                    val othersMissing = photos.count { it.uri != uri && it.timeMs == null }
                    if (othersMissing > 0) askApplyAll = uri to ms else viewModel.setTime(uri, ms, toAllMissing = false)
                }
            )
        }
    }

    askApplyAll?.let { (uri, ms) ->
        val others = photos.count { it.uri != uri && it.timeMs == null }
        AlertDialog(
            onDismissRequest = { viewModel.setTime(uri, ms, false); askApplyAll = null },
            title = { Text("其他照片也用这个时间？") },
            text = { Text("还有 $others 张照片没有拍摄时间。") },
            confirmButton = { TextButton(onClick = { viewModel.setTime(uri, ms, true); askApplyAll = null }) { Text("全部使用") } },
            dismissButton = { TextButton(onClick = { viewModel.setTime(uri, ms, false); askApplyAll = null }) { Text("只这一张") } }
        )
    }

    when (val s = save) {
        is LocalSaveState.Saving -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在加水印") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.done / s.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${s.done} / ${s.total}")
                }
            },
            confirmButton = {}
        )
        is LocalSaveState.Done -> AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = { Text(if (s.failed == 0) "已保存 ${s.saved.size} 张" else "已保存 ${s.saved.size} 张，${s.failed} 张失败") },
            text = { Text("保存在相册 Pictures/GeoTagCamera/本地水印。原图没有改动。") },
            confirmButton = {
                TextButton(onClick = { viewModel.clear() }) { Text("完成") }
            },
            dismissButton = {
                if (s.saved.isNotEmpty()) TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/jpeg"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(s.saved))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享照片"))
                }) { Text("分享") }
            }
        )
        LocalSaveState.Idle -> Unit
    }
}

@Composable
private fun EmptyState(loading: Boolean, onPick: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("给已有照片加水印", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "可一次选多张。拍摄时间优先读取照片的 EXIF，没有的可以手动设置；有 GPS 的照片会显示坐标和地址。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            if (loading) CircularProgressIndicator()
            else OutlinedButton(onClick = onPick, modifier = Modifier.width(220.dp).height(52.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择照片")
            }
        }
    }
}
