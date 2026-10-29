package com.geotagcamera.geotagginglocationonphoto.ui.settings

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.geotagcamera.geotagginglocationonphoto.stamp.CustomField
import kotlinx.coroutines.flow.drop

import com.geotagcamera.geotagginglocationonphoto.data.CaptureResolution
import com.geotagcamera.geotagginglocationonphoto.data.CustomTime

import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import com.geotagcamera.geotagginglocationonphoto.stamp.LogoDecoder
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampOverlay
import com.geotagcamera.geotagginglocationonphoto.stamp.StampTemplate
import com.geotagcamera.geotagginglocationonphoto.stamp.buildStampSpec
import com.geotagcamera.geotagginglocationonphoto.ui.common.AppScaffold
import com.geotagcamera.geotagginglocationonphoto.ui.common.rememberPhotoPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.geotagcamera.geotagginglocationonphoto.profile.UserProfile

private data class StampToggle(
    val label: String,
    val description: String,
    val isChecked: (StampFields) -> Boolean,
    val apply: (StampFields, Boolean) -> StampFields
)

// The four most-changed fields; the rest sit behind "More fields".
private val SURFACED = listOf(
    StampToggle("地址", "反查得到的地址和 Plus Code", { it.showAddress }, { f, v -> f.copy(showAddress = v) }),
    StampToggle("经纬度", "纬度和经度", { it.showCoordinates }, { f, v -> f.copy(showCoordinates = v) }),
    StampToggle("日期时间", "拍摄时间，格式在下方\"日期时间\"中设置", { it.showTimestamp }, { f, v -> f.copy(showTimestamp = v) }),
    StampToggle("地图缩略图", "小幅街道地图（需联网或离线地图）", { it.showMap }, { f, v -> f.copy(showMap = v) })
)

private val MORE = listOf(
    StampToggle("国家代码", "地名旁的三字母国家代码", { it.showCountry }, { f, v -> f.copy(showCountry = v) }),
    StampToggle("时区", "时间后显示 GMT 时区", { it.showGmtOffset }, { f, v -> f.copy(showGmtOffset = v) }),
    StampToggle("海拔", "海拔高度", { it.showAltitude }, { f, v -> f.copy(showAltitude = v) }),
    StampToggle("定位精度", "GPS 精度半径", { it.showAccuracy }, { f, v -> f.copy(showAccuracy = v) }),
    StampToggle("方向", "移动方向（方位角）", { it.showBearing }, { f, v -> f.copy(showBearing = v) }),
    StampToggle("天气", "气温和天气（需联网）", { it.showWeather }, { f, v -> f.copy(showWeather = v) }),
    StampToggle("单位名称", "在水印右侧显示单位名称", { it.showOrgLabelToggle }, { f, v -> f.copy(showOrgLabelToggle = v) }),
    StampToggle("单位 Logo", "在水印右侧显示 Logo", { it.showOrgLogo }, { f, v -> f.copy(showOrgLogo = v) }),
    StampToggle("签名标记", "有手写签名时显示\"已签名\"", { it.showSignatureField }, { f, v -> f.copy(showSignatureField = v) }),
    StampToggle("应用标记", "右下角的\"工程水印\"小字", { it.showBrandMark }, { f, v -> f.copy(showBrandMark = v) }),
    StampToggle("自定义文字", "在\"单位信息\"里设置的固定文字", { it.showCustomText }, { f, v -> f.copy(showCustomText = v) })
)

/** A fixed, recognisable sample so the preview shows every enabled field. */
private val SAMPLE_FIX = LocationFix(
    latitude = 30.123456, longitude = 110.654321, altitudeMeters = 340.0,
    accuracyMeters = 4f, bearingDegrees = 135f, timestampEpochMs = 0L
)
private val SAMPLE_ADDRESS = AddressParts(
    place = "示例市 示例区",
    addressLine = "示例路 1 号 施工现场",
    countryName = "中国", countryCode = "CN", fromCache = false
)

