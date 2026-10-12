package com.geotagcamera.geotagginglocationonphoto.stamp

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Draws [spec] over whatever frame this composable fills, scaled exactly as
 * [StampRenderer] scales the burn-in: the frame is treated as
 * [StampRenderer.REFERENCE_SHORT_SIDE_DP] on its shorter side, whatever its real size.
 * Using the screen's own density here instead would make the preview stamp a
 * different share of the frame than the saved one, which is the one thing
 * the live overlay must never do.
 */
@Composable
fun StampOverlay(spec: StampSpec, modifier: Modifier = Modifier) {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val drawScope = remember { CanvasDrawScope() }
    // One measurer per frame size; its cache is only valid for one density.
    val measurers = remember(fontFamilyResolver) { HashMap<Int, TextMeasurer>(2) }
    Canvas(modifier = modifier) {
        val shortSide = size.minDimension
        if (shortSide <= 0f) return@Canvas
        val density = Density(density = shortSide / StampRenderer.REFERENCE_SHORT_SIDE_DP, fontScale = 1f)
        val measurer = measurers.getOrPut(shortSide.toInt()) {
            TextMeasurer(fontFamilyResolver, density, LayoutDirection.Ltr)
        }
        drawScope.draw(density, LayoutDirection.Ltr, drawContext.canvas, size) {
            StampPainter.draw(this, spec, measurer)
        }
    }
}
