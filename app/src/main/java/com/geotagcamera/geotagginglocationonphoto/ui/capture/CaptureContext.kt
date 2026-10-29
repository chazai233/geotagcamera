package com.geotagcamera.geotagginglocationonphoto.ui.capture

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChip
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.mutableStateMapOf
import com.geotagcamera.geotagginglocationonphoto.stamp.CustomField
import com.geotagcamera.geotagginglocationonphoto.ui.settings.PresetChips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.data.CapturePreferences
import com.geotagcamera.geotagginglocationonphoto.data.ProjectEntity
import com.geotagcamera.geotagginglocationonphoto.data.ProjectWithCount
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ChromeTokens
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The "what is this shot for" row under the viewfinder: active project and
 * the sticky note. Both are optional; when empty they read as quiet prompts.
 */
@Composable
fun CaptureContextRow(
    project: ProjectEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fields: List<CustomField> = emptyList(),
    showProject: Boolean = true
) {
    // 取景框下方的字段卡：每个字段一行、完整显示，点一下打开面板选预设
    Row(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, ChromeTokens.GlassBorder), RoundedCornerShape(16.dp))
            .background(ChromeTokens.Glass)
            .clickable(onClickLabel = "修改水印字段", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showProject) {
                Pill(if (project != null) Icons.Outlined.Folder else Icons.Outlined.FolderOff, project?.name ?: "未选项目", project != null, Modifier)
            }
            if (fields.isEmpty()) {
                Text("点此填写水印字段", color = ChromeTokens.InkMuted, style = MaterialTheme.typography.labelLarge)
            }
            fields.forEach { f ->
                val v = f.value.trim()
                Text(
                    buildString {
                        if (f.label.isNotBlank()) append(f.label).append("：")
                        append(v.ifEmpty { "未填写" })
                    },
                    color = if (v.isEmpty()) ChromeTokens.InkMuted else ChromeTokens.Ink,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = ChromeTokens.InkMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String, active: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (active) ChromeTokens.Ink else ChromeTokens.InkMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (active) ChromeTokens.Ink else ChromeTokens.InkMuted, style = MaterialTheme.typography.labelLarge)
    }
}

/** Pick the project, write the note, or start a new project, in one sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureContextSheet(
    projects: List<ProjectWithCount>,
    activeProjectId: Long?,
    note: String,
    onSelectProject: (Long?) -> Unit,
    onCreateProject: suspend (String) -> String?,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit,
    customFields: List<CustomField> = emptyList(),
    onFieldValues: (Map<Long, String>) -> Unit = {},
    onAddPreset: (Long, String) -> Unit = { _, _ -> },
    onRemoveHistory: (Long, String) -> Unit = { _, _ -> },
    showProjects: Boolean = true,
    onManageProjects: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var noteText by remember { mutableStateOf(note) }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 只编辑开着的字段；关闭面板时一次写回
    val shownFields = customFields.filter { it.enabled }
    val fieldValues = remember { mutableStateMapOf<Long, String>().apply { shownFields.forEach { put(it.id, it.value) } } }

    fun close() {
        onNoteChange(noteText)
        onFieldValues(fieldValues.filter { (id, v) -> shownFields.firstOrNull { it.id == id }?.value != v })
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = { close() }, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = Spacing.XL)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            if (shownFields.isNotEmpty()) {
                Text("水印字段", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(Spacing.S))
                shownFields.forEach { f -> key(f.id) {
                    // TextFieldState 完整保留输入法的拼写状态；用简单的 String 写法时，
                    // 拍照界面每秒刷新会让中文输入法重复上屏（"船闸开挖开挖"）。
                    val input = rememberTextFieldState(fieldValues[f.id] ?: f.value)
                    LaunchedEffect(input) {
                        snapshotFlow { input.text.toString() }.collect { fieldValues[f.id] = CustomField.clean(it) }
                    }
                    val v = input.text.toString()
                    OutlinedTextField(
                        state = input,
                        label = { Text(f.label.ifBlank { "（无名称）" }) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = InputTransformation.maxLength(CustomField.MAX_VALUE),
                        trailingIcon = {
                            if (v.isNotEmpty()) IconButton(onClick = { input.clearText() }) { Icon(Icons.Outlined.Close, contentDescription = "清空") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 预设：点一下就选中
                    if (f.presets.isNotEmpty()) {
                        Spacer(Modifier.size(Spacing.XS))
                        PresetChips(presets = f.presets, selected = v, onSelect = { input.setTextAndPlaceCursorAtEnd(it) })
                    }
                    // 最近用过但不在预设里的
                    val recent = f.history.filter { it != v && it !in f.presets }
                    if (recent.isNotEmpty()) {
                        Text("最近", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.XS))
                        // 点一下填入，点 × 从最近记录里删掉；长内容整条换行显示，不被截断
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            recent.forEach { h ->
                                InputChip(
                                    selected = false,
                                    onClick = { input.setTextAndPlaceCursorAtEnd(h) },
                                    label = { Text(h) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "删除这条最近记录",
                                            modifier = Modifier.size(18.dp).clickable { onRemoveHistory(f.id, h) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                    if (v.isNotBlank() && v.trim() !in f.presets) {
                        TextButton(onClick = { onAddPreset(f.id, v.trim()) }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.XS))
                            Text("把「${v.trim()}」存为预设")
                        }
                    }
                    Spacer(Modifier.size(Spacing.S))
                } }
                Text(
                    "字段和预设在 设置 → 自定义字段 里管理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(Spacing.L))
            }
            if (showProjects) {
            Spacer(Modifier.size(Spacing.L))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前项目", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onManageProjects) { Text("管理项目") }
            }
            Text(
                "选中的项目会自动归入之后拍的每张照片（不显示在水印上）。相册里可按项目筛选、导出位置数据、生成 PDF 报告。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(Spacing.XS))
            val active = projects.filter { !it.archived }
            // 面板整体可滚动，这里不能再嵌套 LazyColumn
            ProjectRow("不选项目", null, activeProjectId == null) { onSelectProject(null) }
            active.forEach { p ->
                ProjectRow(p.name, "${p.photoCount} 张", p.id == activeProjectId) { onSelectProject(p.id) }
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.S))
            if (creating) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(80); error = null },
                    label = { Text("新项目名称") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        scope.launch { error = onCreateProject(newName); if (error == null) { creating = false; newName = "" } }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { creating = false; newName = ""; error = null }) { Text("取消") }
                    TextButton(onClick = {
                        scope.launch { error = onCreateProject(newName); if (error == null) { creating = false; newName = "" } }
                    }) { Text("创建") }
                }
            } else {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.S))
                    Text("新建项目")
                }
            }
            }
            Spacer(Modifier.size(Spacing.L))
        }
    }
}

@Composable
private fun ProjectRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.M))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
