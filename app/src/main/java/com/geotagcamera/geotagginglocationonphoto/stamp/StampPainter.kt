package com.geotagcamera.geotagginglocationonphoto.stamp

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.geotagcamera.geotagginglocationonphoto.ui.theme.StampColors
import com.geotagcamera.geotagginglocationonphoto.ui.theme.StampType

/**
 * One draw path for both stamp templates, shared by the live viewfinder
 * overlay ([StampOverlay]) and the final bitmap burn-in ([StampRenderer]).
 * Both call [draw] with the same [StampSpec] and the same synthetic density
 * (the frame's shorter side is [StampRenderer.REFERENCE_SHORT_SIDE_DP] dp), so
 * what you frame is what burns in.
 *
 * Bar layout (1.3): a compact inset card on the top or bottom edge, built to
 * a height budget instead of growing a row per field.
 *
 *   [map]  [ Place name  IND                       | logo or     ]
 *   [tile] [ Plus code, full address on one line…  | signature   ]
 *   [    ] [ Lat 22.834455°  Long 87.970336°       | org name    ]
 *   [    ] [ Tue, 13/10/2026 12:35 am GMT +05:30   |             ]
 *   [    ] [ Pole 12  ·  Contract A-123            |             ]
 *   [    ] [ ±5 m · ALT 34 m · 312°      SIGNED · GEOTAG        ]
 *
 * Every text line is single-line and ellipsized, so a long address costs
 * width, never height. Absent fields drop their line and the card shrinks;
 * the map tile is a square matching the card height. With every field on it
 * stays near 17% of a portrait 4:3 frame.
 */
object StampPainter {

    fun draw(scope: DrawScope, spec: StampSpec, textMeasurer: TextMeasurer) {
        when (spec.template) {
            StampTemplate.BAR -> drawBar(scope, spec, textMeasurer)
            StampTemplate.MINIMAL -> drawMinimal(scope, spec, textMeasurer)
        }
    }

    // ---- Bar (default) ----

