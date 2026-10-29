package com.geotagcamera.geotagginglocationonphoto.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.key
import com.geotagcamera.geotagginglocationonphoto.stamp.CustomField
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.data.CaptureResolution
import com.geotagcamera.geotagginglocationonphoto.data.CustomTime
import com.geotagcamera.geotagginglocationonphoto.stamp.DateFormats
import com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampTemplate
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// 照片：分辨率、JPEG 质量、保存原图
// ---------------------------------------------------------------------------

@Composable
internal fun PhotoSettingsCard(
    resolution: CaptureResolution,
    jpegQuality: Int,
    saveOriginal: Boolean,
    onResolution: (CaptureResolution) -> Unit,
    onJpegQuality: (Int) -> Unit,
    onSaveOriginal: (Boolean) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    SettingsCard("照片") {
        ListItem(
            headlineContent = { Text("照片分辨率") },
            supportingContent = { Text("${resolution.label} · ${resolution.detail}") },
            trailingContent = { Text("更改", color = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { showPicker = true }
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        LabeledSlider(
            title = "JPEG 质量",
            value = jpegQuality.toFloat(),
            range = 60f..100f,
            steps = 39,
            valueText = { v ->
                val q = v.roundToInt()
                "$q" + when {
                    q >= 95 -> "（最高，文件大）"
                    q >= 88 -> "（推荐）"
                    q >= 75 -> "（省空间）"
                    else -> "（体积最小，画质下降明显）"
                }
            },
            onCommit = { onJpegQuality(it.roundToInt()) }
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ToggleListRow(
            title = "同时保存原图",
            subtitle = "每次拍照另存一张无水印原图到 Pictures/GeoTagCamera/原图",
            checked = saveOriginal,
            onChange = onSaveOriginal
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("照片分辨率") },
            text = {
                Column {
                    CaptureResolution.entries.forEach { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onResolution(r); showPicker = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = r == resolution, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(r.label, fontWeight = FontWeight.Medium)
                                Text(r.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "实际像素取决于手机摄像头，相机会选最接近的尺寸。部分手机不支持最高像素模式，会自动退回 1200 万。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("关闭") } }
        )
    }
}

// ---------------------------------------------------------------------------
// 水印样式：模板、位置、大小、颜色、透明度、边距
// ---------------------------------------------------------------------------

private val BG_SWATCHES = listOf(
    0x121417 to "黑", 0xFFFFFF to "白", 0x37474F to "深灰", 0x0D47A1 to "深蓝",
    0x1B5E20 to "深绿", 0xB71C1C to "红", 0xF9A825 to "工程黄", 0x4E342E to "棕"
)
private val TEXT_SWATCHES = listOf(
    0xFFFFFF to "白", 0x000000 to "黑", 0xFFEB3B to "黄", 0xFF5252 to "红",
    0x69F0AE to "绿", 0x40C4FF to "蓝", 0xFF9800 to "橙"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StyleSettingsCard(fields: StampFields, update: ((StampFields) -> StampFields) -> Unit) {
    SettingsCard("水印样式") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            StampTemplate.entries.forEachIndexed { i, template ->
                SegmentedButton(
                    selected = template == fields.template,
                    onClick = { update { it.copy(template = template, position = fitAnchor(it.position, template)) } },
                    shape = SegmentedButtonDefaults.itemShape(i, StampTemplate.entries.size),
                    label = { Text(if (template == StampTemplate.BAR) "信息栏" else "简洁") }
                )
            }
        }
        Text(
            when (fields.template) {
                StampTemplate.BAR -> "所有已开启的信息排成一栏，放在照片顶部或底部。"
                StampTemplate.MINIMAL -> "简洁文字：只显示自定义字段、坐标和时间，可放在九宫格任意位置。背景不透明度调到 0% 就是纯文字效果（自动加阴影）。"
            } + " 也可以在拍照界面长按水印拖动。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        SectionLabel("位置")
        PositionGrid(
            selected = fields.position,
            template = fields.template,
            onSelect = { a -> update { it.copy(position = a) } },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LabeledSlider(
            title = "水印大小",
            value = fields.styleScale,
            range = 0.5f..2.5f,
            steps = 19,
            valueText = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> update { it.copy(styleScale = v) } }
        )
        LabeledSlider(
            title = "左右边距",
            value = fields.marginX,
            range = 0f..80f,
            steps = 15,
            valueText = { "${it.roundToInt()}" },
            onCommit = { v -> update { it.copy(marginX = v) } }
        )
        LabeledSlider(
            title = "上下边距",
            value = fields.marginY,
            range = 0f..80f,
            steps = 15,
            valueText = { "${it.roundToInt()}" },
            onCommit = { v -> update { it.copy(marginY = v) } }
        )

        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        SectionLabel("背景颜色")
        ColorRow(BG_SWATCHES, fields.bgColor) { c -> update { it.copy(bgColor = c) } }
        LabeledSlider(
            title = "背景不透明度",
            value = fields.bgOpacity,
            range = 0f..1f,
            steps = 19,
            valueText = { "${(it * 100).roundToInt()}%" + if (it < 0.01f) "（无背景）" else "" },
            onCommit = { v -> update { it.copy(bgOpacity = v) } }
        )

        SectionLabel("文字颜色")
        ColorRow(TEXT_SWATCHES, fields.textColor) { c -> update { it.copy(textColor = c) } }
        LabeledSlider(
            title = "文字不透明度",
            value = fields.textOpacity,
            range = 0.2f..1f,
            steps = 15,
            valueText = { "${(it * 100).roundToInt()}%" },
            onCommit = { v -> update { it.copy(textOpacity = v) } }
        )

        TextButton(
            onClick = {
                update {
                    val d = StampFields()
                    it.copy(
                        styleScale = d.styleScale, bgColor = d.bgColor, bgOpacity = d.bgOpacity,
                        textColor = d.textColor, textOpacity = d.textOpacity,
                        marginX = d.marginX, marginY = d.marginY
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) { Text("恢复默认样式") }
    }
}

/** 信息栏只能放在顶部或底部一行，切换模板时把位置挪到合法格子。 */
private fun fitAnchor(anchor: StampAnchor, template: StampTemplate): StampAnchor =
    if (template == StampTemplate.BAR && anchor.row == 1) StampAnchor.entries[6 + anchor.column] else anchor

@Composable
private fun PositionGrid(
    selected: StampAnchor,
    template: StampTemplate,
    onSelect: (StampAnchor) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0..2) {
                    val anchor = StampAnchor.entries[row * 3 + col]
                    val enabled = !(template == StampTemplate.BAR && row == 1)
                    val isSel = anchor == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isSel -> MaterialTheme.colorScheme.primary
                                    enabled -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                                }
                            )
                            .clickable(enabled = enabled) { onSelect(anchor) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSel) Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(swatches: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    var showCustom by remember { mutableStateOf(false) }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        swatches.forEach { (rgb, name) ->
            ColorDot(rgb, selected == rgb, name) { onSelect(rgb) }
        }
        val isCustom = swatches.none { it.first == selected }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isCustom) Color(0xFF000000.toInt() or selected) else MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(if (isCustom) 3.dp else 1.dp, MaterialTheme.colorScheme.primary), CircleShape)
                .clickable(onClickLabel = "自定义颜色") { showCustom = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Palette, contentDescription = "自定义颜色", tint = if (isCustom) contrastOn(selected) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
    if (showCustom) {
        CustomColorDialog(initial = selected, onDismiss = { showCustom = false }, onPick = { onSelect(it); showCustom = false })
    }
}

@Composable
private fun ColorDot(rgb: Int, selected: Boolean, name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF000000.toInt() or rgb))
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                CircleShape
            )
            .clickable(onClickLabel = name, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = name, tint = contrastOn(rgb), modifier = Modifier.size(18.dp))
    }
}

private fun contrastOn(rgb: Int): Color {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return if (0.299 * r + 0.587 * g + 0.114 * b > 150) Color.Black else Color.White
}

/** RGB 滑块 + 十六进制输入的自定义颜色。 */
@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var r by remember { mutableFloatStateOf(((initial shr 16) and 0xFF).toFloat()) }
    var g by remember { mutableFloatStateOf(((initial shr 8) and 0xFF).toFloat()) }
    var b by remember { mutableFloatStateOf((initial and 0xFF).toFloat()) }
    fun rgb() = (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF000000.toInt() or rgb()))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(10.dp))
                )
                listOf(Triple("红", r, 0), Triple("绿", g, 1), Triple("蓝", b, 2)).forEach { (label, v, idx) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.width(24.dp))
                        Slider(
                            value = v,
                            onValueChange = { nv ->
                                when (idx) { 0 -> r = nv; 1 -> g = nv; else -> b = nv }
                                hex = "%06X".format(rgb())
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${v.roundToInt()}", modifier = Modifier.width(36.dp), fontFamily = FontFamily.Monospace)
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        val clean = input.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hex = clean
                        if (clean.length == 6) {
                            val v = clean.toInt(16)
                            r = ((v shr 16) and 0xFF).toFloat(); g = ((v shr 8) and 0xFF).toFloat(); b = (v and 0xFF).toFloat()
                        }
                    },
                    label = { Text("十六进制，如 FFEB3B") },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(rgb()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------------------------------------------------------------------
// 日期时间：格式、自定义格式、自定义时间
// ---------------------------------------------------------------------------

@Composable
internal fun DateTimeSettingsCard(
    fields: StampFields,
    customTime: CustomTime,
    update: ((StampFields) -> StampFields) -> Unit,
    onSetCustomTime: (Long) -> Unit,
    onCustomTimeEnabled: (Boolean) -> Unit
) {
    // 每秒刷新一次示例，看得到秒在走
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            now = System.currentTimeMillis()
        }
    }
    var showFormats by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var pickedDateUtcMs by remember { mutableStateOf<Long?>(null) }

    val stampNow = customTime.toStampTime(now)

    SettingsCard("日期时间") {
        ListItem(
            headlineContent = { Text("日期时间格式") },
            supportingContent = {
                Column {
                    Text(DateFormats.format(fields.dateFormat, stampNow), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(fields.dateFormat, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = { Text("更改", color = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { showFormats = true }
        )
        TimeLabelEditor(fields.timeLabel) { v -> update { it.copy(timeLabel = v) } }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ToggleListRow(
            title = "自定义拍摄时间",
            subtitle = if (customTime.enabled) "水印、照片 EXIF 和相册都使用设定的时间，时钟会从设定时间继续走"
            else "关闭：使用手机当前时间",
            checked = customTime.enabled,
            onChange = { on -> if (on && customTime.offsetMs == 0L) showDate = true else onCustomTimeEnabled(on) }
        )
        if (customTime.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("当前水印时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateFormats.format("yyyy-MM-dd HH:mm:ss EEEE", stampNow), fontWeight = FontWeight.Medium)
                }
                OutlinedButton(onClick = { showDate = true }) { Text("设置时间") }
            }
            Text(
                "签名证明会同时记录真实拍摄时间，在\"校验照片\"中可以看到。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    if (showFormats) {
        DateFormatDialog(
            current = fields.dateFormat,
            sampleMs = stampNow,
            onDismiss = { showFormats = false },
            onPick = { f -> update { it.copy(dateFormat = f) }; showFormats = false }
        )
    }

    if (showDate) {
        DateStep(
            initialMs = stampNow,
            onDismiss = { showDate = false },
            onPicked = { utc -> showDate = false; pickedDateUtcMs = utc }
        )
    }
    pickedDateUtcMs?.let { utcDate ->
        TimeStep(
            initialMs = stampNow,
            onDismiss = { pickedDateUtcMs = null },
            onPicked = { time ->
                pickedDateUtcMs = null
                val date = Instant.ofEpochMilli(utcDate).atZone(ZoneOffset.UTC).toLocalDate()
                val target = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                onSetCustomTime(target)
            }
        )
    }
}

@Composable
private fun DateFormatDialog(current: String, sampleMs: Long, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf(if (current in DateFormats.PRESETS) "" else current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("日期时间格式") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DateFormats.PRESETS.forEach { f ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(f) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = f == current, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(DateFormats.format(f, sampleMs))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("自定义格式", fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(60) },
                    placeholder = { Text("例如 yyyy年M月d日 HH:mm") },
                    singleLine = true,
                    isError = custom.isNotBlank() && !DateFormats.isValid(custom),
                    supportingText = {
                        Text(
                            if (custom.isBlank()) "yyyy 年 · MM 月 · dd 日 · HH 时(24) · hh 时(12) · mm 分 · ss 秒 · EEEE 星期 · a 上午/下午；中文字用单引号括起也可以"
                            else if (DateFormats.isValid(custom)) "预览：" + DateFormats.format(custom, sampleMs)
                            else "格式有误"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(custom.trim()) },
                enabled = DateFormats.isValid(custom.trim())
            ) { Text("使用自定义格式") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStep(initialMs: Long, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    // DatePicker 用 UTC 零点表示日期
    val localDate = Instant.ofEpochMilli(initialMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val state = rememberDatePickerState(initialSelectedDateMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let(onPicked) }, enabled = state.selectedDateMillis != null) { Text("下一步：时间") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        DatePicker(state = state, title = { Text("选择日期", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeStep(initialMs: Long, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val t = Instant.ofEpochMilli(initialMs).atZone(ZoneId.systemDefault()).toLocalTime()
    val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------------------------------------------------------------------
// 通用小部件
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** 拖动时只改本地值，松手才写入设置，预览不卡。 */
@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: (Float) -> String,
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(valueText(local), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
internal fun ToggleListRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

// ---------------------------------------------------------------------------
// 自定义字段：施工区域、施工内容……
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomFieldsCard(fields: StampFields, update: ((StampFields) -> StampFields) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    val list = fields.customFields

    fun edit(id: Long, transform: (CustomField) -> CustomField) =
        update { f -> f.copy(customFields = f.customFields.map { if (it.id == id) transform(it) else it }) }

    fun move(id: Long, delta: Int) = update { f ->
        val l = f.customFields.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) f else f.copy(customFields = l.apply { add(j, removeAt(i)) })
    }

    SettingsCard("自定义字段") {
        Text(
            "每条显示为一行\"名称：内容\"，排在时间前面。内容为空的字段不显示。拍照界面点下方的项目栏可以快速改内容。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
        )
        list.forEachIndexed { index, field ->
            key(field.id) {
                CustomFieldEditor(
                    field = field,
                    canMoveUp = index > 0,
                    canMoveDown = index < list.lastIndex,
                    onLabel = { v -> edit(field.id) { it.copy(label = v) } },
                    onValue = { v -> edit(field.id) { it.copy(value = v) } },
                    onEnabled = { v -> edit(field.id) { it.copy(enabled = v) } },
                    onUp = { move(field.id, -1) },
                    onDown = { move(field.id, 1) },
                    onDelete = { update { f -> f.copy(customFields = f.customFields.filter { it.id != field.id }) } }
                )
            }
            if (index < list.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        TextButton(onClick = { showAdd = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加字段")
        }
    }

    if (showAdd) {
        AddFieldDialog(
            existing = list.map { it.label },
            onDismiss = { showAdd = false },
            onAdd = { label ->
                showAdd = false
                update { f ->
                    val id = (f.customFields.maxOfOrNull { it.id } ?: 0L) + 1
                    f.copy(customFields = f.customFields + CustomField(id, label.take(CustomField.MAX_LABEL)))
                }
            }
        )
    }
}

@Composable
private fun CustomFieldEditor(
    field: CustomField,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onLabel: (String) -> Unit,
    onValue: (String) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit
) {
    // 本地文本状态：输入不等 DataStore 回写，光标不会跳。
    var label by remember(field.id) { mutableStateOf(field.label) }
    // 设置页打开时拍照界面不会同时改内容，所以不用回写同步（回写会在快速输入时吞字）。
    var value by remember(field.id) { mutableStateOf(field.value) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(CustomField.MAX_LABEL); onLabel(label) },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.weight(0.8f)
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(CustomField.MAX_VALUE); onValue(value) },
                label = { Text("内容") },
                singleLine = true,
                modifier = Modifier.weight(1.2f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = field.enabled, onCheckedChange = onEnabled)
            Spacer(Modifier.width(8.dp))
            Text(if (field.enabled) "显示" else "隐藏", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onUp, enabled = canMoveUp) { Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移") }
            IconButton(onClick = onDown, enabled = canMoveDown) { Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "删除字段") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFieldDialog(existing: List<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加字段") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("常用", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomField.SUGGESTED_LABELS.filter { it !in existing }.forEach { l ->
                        SuggestionChip(onClick = { onAdd(l) }, label = { Text(l) })
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(CustomField.MAX_LABEL) },
                    label = { Text("或输入名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(custom.trim()) }, enabled = custom.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 时间前的标签，如"拍摄时间：2026.10.29 18:12:46"。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeLabelEditor(current: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(CustomField.MAX_LABEL); onChange(text) },
            label = { Text("时间前的名称（可空）") },
            placeholder = { Text("例如 拍摄时间") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            listOf("" to "不加", "拍摄时间" to "拍摄时间", "时间" to "时间", "日期" to "日期").forEach { (v, l) ->
                SuggestionChip(onClick = { text = v; onChange(v) }, label = { Text(l) })
            }
        }
    }
}
