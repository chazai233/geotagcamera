package com.geotagcamera.geotagginglocationonphoto.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.exif.SignedPayload
import com.geotagcamera.geotagginglocationonphoto.security.KeyFingerprint
import com.geotagcamera.geotagginglocationonphoto.security.VerificationOutcome
import com.geotagcamera.geotagginglocationonphoto.ui.theme.RobotoMono
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ProofColors


/**
 * The verification result, worded so it never overclaims (design section 05):
 * a valid signature proves the image pixels, stamp included, are unchanged
 * since a given key signed them, and (v2 proofs) that the EXIF latitude,
 * longitude and time still match what was signed. It says which key, and
 * whether that key is this device's own. It does not prove who held the phone.
 */
@Composable
fun ProofCard(outcome: VerificationOutcome, deviceKeyBase64: String?, modifier: Modifier = Modifier) {
    val content = describe(outcome, deviceKeyBase64)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(content.accent.copy(alpha = 0.10f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(content.icon, contentDescription = null, tint = content.accent, modifier = Modifier.size(24.dp))
            Text(
                content.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = content.accent
            )
        }
        Text(content.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        content.fingerprint?.let {
            Text(
                "密钥 $it",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = RobotoMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class ProofContent(
    val icon: ImageVector,
    val accent: Color,
    val title: String,
    val body: String,
    val fingerprint: String?
)

@Composable
private fun describe(outcome: VerificationOutcome, deviceKey: String?): ProofContent {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (outcome) {
        is VerificationOutcome.Untampered -> {
            val p = outcome.payload
            val origin = if (deviceKey != null && p.publicKeyBase64.trim() == deviceKey.trim()) {
                "由本机签名"
            } else {
                "由其他设备的密钥签名"
            }
            val realTime = p.claims?.realEpochMs
            ProofContent(
                Icons.Filled.Verified, ProofColors.verified, "签名后未被修改",
                "$origin，签名时间 ${realTime?.let { formatEpoch(it) } ?: formatSigned(p)}。" + if (p.claims != null) {
                    "照片（含水印）以及其中的 GPS 位置和时间与签名完全一致。"
                } else {
                    "照片（含水印）与签名完全一致。这是旧版证明，不覆盖文件元数据中的 GPS 位置和时间。"
                } + if (realTime != null) {
                    "\n注意：照片上的拍摄时间是手动设定的（${formatEpoch(p.claims?.epochMs ?: realTime)}），实际按快门的时间是 ${formatEpoch(realTime)}。"
                } else "",
                KeyFingerprint.of(p.publicKeyBase64)
            )
        }
        is VerificationOutcome.Edited -> ProofContent(
            Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, "签名后被修改过",
            "这张照片带有防篡改证明，但画面或其中的 GPS 位置、时间已与证明不符。" +
                "签名之后被编辑、裁剪、重新压缩或改动过元数据。",
            KeyFingerprint.of(outcome.payload.publicKeyBase64)
        )
        is VerificationOutcome.MetadataUnavailable -> ProofContent(
            Icons.Outlined.Info, muted, "画面完好，位置未能核对",
            "画面与证明一致，但这份文件里没有可读取的 GPS 位置或时间。" +
                "聊天软件转发时常会删掉这些信息，请用原始文件校验位置。",
            KeyFingerprint.of(outcome.payload.publicKeyBase64)
        )
        is VerificationOutcome.NoProof -> ProofContent(
            Icons.Outlined.Info, muted, "未找到证明",
            "这张照片没有防篡改证明。这不代表有问题：它可能不是本应用拍的，" +
                "或者被聊天软件删掉了元数据。",
            null
        )
        is VerificationOutcome.Unreadable -> ProofContent(
            Icons.Outlined.HelpOutline, muted, "无法读取此文件",
            "文件打不开，或者不是 JPEG 格式。",
            null
        )
    }
}

private fun formatSigned(payload: SignedPayload): String = runCatching {
    val time = OffsetDateTime.parse(payload.timestampIso).atZoneSameInstant(ZoneId.systemDefault())
    time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}.getOrElse { payload.timestampIso }

private fun formatEpoch(epochMs: Long): String =
    java.time.Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
