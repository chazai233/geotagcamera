package com.geotagcamera.geotagginglocationonphoto.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.data.ProjectWithCount
import com.geotagcamera.geotagginglocationonphoto.export.ExportFormat
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Filter chips over the grid: All, each active project, No project. */
@Composable
fun ProjectFilterRow(projects: List<ProjectWithCount>, filter: GalleryFilter, onFilter: (GalleryFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.M),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        item { FilterChip(selected = filter == GalleryFilter.All, onClick = { onFilter(GalleryFilter.All) }, label = { Text("全部") }) }
        items(projects.filter { !it.archived || (filter as? GalleryFilter.Project)?.id == it.id }, key = { it.id }) { p ->
            FilterChip(
                selected = (filter as? GalleryFilter.Project)?.id == p.id,
                onClick = { onFilter(GalleryFilter.Project(p.id)) },
                label = { Text(p.name) },
                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
        item {
            FilterChip(
                selected = filter == GalleryFilter.Unfiled,
                onClick = { onFilter(GalleryFilter.Unfiled) },
                label = { Text("未分项目") },
                leadingIcon = { Icon(Icons.Outlined.FolderOff, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

/** Pick CSV, KML or GeoJSON, with a hint of what opens each. */
@Composable
fun ExportFormatDialog(count: Int, onPick: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 $count 条位置数据") },
        text = {
            Column {
                ExportFormat.entries.forEach { f ->
                    ListItem(
                        headlineContent = { Text(f.label) },
                        supportingContent = { Text(f.hint) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(f) }
                    )
                }
                Text(
                    "包含时间、坐标、地点、备注、项目和每张照片的 SHA-256，不包含照片本身。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.S)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** Export/report progress, failure, or the ready file. */
@Composable
fun ExportProgressDialog(state: ExportState, onShare: (ExportState.Ready) -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ExportState.Idle -> Unit
        is ExportState.Working -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
            title = { Text(state.label) },
            text = {
                Column {
                    if (state.total > 0) {
                        LinearProgressIndicator(progress = { state.done / state.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.size(Spacing.S))
                        Text("${state.done} / ${state.total} 张，逐张重新校验", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
        is ExportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("导出失败") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("确定") } }
        )
        is ExportState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("已完成") },
            text = { Text("${state.title} 已生成，可以分享或保存。") },
            confirmButton = { TextButton(onClick = { onShare(state) }) { Text("分享或保存") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
        )
    }
}

/** Move the selected photos into a project (or out of all projects), or a new one. */
@Composable
fun MoveToProjectDialog(
    projects: List<ProjectWithCount>,
    count: Int,
    onMove: (Long?) -> Unit,
    onCreate: suspend (String) -> String?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动 $count 张照片") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(projects.filter { !it.archived }, key = { it.id }) { p ->
                        ListItem(
                            leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            headlineContent = { Text(p.name) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onMove(p.id) }
                        )
                    }
                    item {
                        ListItem(
                            leadingContent = { Icon(Icons.Outlined.FolderOff, contentDescription = null) },
                            headlineContent = { Text("不归入项目") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onMove(null) }
                        )
                    }
                }
                HorizontalDivider()
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80); error = null },
                        label = { Text("新项目名称") },
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)
                    )
                } else {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.S))
                        Text("新建项目")
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { scope.launch { error = onCreate(name); if (error == null) onDismiss() } }) { Text("新建并移动") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** Rename, archive or delete projects. Deleting never deletes photos. */
@Composable
fun ManageProjectsDialog(
    projects: List<ProjectWithCount>,
    onRename: (Long, String) -> Unit,
    onArchive: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var renaming by remember { mutableStateOf<ProjectWithCount?>(null) }
    var deleting by remember { mutableStateOf<ProjectWithCount?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("项目") },
        text = {
            if (projects.isEmpty()) {
                Text("还没有项目。可以在拍照界面取景框下方新建。")
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(projects, key = { it.id }) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${p.photoCount} photo" + (if (p.photoCount == 1) "" else "s") + if (p.archived) " · archived" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { renaming = p }) { Icon(Icons.Outlined.Edit, contentDescription = "重命名 ${p.name}") }
                            IconButton(onClick = { onArchive(p.id, !p.archived) }) {
                                Icon(if (p.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, contentDescription = if (p.archived) "取消归档" else "归档")
                            }
                            IconButton(onClick = { deleting = p }) { Icon(Icons.Outlined.Delete, contentDescription = "删除 ${p.name}") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )

    renaming?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名项目") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(80) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(p.id, text); renaming = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("删除项目「${p.name}」？") },
            text = { Text("只删除项目本身，其中 ${p.photoCount} 张照片仍保留在相册中，变为未分项目。") },
            confirmButton = { TextButton(onClick = { onDelete(p.id); deleting = null }) { Text("删除项目") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

