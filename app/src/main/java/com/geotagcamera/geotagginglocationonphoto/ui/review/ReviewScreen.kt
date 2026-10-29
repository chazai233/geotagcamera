package com.geotagcamera.geotagginglocationonphoto.ui.review

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geotagcamera.geotagginglocationonphoto.ui.capture.CaptureUiState
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ChromeTokens
import com.geotagcamera.geotagginglocationonphoto.ui.theme.ProofColors

private val ChromeBase = ChromeTokens.Base
private val Ink = ChromeTokens.Ink
private val Muted = ChromeTokens.Muted
private val Outline = ChromeTokens.OutlineSoft
private val AccentVerified = ProofColors.Verified

/**
 * Post-capture review takeover (design section 04). Share is the only filled
 * control — the entire point of the app is getting proof into WhatsApp or a
 * report in seconds. Retake discards the photo just saved; Done keeps it.
 * The SIGNED chip is always present because signing completes inside the
 * save pipeline, before this screen appears.
 *
 * Landscape (design section 10): photo and actions sit side by side.
 */
@Composable
fun ReviewScreen(
    review: CaptureUiState.Review,
    onRetake: () -> Unit,
    onDone: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChromeBase)
            // Swallow touches so nothing reaches the viewfinder underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
    ) {
        TopBar(onDone)

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp)) {
                PhotoPane(review.uri, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) { Actions(review, onRetake, onDone, onShare) }
            }
        } else {
            PhotoPane(review.uri, Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp))
            Actions(review, onRetake, onDone, onShare)
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回相机", tint = Ink)
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(AccentVerified.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = AccentVerified, modifier = Modifier.size(16.dp))
            Text("已签名 · SHA-256", color = AccentVerified, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PhotoPane(uri: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = uri,
            contentDescription = "刚拍的照片",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
        )
    }
}

@Composable
private fun ColumnScope.Actions(
    review: CaptureUiState.Review,
    onRetake: () -> Unit,
    onDone: () -> Unit,
    onShare: () -> Unit
) {
    Text(
        review.filename,
        color = Ink,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
    Text(
        buildString {
            if (review.width > 0) append("${review.width}×${review.height}（${"%.1f".format(review.width.toLong() * review.height / 1_000_000.0)} 百万像素）· ")
            append("${ReviewFilename.formatSize(review.sizeBytes)} · 已保存到 Pictures/GeoTagCamera")
            if (review.originalUri != null) append("\n原图已另存到 GeoTagCamera/原图")
            if (review.downscaled) append("\n内存不足，成片已按一半分辨率处理")
        },
        color = Muted,
        fontSize = 11.5.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onRetake,
            modifier = Modifier.weight(1f).height(52.dp),
            border = BorderStroke(1.5.dp, Outline)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("重拍", color = Ink)
        }
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.weight(1f).height(52.dp),
            border = BorderStroke(1.5.dp, Outline)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("完成", color = Ink)
        }
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = onShare,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = ChromeTokens.OnInk)
    ) {
        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("分享", fontWeight = FontWeight.Medium, fontSize = 15.5.sp)
    }
    Spacer(Modifier.height(8.dp))
}
