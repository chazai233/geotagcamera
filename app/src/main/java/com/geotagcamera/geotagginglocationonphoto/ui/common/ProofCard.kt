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

private val VerifiedGreen = Color(0xFF2FA878)

/**
 * The verification result, worded so it never overclaims (design section 05):
 * a valid signature proves the image pixels, stamp included, are unchanged
 * since a given key signed them. It says which key, and whether that key is
 * this device's own. It does not prove who held the phone, and the EXIF
 * fields are not covered by the signature.
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
                "Key $it",
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
                "Signed on this device"
            } else {
                "Signed by another device's key"
            }
            ProofContent(
                Icons.Filled.Verified, VerifiedGreen, "Unaltered since signing",
                "$origin ${formatSigned(p)}. The image, including its stamp, matches the signature exactly. " +
                    "The location fields in the file's metadata are not covered by the signature.",
                KeyFingerprint.of(p.publicKeyBase64)
            )
        }
        is VerificationOutcome.Edited -> ProofContent(
            Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.error, "Edited after signing",
            "This photo carries a GeoTag proof, but its pixels no longer match it. " +
                "It was edited, cropped or re-compressed after it was signed.",
            KeyFingerprint.of(outcome.payload.publicKeyBase64)
        )
        is VerificationOutcome.NoProof -> ProofContent(
            Icons.Outlined.Info, muted, "No proof found",
            "This photo has no GeoTag proof. That isn't a failure: it just wasn't signed by this app, " +
                "or a messaging app stripped its metadata.",
            null
        )
        is VerificationOutcome.Unreadable -> ProofContent(
            Icons.Outlined.HelpOutline, muted, "Couldn't read this file",
            "The file couldn't be opened or isn't a JPEG.",
            null
        )
    }
}

private fun formatSigned(payload: SignedPayload): String = runCatching {
    val time = OffsetDateTime.parse(payload.timestampIso).atZoneSameInstant(ZoneId.systemDefault())
    "on " + time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}.getOrElse { "at ${payload.timestampIso}" }
