package com.geotagcamera.geotagginglocationonphoto.stamp

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.geotagcamera.geotagginglocationonphoto.ui.theme.MonoDataStyle
import com.geotagcamera.geotagginglocationonphoto.ui.theme.Poppins
import com.geotagcamera.geotagginglocationonphoto.ui.theme.RobotoMono
import com.geotagcamera.geotagginglocationonphoto.ui.theme.StampAnchorStyle

/**
 * One draw path for all three stamp templates, shared by both the live
 * viewfinder overlay (a `Canvas` composable, inside composition, Phase 5)
 * and the final bitmap burn-in ([StampRenderer], a `CanvasDrawScope`
 * wrapping the target `android.graphics.Canvas`, outside composition). Both
 * call the same [draw] against the same [StampSpec] — that identity is what
 * makes "what you frame is what burns in" a guarantee, not a coincidence
 * between two separately-maintained implementations.
 *
 * Bar and Minimal share the same row-building idea: nothing is positioned
 * by a fixed index, every row's presence is read straight off [StampSpec],
 * so the layout closes the gap itself when a field is off — never a hole
 * where the address used to be.
 *
 * No RenderEffect/backdrop blur here (that needs API 31+): a flat
 * semi-transparent scrim is used everywhere, matching the design system's
 * own documented low-end/API-26 fallback path (section 10, "API 26
 * fallbacks" — "a slightly darker flat scrim below it, tuned to the same
 * measured contrast"). Every line is measured against the space it has and
 * wraps or ellipsizes, so long place names and addresses never run off the
 * stamp or the photo edge.
 */
object StampPainter {
    private val CardScrim = Color(0xB2141619) // ~rgba(20,22,25,.70), one shade lighter than chrome/base for legibility
    private val CardBorder = Color(0x24FFFFFF) // rgba(255,255,255,.14)
    private val TextPrimary = Color.White
    private val TextSecondary = Color(0xCCFFFFFF) // rgba(255,255,255,.80)
    private val TextMuted = Color(0x8CFFFFFF) // rgba(255,255,255,.55)
    private val ChipBackground = Color(0x1CFFFFFF) // rgba(255,255,255,.11)
    private val BrandDotColor = Color(0xFF56CB98) // accent/verified