    private fun drawBar(scope: DrawScope, spec: StampSpec, tm: TextMeasurer) = with(scope) {
        val margin = px(8f)
        val gap = px(4f)
        val padH = px(8f)
        val padV = px(6f)
        val lineGap = px(1.5f)

        // Wide (landscape) frames would stretch the card into a thin ribbon;
        // cap it and let the anchor's column decide where it sits.
        val outerWidth = (size.width - margin * 2).coerceAtMost(px(MAX_BAR_WIDTH_DP))

        // Right column: logo and/or signature with the org name under them.
        val rightImages = listOfNotNull(spec.orgLogo, spec.signature)
        val hasRightColumn = rightImages.isNotEmpty() || spec.orgLabel != null

        // First pass assumes a tile, then the tile is sized from the text height.
        // Text height doesn't depend on width (every line is one line), so one
        // pass with an estimated tile width is exact.
        val textHeightProbe = barTextHeight(spec, tm, lineGap)
        val cardHeight = (textHeightProbe + padV * 2).coerceAtLeast(px(28f))
        val tileSize = if (spec.mapTile != null) cardHeight else 0f
        val cardWidth = outerWidth - (if (tileSize > 0f) tileSize + gap else 0f)
        val rightColWidth = if (hasRightColumn) cardWidth * 0.24f else 0f
        val textWidth = (cardWidth - padH * 2 - (if (hasRightColumn) rightColWidth + padH else 0f)).coerceAtLeast(1f)

        val totalWidth = (if (tileSize > 0f) tileSize + gap else 0f) + cardWidth
        val x0 = when (spec.anchor.column) {
            0 -> margin
            2 -> size.width - margin - totalWidth
            else -> (size.width - totalWidth) / 2f
        }
        val y0 = if (spec.anchor.row == 0) margin else size.height - margin - cardHeight
        val corner = CornerRadius(px(8f))

        // Map tile: its own rounded square, left of the card.
        spec.mapTile?.let { tile ->
            drawImageClipped(tile, Offset(x0, y0), Size(tileSize, tileSize), corner, crop = true)
            drawRoundRect(StampColors.Border, Offset(x0, y0), Size(tileSize, tileSize), corner, style = Stroke(px(1f)))
        }

        val cardX = x0 + (if (tileSize > 0f) tileSize + gap else 0f)
        drawRoundRect(StampColors.Scrim, Offset(cardX, y0), Size(cardWidth, cardHeight), corner)
        drawRoundRect(StampColors.Border, Offset(cardX, y0), Size(cardWidth, cardHeight), corner, style = Stroke(px(1f)))

        val textX = cardX + padH
        var y = y0 + padV

        // Line 1: place name + ISO-3 country chip.
        spec.placeName?.let { place ->
            val country = spec.countryCode?.let { tm.measure(it, StampType.Country, maxLines = 1) }
            val chipPadH = px(3f)
            val chipReserve = country?.let { it.size.width + chipPadH * 2 + px(5f) } ?: 0f
            val title = one(tm, place, StampType.Title, textWidth - chipReserve)
            drawText(title, topLeft = Offset(textX, y))
            country?.let { c ->
                val chipX = textX + title.size.width + px(5f)
                val chipH = c.size.height.toFloat()
                val chipY = y + (title.size.height - chipH) / 2f
                drawRoundRect(StampColors.CountryChip, Offset(chipX, chipY), Size(c.size.width + chipPadH * 2, chipH), CornerRadius(px(2.5f)))
                drawText(c, topLeft = Offset(chipX + chipPadH, chipY))
            }
            y += title.size.height + lineGap
        }
        spec.addressLine?.let { y += drawLine(tm, it, StampType.Body, textX, y, textWidth) + lineGap }
        spec.coordinatesText?.let { y += drawLine(tm, it, StampType.Data, textX, y, textWidth) + lineGap }
        dateTimeLine(spec)?.let { y += drawLine(tm, it, StampType.Body, textX, y, textWidth) + lineGap }
        spec.noteLine?.let { y += drawLine(tm, it, StampType.Note, textX, y, textWidth) + lineGap }

        // Last line: meta chips as one dotted run on the left, marks on the right.
        val marks = marksText(spec)
        val meta = spec.chips.joinToString("  ·  ") { it.text }.takeIf { it.isNotBlank() }
        if (meta != null || marks != null) {
            val fullTextWidth = cardWidth - padH * 2
            val marksLayout = marks?.let { tm.measure(it, StampType.Mark, maxLines = 1) }
            val markReserve = marksLayout?.let { it.size.width + px(8f) } ?: 0f
            val metaWidth = (if (hasRightColumn) textWidth else fullTextWidth) - markReserve
            val metaLayout = meta?.let { one(tm, it, StampType.Meta, metaWidth) }
            metaLayout?.let { drawText(it, topLeft = Offset(textX, y)) }
            marksLayout?.let {
                val lineH = maxOf(metaLayout?.size?.height ?: 0, it.size.height)
                drawText(it, topLeft = Offset(cardX + cardWidth - padH - it.size.width, y + (lineH - it.size.height) / 2f))
            }
        }

        if (hasRightColumn) {
            val colX = cardX + cardWidth - padH - rightColWidth
            val colTop = y0 + padV
            // Keep the bottom line free for the marks when there are any.
            val reservedBottom = if (marks != null) tm.measure(marks, StampType.Mark, maxLines = 1).size.height + lineGap * 2 else 0f
            val colHeight = cardHeight - padV * 2 - reservedBottom
            val label = spec.orgLabel?.let { one(tm, it, StampType.Caption, rightColWidth) }
            val labelHeight = label?.let { it.size.height + lineGap } ?: 0f
            val imagesHeight = (colHeight - labelHeight).coerceAtLeast(0f)
            if (rightImages.isNotEmpty() && imagesHeight > 0f) {
                val slotWidth = (rightColWidth - gap * (rightImages.size - 1)) / rightImages.size
                rightImages.forEachIndexed { i, img ->
                    val fit = fitInside(img, slotWidth, imagesHeight)
                    val slotX = colX + i * (slotWidth + gap) + (slotWidth - fit.width) / 2f
                    drawImageClipped(img, Offset(slotX, colTop + (imagesHeight - fit.height) / 2f), fit, CornerRadius(px(3f)), crop = false)
                }
            }
            label?.let {
                val labelY = if (rightImages.isEmpty()) colTop + (colHeight - it.size.height) / 2f else colTop + imagesHeight + lineGap
                drawText(it, topLeft = Offset(colX + (rightColWidth - it.size.width) / 2f, labelY))
            }
        }
    }

    /** Height of the Bar's text column; every line is single-line so width doesn't matter. */
    private fun barTextHeight(spec: StampSpec, tm: TextMeasurer, lineGap: Float): Float {
        val heights = buildList {
            if (spec.placeName != null) add(tm.measure("Ag", StampType.Title, maxLines = 1).size.height)
            if (spec.addressLine != null) add(tm.measure("Ag", StampType.Body, maxLines = 1).size.height)
            if (spec.coordinatesText != null) add(tm.measure("Ag", StampType.Data, maxLines = 1).size.height)
            if (dateTimeLine(spec) != null) add(tm.measure("Ag", StampType.Body, maxLines = 1).size.height)
            if (spec.noteLine != null) add(tm.measure("Ag", StampType.Note, maxLines = 1).size.height)
            if (spec.chips.isNotEmpty() || marksText(spec) != null) {
                add(maxOf(tm.measure("Ag", StampType.Meta, maxLines = 1).size.height, tm.measure("AG", StampType.Mark, maxLines = 1).size.height))
            }
        }
        if (heights.isEmpty()) return 0f
        return heights.sum() + lineGap * (heights.size - 1)
    }

