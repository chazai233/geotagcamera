package com.geotagcamera.geotagginglocationonphoto.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor

/** Row-major 3×3 of the nine anchors: [row][col], row = top/mid/bottom, col = left/center/right. */
private val ANCHOR_GRID = arrayOf(
    arrayOf(StampAnchor.TOP_LEFT, StampAnchor.TOP_CENTER, StampAnchor.TOP_RIGHT),
    arrayOf(StampAnchor.MID_LEFT, StampAnchor.MID_CENTER, StampAnchor.MID_RIGHT),
    arrayOf(StampAnchor.BOTTOM_LEFT, StampAnchor.BOTTOM_CENTER, StampAnchor.BOTTOM_RIGHT)
)

/**
 * Maps a point given as fractions of the frame (0..1 each axis) to the anchor
 * whose third of the frame it falls in. Shared by the viewfinder drag-snap and
 * the Settings position picker so both agree on where the nine cells are.
 */
fun anchorForFraction(xFraction: Float, yFraction: Float): StampAnchor {
    val col = when {
        xFraction < 1f / 3f -> 0
        xFraction < 2f / 3f -> 1
        else -> 2
    }
    val row = when {
        yFraction < 1f / 3f -> 0
        yFraction < 2f / 3f -> 1
        else -> 2
    }
    return ANCHOR_GRID[row][col]
}