    private val AddressStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, color = TextSecondary)
    private val DateTimeStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, color = TextSecondary)
    private val ChipStyle = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = TextPrimary)
    private val MutedChipStyle = ChipStyle.copy(color = TextMuted)
    private val FooterLabelStyle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextPrimary)
    private val CountryChipStyle = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Color(0xFF0A0C0E))
    private val BrandTextStyle = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 8.5.sp, color = TextPrimary.copy(alpha = 0.55f), letterSpacing = 1.sp)

    fun draw(scope: DrawScope, spec: StampSpec, textMeasurer: TextMeasurer) {
        when (spec.template) {
            StampTemplate.BAR -> drawBar(scope, spec, textMeasurer)
            StampTemplate.MINIMAL -> drawMinimal(scope, spec, textMeasurer)
        }
    }

    // ---- Bar (default): every enabled field in a full-width strip on the top or bottom edge ----

    private fun drawBar(scope: DrawScope, spec: StampSpec, tm: TextMeasurer) = with(scope) {
        drawPanel(spec, tm, size.width, cornered = false, tileFraction = 0.2f) { panel ->
            Offset(0f, if (spec.anchor in TOP_ANCHORS) 0f else size.height - panel.height)
        }
    }

    /**
     * The shared stamp panel: optional map tile on the left, every enabled
     * text field stacked on the right, a chip row, then a footer row (logo,
     * org name, SIGNED, brand mark). Each part is drawn only if present, and
     * the panel height is computed from what is actually there, so turning
     * fields on or off reflows it instead of leaving gaps.
     */
    private fun DrawScope.drawPanel(
        spec: StampSpec,
        tm: TextMeasurer,
        cardWidth: Float,
        cornered: Boolean,
        tileFraction: Float,
        place: DrawScope.(Size) -> Offset
    ) {
        val px3 = px(3f); val px1 = px(1f)
        val pad = cardWidth * (if (cornered) 0.045f else 0.035f)
        val gap = pad * 0.67f

        val tileSize = if (spec.mapTile != null) cardWidth * tileFraction else 0f
        val hasTile = spec.mapTile != null

        // Text column width: whatever's left of the card after padding and the
        // (optional) map tile. Every line is constrained to this and wraps or
        // ellipsizes, so a long address can never run past the card or off the
        // photo edge (the device-tested failure this fixes).
        val textColWidth = (cardWidth - pad * 2 - (if (hasTile) tileSize + gap else 0f)).coerceAtLeast(1f)
        val measure: (String, TextStyle, Int, Float) -> TextLayoutResult = { text, style, maxLines, maxW ->
            tm.measure(
                text = text,
                style = style,
                overflow = TextOverflow.Ellipsis,
                maxLines = maxLines,
                constraints = Constraints(maxWidth = maxW.roundToInt().coerceAtLeast(1))
            )
        }

        val countryLayout = spec.countryCode?.let { tm.measure(it, CountryChipStyle, maxLines = 1) }
        val countryReserve = countryLayout?.let { it.size.width + px(11f) } ?: 0f
        // A footer holding nothing but the app mark would waste a whole row, so
        // the mark moves up to the top-right corner, beside the place name.
        val brandInline = spec.showBrandMark && spec.orgLogo == null && spec.orgLabel == null && !spec.showSignedMark
        val hasFooter = spec.hasFooterRow && !brandInline
        val brandLabel = if (spec.showBrandMark) tm.measure("GEOTAG", BrandTextStyle, maxLines = 1) else null
        val brandWidth = brandLabel?.let { px(11f) + px(4f) + it.size.width } ?: 0f
        val brandReserve = if (brandInline) brandWidth + gap else 0f
        val placeLayout = spec.placeName?.let {
            measure(it, StampAnchorStyle.copy(color = TextPrimary), 1, (textColWidth - countryReserve - brandReserve).coerceAtLeast(1f))
        }
        val addressLayout = spec.addressLine?.let { measure(it, AddressStyle, 2, textColWidth) }
        // Coordinates may wrap (Lat on one line, Long on the next) but are never cut off.
        val coordsLayout = spec.coordinatesText?.let { measure(it, MonoDataStyle.copy(color = TextPrimary), 2, textColWidth) }
        val dateTimeText = listOfNotNull(spec.dateTimeText, spec.gmtOffsetText).joinToString(" ")
        val dateTimeLayout = dateTimeText.takeIf { it.isNotBlank() }?.let { measure(it, DateTimeStyle, 2, textColWidth) }

        // Chips (accuracy, altitude, bearing, weather) flow left to right in the
        // text column and wrap onto a new row instead of running off the panel.
        val chipPad = px(5f)
        val chipSpacing = px(5f)
        val chipLayouts = spec.chips.map { tm.measure(it.text, ChipStyle, maxLines = 1) }
        val chipHeight = chipLayouts.firstOrNull()?.let { it.size.height + chipPad * 1.2f } ?: 0f
        val chipPositions = ArrayList<Offset>(chipLayouts.size) // relative to the chip block's top-left
        run {
            var cx = 0f
            var cy = 0f
            chipLayouts.forEach { layout ->
                val w = layout.size.width + chipPad * 2
                if (cx > 0f && cx + w > textColWidth) { cx = 0f; cy += chipHeight + chipSpacing }
                chipPositions += Offset(cx, cy)
                cx += w + chipSpacing
            }
        }
        val chipBlockHeight = chipPositions.lastOrNull()?.let { it.y + chipHeight } ?: 0f

        val textBlockHeight = stackedHeight(listOfNotNull(placeLayout, addressLayout, coordsLayout, dateTimeLayout), px3) +
            (if (chipBlockHeight > 0f) px(6f) + chipBlockHeight else 0f)
        val rowHeight = maxOf(tileSize, textBlockHeight)

        val footerHeight = if (hasFooter) with(FooterLabelStyle.fontSize) { toPx() } * 2.0f else 0f

        val cardHeight = pad * 2 +
            rowHeight +
            (if (hasFooter) gap + footerHeight else 0f)

        val cardOrigin = place(Size(cardWidth, cardHeight))
        val corner = CornerRadius(if (cornered) cardWidth * 0.045f else 0f)

        drawRoundRect(color = CardScrim, topLeft = cardOrigin, size = Size(cardWidth, cardHeight), cornerRadius = corner)
        if (cornered) {
            drawRoundRect(color = CardBorder, topLeft = cardOrigin, size = Size(cardWidth, cardHeight), cornerRadius = corner, style = Stroke(width = px1))
        }

        var x = cardOrigin.x + pad
        val rowTop = cardOrigin.y + pad

        val tile = spec.mapTile
        if (tile != null) {
            val tileCorner = CornerRadius(tileSize * 0.12f)
            clipRoundRect(Offset(x, rowTop), Size(tileSize, tileSize), tileCorner) {
                drawImage(tile, dstOffset = IntOffset(x.roundToInt(), rowTop.roundToInt()), dstSize = IntSize(tileSize.roundToInt().coerceAtLeast(1), tileSize.roundToInt().coerceAtLeast(1)))
            }
            x += tileSize + gap
        }

        var textY = rowTop
        placeLayout?.let { layout ->
            drawText(layout, topLeft = Offset(x, textY))
            countryLayout?.let { cl ->
                val chipPad = px(4f)
                val chipX = x + layout.size.width + px(7f)
                val chipSize = Size(cl.size.width + chipPad * 2, cl.size.height + chipPad * 1.2f)
                drawRoundRect(Color(0xFFE9EBEC), Offset(chipX, textY + px1), chipSize, CornerRadius(px(3f)))
                drawText(cl, topLeft = Offset(chipX + chipPad, textY + px1 + chipPad * 0.6f))
            }
            textY += layout.size.height + px3
        }
        addressLayout?.let { layout -> drawText(layout, topLeft = Offset(x, textY)); textY += layout.size.height + px3 }
        coordsLayout?.let { layout -> drawText(layout, topLeft = Offset(x, textY)); textY += layout.size.height + px3 }
        dateTimeLayout?.let { layout -> drawText(layout, topLeft = Offset(x, textY)); textY += layout.size.height + px3 }

        if (chipLayouts.isNotEmpty()) {
            val blockTop = textY + px(6f) - px3
            chipLayouts.forEachIndexed { i, layout ->
                val at = chipPositions[i]
                val chipX = x + at.x
                val chipY = blockTop + at.y
                drawRoundRect(ChipBackground, Offset(chipX, chipY), Size(layout.size.width + chipPad * 2, chipHeight), CornerRadius(px(4f)))
                drawText(layout, topLeft = Offset(chipX + chipPad, chipY + chipPad * 0.6f))
            }
        }

        var y = rowTop + rowHeight

        if (brandInline && brandLabel != null) {
            val centerY = rowTop + (placeLayout?.size?.height ?: brandLabel.size.height) / 2f
            drawBrandMark(brandLabel, cardOrigin.x + cardWidth - pad - brandWidth, centerY)
        }

        if (hasFooter) {
            y += gap
            drawLine(CardBorder, Offset(cardOrigin.x + pad, y), Offset(cardOrigin.x + cardWidth - pad, y), strokeWidth = px1)
            val centerY = y + footerHeight * 0.5f + gap * 0.2f

            // Left: logo, vertically centred.
            var leftX = cardOrigin.x + pad
            spec.orgLogo?.let { logo ->
                // Keep the logo's own aspect ratio (wide wordmarks are common),
                // capped at a third of the panel so the org name still fits.
                val logoHeight = footerHeight * 0.72f
                val aspect = if (logo.height > 0) logo.width.toFloat() / logo.height else 1f
                val logoWidth = (logoHeight * aspect).coerceAtMost(cardWidth / 3f)
                val drawHeight = if (aspect > 0f) logoWidth / aspect else logoHeight
                val top = centerY - drawHeight / 2f
                val w = logoWidth.roundToInt().coerceAtLeast(1)
                val h = drawHeight.roundToInt().coerceAtLeast(1)
                clipRoundRect(Offset(leftX, top), Size(logoWidth, drawHeight), CornerRadius(drawHeight * 0.18f)) {
                    drawImage(logo, dstOffset = IntOffset(leftX.roundToInt(), top.roundToInt()), dstSize = IntSize(w, h))
                }
                leftX += logoWidth + gap * 0.6f
            }

            // Right: brand mark (rightmost), then SIGNED to its left — laid out
            // right-to-left so nothing collides, each vertically centred.
            var rightX = cardOrigin.x + cardWidth - pad
            if (spec.showBrandMark && brandLabel != null) {
                val startX = rightX - brandWidth
                drawBrandMark(brandLabel, startX, centerY)
                rightX = startX - gap
            }
            if (spec.showSignedMark) {
                val label = tm.measure("SIGNED", MutedChipStyle, maxLines = 1)
                val startX = rightX - label.size.width
                drawLine(CardBorder, Offset(startX - gap * 0.6f, centerY - footerHeight * 0.28f), Offset(startX - gap * 0.6f, centerY + footerHeight * 0.28f), strokeWidth = px1)
                drawText(label, topLeft = Offset(startX, centerY - label.size.height / 2f))
                rightX = startX - gap
            }

            // Org label fills the gap between the logo and the right-hand block.
            spec.orgLabel?.let { label ->
                val avail = (rightX - leftX).coerceAtLeast(1f)
                val layout = tm.measure(label, FooterLabelStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = avail.roundToInt().coerceAtLeast(1)))
                drawText(layout, topLeft = Offset(leftX, centerY - layout.size.height / 2f))
            }
        }
    }

    // ---- Minimal: a small blurred-look pill, coordinates and date/time only ----

    private fun drawMinimal(scope: DrawScope, spec: StampSpec, tm: TextMeasurer) = with(scope) {
        val margin = size.minDimension * 0.035f
        val pad = px(10f)
        val px2 = px(2f)
        val minimalMax = size.width * 0.7f
        val coordsLayout = spec.coordinatesText?.let { tm.measure(it, MonoDataStyle.copy(color = TextPrimary), overflow = TextOverflow.Ellipsis, maxLines = 2, constraints = Constraints(maxWidth = minimalMax.roundToInt().coerceAtLeast(1))) }
        val dateTimeText = listOfNotNull(spec.dateTimeText, spec.gmtOffsetText).joinToString(" ")
        val dateTimeLayout = dateTimeText.takeIf { it.isNotBlank() }?.let { tm.measure(it, DateTimeStyle, overflow = TextOverflow.Ellipsis, maxLines = 2, constraints = Constraints(maxWidth = minimalMax.roundToInt().coerceAtLeast(1))) }
        if (coordsLayout == null && dateTimeLayout == null) return@with

        val width = maxOf(coordsLayout?.size?.width ?: 0, dateTimeLayout?.size?.width ?: 0) + pad * 2
        val height = stackedHeight(listOfNotNull(coordsLayout, dateTimeLayout), px2) + pad * 1.4f

        val origin = anchorOrigin(spec.anchor, Size(width, height), margin)
        drawRoundRect(CardScrim, origin, Size(width, height), CornerRadius(px(9f)))

        var y = origin.y + pad * 0.7f
        coordsLayout?.let { drawText(it, topLeft = Offset(origin.x + pad, y)); y += it.size.height + px2 }
        dateTimeLayout?.let { drawText(it, topLeft = Offset(origin.x + pad, y)) }
    }

    // ---- Shared helpers ----

    /** The small rounded-square mark plus "GEOTAG", starting at [startX], vertically centred on [centerY]. */
    private fun DrawScope.drawBrandMark(label: TextLayoutResult, startX: Float, centerY: Float) {
        val markSize = px(11f)
        drawRoundRect(
            TextPrimary.copy(alpha = 0.55f), Offset(startX, centerY - markSize / 2f), Size(markSize, markSize),
            CornerRadius(markSize * 0.33f), style = Stroke(width = px(1.2f))
        )
        drawCircle(BrandDotColor.copy(alpha = 0.85f), markSize * 0.15f, Offset(startX + markSize / 2f, centerY))
        drawText(label, topLeft = Offset(startX + markSize + px(4f), centerY - label.size.height / 2f))
    }

    private val TOP_ANCHORS = setOf(StampAnchor.TOP_LEFT, StampAnchor.TOP_CENTER, StampAnchor.TOP_RIGHT)

    /** Nine-anchor placement: same grid the viewfinder drag and the Settings position picker use. */
    private fun DrawScope.anchorOrigin(anchor: StampAnchor, contentSize: Size, margin: Float): Offset {
        val x = when (anchor) {
            StampAnchor.TOP_LEFT, StampAnchor.MID_LEFT, StampAnchor.BOTTOM_LEFT -> margin
            StampAnchor.TOP_CENTER, StampAnchor.MID_CENTER, StampAnchor.BOTTOM_CENTER -> (size.width - contentSize.width) / 2f
            StampAnchor.TOP_RIGHT, StampAnchor.MID_RIGHT, StampAnchor.BOTTOM_RIGHT -> size.width - contentSize.width - margin
        }
        val y = when (anchor) {
            StampAnchor.TOP_LEFT, StampAnchor.TOP_CENTER, StampAnchor.TOP_RIGHT -> margin
            StampAnchor.MID_LEFT, StampAnchor.MID_CENTER, StampAnchor.MID_RIGHT -> (size.height - contentSize.height) / 2f
            StampAnchor.BOTTOM_LEFT, StampAnchor.BOTTOM_CENTER, StampAnchor.BOTTOM_RIGHT -> size.height - contentSize.height - margin
        }
        return Offset(x, y)
    }

    private fun DrawScope.clipRoundRect(topLeft: Offset, size: Size, corner: CornerRadius, block: DrawScope.() -> Unit) {
        val path = Path().apply {
            addRoundRect(RoundRect(Rect(topLeft, size), corner))
        }
        clipPath(path) { block() }
    }

    /** [value] is in dp; converts to px using this DrawScope's own density. */
    private fun DrawScope.px(value: Float): Float = value.dp.toPx()

    /** Sum of each layout's height plus one [gap] between consecutive items, never after the last. */
    private fun stackedHeight(layouts: List<TextLayoutResult>, gap: Float): Float =
        if (layouts.isEmpty()) 0f else layouts.sumOf { it.size.height } + gap * (layouts.size - 1)
}
