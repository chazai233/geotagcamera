package com.geotagcamera.geotagginglocationonphoto.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.stamp.CustomField
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields

/**
 * 自定义字段卡片：和设置页其它项一样是一行行的列表。
 * 行内开关控制是否显示；点行打开底部面板，编辑名称、内容和预设。
 */
@Composable
internal fun CustomFieldsCard(fields: StampFields, update: ((StampFields) -> StampFields) -> Unit) {
    var editingId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    val list = fields.customFields

    fun edit(id: Long, transform: (CustomField) -> CustomField) =
        update { f -> f.copy(customFields = f.customFields.map { if (it.id == id) transform(it) else it }) }

    SettingsCard("自定义字段") {
        list.forEachIndexed { index, field ->
            ListItem(
                headlineContent = { Text(field.label.ifBlank { "未命名字段" }) },
                supportingContent = {
                    val content = field.value.trim().ifEmpty { "未填写" }
                    val presets = if (field.presets.isEmpty()) "" else " · ${field.presets.size} 个预设"
                    Text(content + presets)
                },
                trailingContent = {
                    Switch(checked = field.enabled, onCheckedChange = { on -> edit(field.id) { it.copy(enabled = on) } })
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { editingId = field.id }
            )
            if (index < list.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        if (list.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ListItem(
            leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text("添加字段", color = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { adding = true }
        )
        Text(
            "每个字段在水印上显示为一行\"名称：内容\"，内容为空时不显示。拍照时点取景框下方的字段栏，可直接选预设。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
        )
    }

    if (adding) {
        AddFieldDialog(
            existing = list.map { it.label },
            onDismiss = { adding = false },
            onAdd = { label ->
                adding = false
                val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
                update { f -> f.copy(customFields = f.customFields + CustomField(id, label.take(CustomField.MAX_LABEL))) }
                // 新字段加好后直接打开编辑面板，接着填内容和预设
                editingId = id
            }
        )
    }

    // 从最新的设置里取，预设增删后面板立即刷新
    val editing = editingId?.let { id -> list.firstOrNull { it.id == id } }
    if (editingId != null && editing != null) {
        val index = list.indexOf(editing)
        FieldEditorSheet(
            field = editing,
            canMoveUp = index > 0,
            canMoveDown = index < list.lastIndex,
            onChange = { transform -> edit(editing.id, transform) },
            onMove = { delta ->
                update { f ->
                    val l = f.customFields.toMutableList()
                    val i = l.indexOfFirst { it.id == editing.id }
                    val j = i + delta
                    if (i < 0 || j !in l.indices) f else f.copy(customFields = l.apply { add(j, removeAt(i)) })
                }
            },
            onDelete = {
                editingId = null
                update { f -> f.copy(customFields = f.customFields.filter { it.id != editing.id }) }
            },
            onDismiss = { editingId = null }
        )
    }
}

/** 编辑一个字段：名称、当前内容、预设（选择 / 添加 / 排序删除）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FieldEditorSheet(
    field: CustomField,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: ((CustomField) -> CustomField) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // TextFieldState 保留输入法拼写状态，中文输入不会重复上屏；
    // 文本只从输入框流向设置，设置回写不会再改输入框。
    val labelInput = rememberTextFieldState(field.label)
    val valueInput = rememberTextFieldState(field.value)
    val presetInput = rememberTextFieldState("")
    var arranging by remember(field.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val currentOnChange by rememberUpdatedState(onChange)

    LaunchedEffect(labelInput) {
        snapshotFlow { labelInput.text.toString() }.drop(1).collect { t -> currentOnChange { f -> f.copy(label = CustomField.clean(t)) } }
    }
    LaunchedEffect(valueInput) {
        snapshotFlow { valueInput.text.toString() }.drop(1).collect { t -> currentOnChange { f -> f.copy(value = CustomField.clean(t)) } }
    }
    val value = valueInput.text.toString()

    fun addPresets(raw: String) {
        val items = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isNotEmpty()) currentOnChange { it.withPresets(items) }
    }
    // 预设输入框允许回车/粘贴多行：一出现换行就把已输入的各行加为预设并清空
    LaunchedEffect(presetInput) {
        snapshotFlow { presetInput.text.toString() }.collect { t ->
            if (t.contains('\n')) {
                addPresets(t)
                presetInput.clearText()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text("编辑字段", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                state = labelInput,
                label = { Text("名称") },
                placeholder = { Text("例如 施工部位") },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(CustomField.MAX_LABEL),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                state = valueInput,
                label = { Text("当前内容") },
                placeholder = { Text("输入或从下方预设中选") },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(CustomField.MAX_VALUE),
                trailingIcon = {
                    if (value.isNotEmpty()) IconButton(onClick = { valueInput.clearText() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ---- 预设 ----
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "预设" + if (field.presets.isEmpty()) "" else "（${field.presets.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (field.presets.isNotEmpty()) {
                    TextButton(onClick = { arranging = !arranging }) { Text(if (arranging) "完成" else "排序和删除") }
                }
            }

            AnimatedContent(targetState = arranging, label = "presetMode") { arrange ->
                if (field.presets.isEmpty()) {
                    Text(
                        "常用的内容存成预设，拍照时一点就能选。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else if (!arrange) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        field.presets.forEach { pr ->
                            FilterChip(
                                selected = pr == value.trim(),
                                onClick = { valueInput.setTextAndPlaceCursorAtEnd(pr) },
                                label = { Text(pr) }
                            )
                        }
                    }
                } else {
                    Column {
                        field.presets.forEachIndexed { i, pr ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pr, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onChange { it.movePreset(pr, -1) } }, enabled = i > 0) {
                                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移")
                                }
                                IconButton(onClick = { onChange { it.movePreset(pr, 1) } }, enabled = i < field.presets.lastIndex) {
                                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移")
                                }
                                IconButton(onClick = { onChange { it.withoutPreset(pr) } }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (i < field.presets.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            if (!arranging) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    state = presetInput,
                    placeholder = { Text("添加预设，回车添加，可粘贴多行") },
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 4),
                    trailingIcon = {
                        IconButton(
                            onClick = { addPresets(presetInput.text.toString()); presetInput.clearText() },
                            enabled = presetInput.text.isNotBlank()
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "添加预设")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.isNotBlank() && value.trim() !in field.presets) {
                    TextButton(onClick = { addPresets(value) }) {
                        Text("把当前内容存为预设")
                    }
                }
            }

            // ---- 字段操作 ----
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("上移")
                }
                TextButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("下移")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除字段", color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("完成") }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除字段「${field.label.ifBlank { "未命名" }}」？") },
            text = { Text("字段和它的 ${field.presets.size} 个预设都会删除。已拍的照片不受影响。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFieldDialog(existing: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    val suggestions = CustomField.SUGGESTED_LABELS.filter { it !in existing }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加字段") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(CustomField.MAX_LABEL) },
                    label = { Text("字段名称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) onAdd(custom.trim()) }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { l ->
                            FilterChip(selected = custom == l, onClick = { custom = l }, label = { Text(l) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(custom.trim()) }, enabled = custom.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 拍照面板用的预设选择：选中的高亮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresetChips(presets: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { pr ->
            FilterChip(selected = pr == selected.trim(), onClick = { onSelect(pr) }, label = { Text(pr) })
        }
    }
}
