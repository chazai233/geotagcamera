package com.geotagcamera.geotagginglocationonphoto.ui.legal

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
private const val WEBSITE_URL = "https://konkomaji.com/geotagcamera/"

/**
 * About & legal (design section 05, screen 10). Copy is written to match the
 * eventual Play data-safety declaration word for word so the two can't drift,
 * and to never overclaim: a signature proves the image is unedited and which
 * key signed it, never who was holding the phone.
 */
@Composable
fun AboutLegalScreen(
    onBack: () -> Unit,
    onOpenDoc: (LegalDoc) -> Unit = {},
    onReport: () -> Unit = {},
    viewModel: AboutLegalViewModel = viewModel()
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    AppScaffold(title = "关于与法律信息", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "工程水印相机 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Body("基于开源项目 GeoTag Camera（GPLv3）的中文修改版。免费开源，无广告、无账号、无跟踪。")

            Section("不收集数据") {
                Body("无账号、无统计、无广告 SDK、无崩溃上报。水印地图会下载你所在位置（以及你选择保存的区域）的地图瓦片；天气默认关闭。")
            }

            Section("签名能证明什么") {
                Body("每张照片都会计算哈希，并用保存在本机安全密钥库中的密钥签名。校验可以证明照片（含水印）自签名后未被改动，并显示是哪把密钥签的。签名同时覆盖拍摄的纬度、经度和时间；若时间是手动设定的，证明里还会记录真实拍摄时间。签名不能证明是谁拿着手机，也不覆盖其他元数据（海拔、方向、相机参数）。")
            }

            Section("位置与照片") {
                Body("只在相机界面打开时使用定位，用来标注拍摄地点，从不在后台定位。街道地址来自手机自带的地理编码服务（多数手机通过 Google Play 服务）。照片保存在你自己的相册 Pictures/GeoTagCamera 下。")
            }

            Section("可选的联网功能") {
                Body("水印地图：来自 OpenFreeMap 的 OpenStreetMap 矢量瓦片，在手机上绘制并缓存供离线使用；请求中包含你所在位置附近的瓦片编号，在设置中关闭地图即可停止。天气（默认关闭）：Open-Meteo，发送约 1 公里精度的位置。离线时两者都不会妨碍拍照。")
            }

            Section("许可证") {
                Body(
                    "GeoTag Camera 及本修改版：GNU GPL v3。\n" +
                        "Poppins and Roboto Mono fonts: SIL Open Font License 1.1.\n" +
                        "AndroidX, CameraX, Kotlin, Coil: Apache License 2.0.\n" +
                        "MapLibre Native: BSD 2-Clause.\n" +
                        "Map data © OpenStreetMap contributors (ODbL). Tiles: OpenFreeMap, © OpenMapTiles.\n" +
                        "Weather data by Open-Meteo.com (CC BY 4.0)."
                )
            }

            Section("文档（英文）") {
                LegalDoc.entries.take(4).forEach { doc -> DocRow(doc.title) { onOpenDoc(doc) } }
                DocRow("其他许可证") { onOpenDoc(LegalDoc.APACHE) }
            }

            Section("帮助") {
                DocRow("反馈问题") { onReport() }
                Body("会打开邮件应用，收件人为原作者 ${com.geotagcamera.geotagginglocationonphoto.support.ProblemReport.EMAIL}（英文）。你不点发送就不会发出任何内容。")
            }

            Section("链接") {
                LinkRow("原项目网站", WEBSITE_URL, context)
                LinkRow("原项目源代码", SOURCE_URL, context)
                LinkRow("隐私政策（在线）", "$SOURCE_URL/blob/main/PRIVACY.md", context)
            }

            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("清除全部应用数据", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(6.dp))
            Body("清除本应用的照片索引、缓存、Logo 和设置。已保存到相册的照片不受影响。")
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
        title = { Text("清除全部应用数据？") },
        text = {
            Column {
                Text("将清除拍摄索引、缓存、单位 Logo 和设置。相册中的照片不受影响。输入 DELETE 确认。")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text("输入 DELETE") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = armed) {
                Text("清除", color = if (armed) MaterialTheme.colorScheme.error else Color.Gray)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
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
            .clickable(onClickLabel = "打开 $label") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** A row that opens an in-app page (chevron, not the external-link icon). */
@Composable
private fun DocRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开 $label", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