@Composable
fun SettingsScreen(
    onOpenVerify: () -> Unit = {},
    onOpenAboutLegal: () -> Unit = {},
    onOpenOfflineMaps: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val loaded by viewModel.fields.collectAsStateWithLifecycle()
    val autoDismiss by viewModel.autoDismissReview.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val resolution by viewModel.resolution.collectAsStateWithLifecycle()
    val jpegQuality by viewModel.jpegQuality.collectAsStateWithLifecycle()
    val saveOriginal by viewModel.saveOriginal.collectAsStateWithLifecycle()
    val customTime by viewModel.customTime.collectAsStateWithLifecycle()
    val useProjects by viewModel.useProjects.collectAsStateWithLifecycle()

    AppScaffold(title = "设置") { padding ->
        val fields = loaded
        if (fields == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@AppScaffold
        }
        SettingsContent(
            fields = fields,
            autoDismiss = autoDismiss,
            profile = profile,
            resolution = resolution,
            jpegQuality = jpegQuality,
            saveOriginal = saveOriginal,
            customTime = customTime,
            useProjects = useProjects,
            contentPadding = padding,
            viewModel = viewModel,
            onOpenVerify = onOpenVerify,
            onOpenAboutLegal = onOpenAboutLegal,
            onOpenOfflineMaps = onOpenOfflineMaps,
            onOpenReport = onOpenReport
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    fields: StampFields,
    autoDismiss: Boolean,
    profile: UserProfile,
    resolution: CaptureResolution,
    jpegQuality: Int,
    saveOriginal: Boolean,
    customTime: CustomTime,
    useProjects: Boolean,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel,
    onOpenVerify: () -> Unit,
    onOpenAboutLegal: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenReport: () -> Unit
) {
    var showAll by rememberSaveable(profile) { mutableStateOf(profile.showsAllSettings) }
    val pickLogo = rememberPhotoPicker { uri -> viewModel.setOrgLogo(uri) }

    // 预览固定在顶部，往下调设置时也一直看得到效果
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        StampPreview(fields, customTime, Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
        HorizontalDivider()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader("水印内容")
        CustomFieldsCard(fields) { transform -> viewModel.update(transform) }

        DateTimeSettingsCard(
            fields = fields,
            customTime = customTime,
            update = { transform -> viewModel.update(transform) },
            onSetCustomTime = viewModel::setCustomTime,
            onCustomTimeEnabled = viewModel::setCustomTimeEnabled
        )

        SettingsCard("显示哪些信息") {
            SURFACED.forEach { ToggleRow(it, fields, viewModel) }
            AnimatedVisibility(visible = showAll) {
                Column { MORE.forEach { ToggleRow(it, fields, viewModel) } }
            }
            TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(if (showAll) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (showAll) "收起" else "更多字段（${MORE.size} 项）")
            }
        }

        SettingsCard("单位信息") {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                // TextFieldState：中文输入法拼写状态完整保留，不会重复上屏
                val orgInput = rememberTextFieldState(fields.orgLabel)
                LaunchedEffect(orgInput) {
                    snapshotFlow { orgInput.text.toString() }.drop(1).collect { t -> viewModel.update { it.copy(orgLabel = CustomField.clean(t)) } }
                }
                OutlinedTextField(
                    state = orgInput,
                    label = { Text("单位、公司或项目名称") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                val customInput = rememberTextFieldState(fields.customText)
                LaunchedEffect(customInput) {
                    snapshotFlow { customInput.text.toString() }.drop(1).collect { t -> viewModel.update { it.copy(customText = CustomField.clean(t)) } }
                }
                OutlinedTextField(
                    state = customInput,
                    label = { Text("每张照片都显示的文字（可选）") },
                    supportingText = { Text("例如合同编号、监理姓名。") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = InputTransformation.maxLength(120),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val logoPath = fields.orgLogoUri
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoPath != null) {
                            AsyncImage(
                                model = File(logoPath),
                                contentDescription = "单位 Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                        } else {
                            Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(onClick = pickLogo) { Text(if (logoPath != null) "更换 Logo" else "选择 Logo") }
                    if (logoPath != null) {
                        TextButton(onClick = { viewModel.clearOrgLogo() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("移除")
                        }
                    }
                }
            }
        }

        SectionHeader("外观")
        StyleSettingsCard(fields) { transform -> viewModel.update(transform) }

        SectionHeader("拍照")
        PhotoSettingsCard(
            resolution = resolution,
            jpegQuality = jpegQuality,
            saveOriginal = saveOriginal,
            onResolution = viewModel::setResolution,
            onJpegQuality = viewModel::setJpegQuality,
            onSaveOriginal = viewModel::setSaveOriginal
        )

        SettingsCard("拍摄") {
            // Field keeps this one tucked away unless it's already on.
            if (profile.showsAllSettings || fields.requireSignature || showAll) {
                SwitchRow(
                    title = "必须签名",
                    subtitle = "每张照片保存前都要求手写签名",
                    checked = fields.requireSignature,
                    onChange = { checked -> viewModel.update { it.copy(requireSignature = checked) } }
                )
            }
            SwitchRow(
                title = "按项目分组照片",
                subtitle = if (useProjects) "拍照界面可选当前项目，新照片自动归入；相册可按项目筛选、导出、出 PDF 报告"
                else "关闭：拍照界面不显示项目",
                checked = useProjects,
                onChange = { viewModel.setUseProjects(it) }
            )
            SwitchRow(
                title = "自动关闭预览",
                subtitle = "拍完 4 秒后自动回到相机",
                checked = autoDismiss,
                onChange = { checked -> viewModel.setAutoDismissReview(checked) }
            )
        }

        SettingsCard("使用场景") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                UserProfile.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = p == profile,
                        onClick = { if (p != profile) viewModel.setProfile(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, UserProfile.entries.size),
                        label = { Text(p.title) }
                    )
                }
            }
            Text(
                profile.summary + " 切换后会套用该场景推荐的水印字段。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        SectionHeader("其他")
        SettingsCard("更多") {
            NavRow(Icons.Outlined.Map, "离线地图", "下载区域地图，没信号时水印地图也能显示", onOpenOfflineMaps)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            NavRow(Icons.Outlined.VerifiedUser, "校验照片", "检查任意照片的防篡改证明，包括别人拍的", onOpenVerify)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            NavRow(Icons.Outlined.Info, "关于与法律信息", "隐私、条款、许可证、清除应用数据", onOpenAboutLegal)
            HorizontalDivider(Modifier.padding(start = 56.dp))
            NavRow(Icons.Outlined.BugReport, "反馈问题", "给原作者发邮件（英文），可附设备信息", onOpenReport)
        }
    }
    }
}

/** The current stamp settings drawn on a sample frame, exactly as the camera would burn them in. */
@Composable
private fun StampPreview(fields: StampFields, customTime: CustomTime, modifier: Modifier = Modifier) {
    val logo by produceState<android.graphics.Bitmap?>(null, fields.orgLogoUri) {
        value = fields.orgLogoUri?.let { path -> withContext(Dispatchers.IO) { LogoDecoder.decode(path) } }
    }
    val spec = remember(fields, logo, customTime) {
        buildStampSpec(
            fix = SAMPLE_FIX,
            addressParts = SAMPLE_ADDRESS,
            capturedAtEpochMs = customTime.toStampTime(System.currentTimeMillis()),
            fields = fields,
            mapTile = SampleMap.tile,
            orgLogo = logo,
            hasSignature = fields.requireSignature,
            weatherChipText = "31°C 晴"
        )
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .heightIn(max = 220.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF8FB3C9), Color(0xFFC9D6CF), Color(0xFF6F7F62), Color(0xFF4A5544))
                    )
                )
        ) {
            StampOverlay(spec, Modifier.fillMaxSize())
        }
        Text(
            "效果预览（示例位置）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
        )
    }
}

@Composable
internal fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        content()
    }
}

@Composable
private fun ToggleRow(toggle: StampToggle, fields: StampFields, viewModel: SettingsViewModel) {
    SwitchRow(
        title = toggle.label,
        subtitle = toggle.description,
        checked = toggle.isChecked(fields),
        onChange = { checked -> viewModel.update { toggle.apply(it, checked) } }
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // The whole row toggles, not just the small switch.
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}



@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}