    private fun dateTimeLine(spec: StampSpec): String? =
        listOfNotNull(spec.dateTimeText, spec.gmtOffsetText).joinToString(" ").takeIf { it.isNotBlank() }

    private fun marksText(spec: StampSpec): String? =
        listOfNotNull(if (spec.showSignedMark) "SIGNED" else null, if (spec.showBrandMark) "GEOTAG" else null)
            .joinToString("  ·  ").takeIf { it.isNotBlank() }

    // ---- Minimal: a small pill, coordinates and date/time only ----

    private fun drawMinimal(scope: DrawScope, spec: StampSpec, tm: TextMeasurer) = with(scope) {
        val margin = px(8f)
        val padH = px(7f)
        val padV = px(5f)
        val lineGap = px(1.5f)
        val maxW = size.width * 0.7f
        val lines = listOfNotNull(
            spec.coordinatesText?.let { one(tm, it, StampType.Data, maxW) },
            dateTimeLine(spec)?.let { one(tm, it, StampType.Body, maxW) }
        )
        if (lines.isEmpty()) return@with

        val width = lines.maxOf { it.size.width } + padH * 2
        val height = lines.sumOf { it.size.height } + lineGap * (lines.size - 1) + padV * 2
        val x = when (spec.anchor.column) {
            0 -> margin
            2 -> size.width - margin - width
            else -> (size.width - width) / 2f
        }
        val y = when (spec.anchor.row) {
            0 -> margin
            1 -> (size.height - height) / 2f
            else -> size.height - margin - height
        }
        drawRoundRect(StampColors.Scrim, Offset(x, y), Size(width, height), CornerRadius(px(7f)))
        var ty = y + padV
        lines.forEach { drawText(it, topLeft = Offset(x + padH, ty)); ty += it.size.height + lineGap }
    }

    // ---- Shared helpers ----

    private fun one(tm: TextMeasurer, text: String, style: TextStyle, maxWidth: Float): TextLayoutResult =
        tm.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            softWrap = false,
            constraints = Constraints(maxWidth = maxWidth.roundToInt().coerceAtLeast(1))
        )

    /** Draws one ellipsized line and returns its height. */
    private fun DrawScope.drawLine(tm: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float, maxWidth: Float): Float {
        val layout = one(tm, text, style, maxWidth)
        drawText(layout, topLeft = Offset(x, y))
        return layout.size.height.toFloat()
    }

    private fun fitInside(img: ImageBitmap, maxW: Float, maxH: Float): Size {
        if (img.width <= 0 || img.height <= 0) return Size(0f, 0f)
        val scale = minOf(maxW / img.width, maxH / img.height)
        return Size(img.width * scale, img.height * scale)
    }

    /** Draws [img] into the rect; [crop] = fill and centre-crop, else stretch to the (already aspect-fit) rect. */
    private fun DrawScope.drawImageClipped(img: ImageBitmap, topLeft: Offset, dst: Size, corner: CornerRadius, crop: Boolean) {
        if (dst.width < 1f || dst.height < 1f) return
        var srcOffset = IntOffset.Zero
        var srcSize = IntSize(img.width, img.height)
        if (crop) {
            val dstAspect = dst.width / dst.height
            val imgAspect = img.width.toFloat() / img.height
            srcSize = if (imgAspect > dstAspect) IntSize((img.height * dstAspect).roundToInt(), img.height)
            else IntSize(img.width, (img.width / dstAspect).roundToInt())
            srcOffset = IntOffset((img.width - srcSize.width) / 2, (img.height - srcSize.height) / 2)
        }
        val path = Path().apply { addRoundRect(RoundRect(Rect(topLeft, dst), corner)) }
        clipPath(path) {
            drawImage(
                img, srcOffset = srcOffset, srcSize = srcSize,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(dst.width.roundToInt().coerceAtLeast(1), dst.height.roundToInt().coerceAtLeast(1))
            )
        }
    }

    /** [value] is in dp; converts to px using this DrawScope's own (synthetic) density. */
    private fun DrawScope.px(value: Float): Float = value.dp.toPx()

    /** Widest the Bar gets, in reference dp (the frame's short side is 360). */
    private const val MAX_BAR_WIDTH_DP = 560f
}
