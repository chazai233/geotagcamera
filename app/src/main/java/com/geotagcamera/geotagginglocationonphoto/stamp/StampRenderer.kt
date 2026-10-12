package com.geotagcamera.geotagginglocationonphoto.stamp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix

/**
 * Burns a [StampSpec] into a captured photo. Building the spec
 * ([buildStampSpec]) is pure data resolution; the actual drawing is
 * [StampPainter.draw], the exact same function Phase 5's live viewfinder
 * overlay calls from inside a Compose `Canvas` — this function's only job
 * is to drive that same draw call outside of any Composition, against a
 * `CanvasDrawScope` wrapping the target bitmap's own `android.graphics.Canvas`.
 *
 * Density note, easy to get subtly wrong: [StampPainter] uses `.dp`/`.sp`
 * sizing for small fixed details (padding, stroke width, font size), which
 * are density-relative by design in Compose. If this burn-in used the real
 * device's screen density, a "12sp" label would come out a completely
 * different *proportion* of a high-resolution sensor photo than it did on
 * the (much lower-resolution) live preview, breaking the whole "what you
 * frame is what burns in" guarantee at exactly the most visible level: text
 * size. The fix is a synthetic density scaled to the image's own pixel
 * width against a fixed reference (360dp, a common phone width baseline),
 * so "the image is 360dp wide" is true regardless of whether the image is
 * actually 1080px (a live preview) or 4032px (a full-resolution capture).
 * Phase 5's live-overlay `Canvas` composable must use this same synthetic
 * density, not `LocalDensity.current`, or the two will drift apart again.
 */
object StampRenderer {
    /**
     * The burn-in and every on-screen preview treat the frame's SHORTER side as
     * this many dp, so a stamp covers the same share of a portrait or a
     * landscape photo instead of ballooning when the frame is wide.
     */
    const val REFERENCE_SHORT_SIDE_DP = 360f

    fun stamp(
        context: Context,
        source: Bitmap,
        fix: LocationFix,
        addressParts: AddressParts?,
        capturedAtEpochMs: Long,
        fields: StampFields,
        mapTile: Bitmap? = null,
        orgLogo: Bitmap? = null,
        hasSignature: Boolean = false,
        weatherChipText: String? = null
    ): Bitmap {
        val spec = buildStampSpec(fix, addressParts, capturedAtEpochMs, fields, mapTile, orgLogo, hasSignature, weatherChipText)

        // Draw in place when we can: a full-resolution copy is tens of MB, and
        // the caller already owns [source]. Returns [source] itself in that case.
        val output = if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) source
        else source.copy(Bitmap.Config.ARGB_8888, true)
        val density = Density(density = minOf(output.width, output.height) / REFERENCE_SHORT_SIDE_DP, fontScale = 1f)
        val fontFamilyResolver = createFontFamilyResolver(context)
        val textMeasurer = TextMeasurer(
            defaultFontFamilyResolver = fontFamilyResolver,
            defaultDensity = density,
            defaultLayoutDirection = LayoutDirection.Ltr
        )

        val canvasDrawScope = CanvasDrawScope()
        canvasDrawScope.draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(android.graphics.Canvas(output)),
            size = Size(output.width.toFloat(), output.height.toFloat())
        ) {
            StampPainter.draw(this, spec, textMeasurer)
        }

        return output
    }
}
