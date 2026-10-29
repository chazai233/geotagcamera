package com.geotagcamera.geotagginglocationonphoto.stamp

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
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
        val pal = Palette(spec.style)
        when (spec.template) {
            StampTemplate.BAR -> drawBar(scope, spec, textMeasurer, pal)
            StampTemplate.MINIMAL -> drawMinimal(scope, spec, textMeasurer, pal)
        }
    }

    /**
     * 画水印用的密度：画面短边按 [StampRenderer.REFERENCE_SHORT_SIDE_DP] dp 计，
     * 再乘以用户设定的大小倍数。取景器预览和成片都必须用它，两者才会一致。
     */
    fun density(shortSidePx: Float, spec: StampSpec): Float =
        shortSidePx / StampRenderer.REFERENCE_SHORT_SIDE_DP * spec.style.scale

    /** 由样式设置得出的颜色和字体。 */
    private class Palette(style: StampStyle) {
        val scale = style.scale
        val marginX = style.marginX
        val marginY = style.marginY
        val wrap = style.wrap
        val bg = Color(style.bgArgb)
        val border = StampColors.Border.copy(alpha = StampColors.Border.alpha * (bg.alpha / 0.7f).coerceIn(0f, 1f))
        private val text = Color(style.textArgb)
        // 背景很淡时给文字加阴影，白字落在亮处也看得清
        private val shadow = if (bg.alpha < 0.35f) {
            Shadow(color = Color.Black.copy(alpha = 0.75f), offset = Offset(0f, 0.6f), blurRadius = 2.5f)
        } else null
        private fun t(base: TextStyle, k: Float) = base.copy(color = text.copy(alpha = (text.alpha * k).coerceIn(0f, 1f)), shadow = shadow)
        val title = t(StampType.Title, 1f)
        val body = t(StampType.Body, 0.9f)
        val data = t(StampType.Data, 1f)
        val meta = t(StampType.Meta, 0.9f)
        val mark = t(StampType.Mark, 0.6f)
        val note = t(StampType.Note, 1f)
        val caption = t(StampType.Caption, 0.9f)
        val country = StampType.Country
    }

    // ---- Bar (default) ----

    private fun drawBar(scope: DrawScope, spec: StampSpec, tm: TextMeasurer, pal: Palette) = with(scope) {
        // 边距不随水印大小缩放：它是相对画面的位置
        val marginX = px(pal.marginX / pal.scale)
        val marginY = px(pal.marginY / pal.scale)
        val gap = px(4f)
        val padH = px(8f)
        val padV = px(6f)
        val lineGap = px(1.5f)

        // Wide (landscape) frames would stretch the card into a thin ribbon;
        // cap it and let the anchor's column decide where it sits.
        val outerWidth = (size.width - marginX * 2).coerceAtMost(px(MAX_BAR_WIDTH_DP))

        // Right column: logo and/or signature with the org name under them.
        val rightImages = listOfNotNull(spec.orgLogo, spec.signature)
        val hasRightColumn = rightImages.isNotEmpty() || spec.orgLabel != null

        // First pass assumes a tile, then the tile is sized from the text height.
        // Text height doesn't depend on width (every line is one line), so one
        // pass with an estimated tile width is exact.
        // 地图方块按"每项一行"的高度定；自定义字段和备注过长时会换行，卡片随之变高。
        val textHeightProbe = barTextHeight(spec, tm, lineGap, pal, null)
        val tileSize = if (spec.mapTile != null) (textHeightProbe + padV * 2).coerceAtLeast(px(28f)) else 0f
        var cardWidth = outerWidth - (if (tileSize > 0f) tileSize + gap else 0f)
        val rightColWidth = if (hasRightColumn) cardWidth * 0.24f else 0f
        var textWidth = (cardWidth - padH * 2 - (if (hasRightColumn) rightColWidth + padH else 0f)).coerceAtLeast(1f)
        val marksForWidth = marksText(spec)?.let { tm.measure(it, pal.mark, maxLines = 1) }
        val markReserveW = marksForWidth?.let { it.size.width + px(8f) } ?: 0f
        var metaWidth = ((if (hasRightColumn) textWidth else cardWidth - padH * 2) - markReserveW).coerceAtLeast(1f)
        val chipReserveW = spec.countryCode?.let { tm.measure(it, pal.country, maxLines = 1).size.width + px(3f) * 2 + px(5f) } ?: 0f
        // 单行模式：每行严格一行，不换行、不省略、不缩字号；卡片随最长一行变宽（可超出画面）
        if (!pal.wrap) {
            fun nat(t: String, st: TextStyle) = tm.measure(t, st, softWrap = false, maxLines = 1).size.width.toFloat()
            val needed = listOfNotNull(
                spec.placeName?.let { nat(it, pal.title) + chipReserveW },
                spec.addressLine?.let { nat(it, pal.body) },
                spec.coordinatesText?.let { nat(it, pal.data) },
                dateTimeLine(spec)?.let { nat(it, pal.body) },
                spec.noteLine?.let { nat(it, pal.note) }
            ) + spec.fieldLines.map { nat(it, pal.note) }
            val metaNeeded = spec.chips.joinToString("  ·  ") { it.text }.takeIf { it.isNotBlank() }?.let { nat(it, pal.meta) } ?: 0f
            val grow = maxOf((needed.maxOrNull() ?: 0f) - textWidth, metaNeeded - metaWidth, 0f)
            if (grow > 0f) {
                textWidth += grow
                cardWidth += grow
                metaWidth += grow
            }
        }
        // 文字绝不省略：放不下就换行，卡片随之变高
        val cardHeight = (barTextHeight(spec, tm, lineGap, pal, BarWidths(textWidth, metaWidth, (textWidth - chipReserveW).coerceAtLeast(1f))) + padV * 2).coerceAtLeast(maxOf(px(28f), tileSize))

        val totalWidth = (if (tileSize > 0f) tileSize + gap else 0f) + cardWidth
        val x0 = when (spec.anchor.column) {
            0 -> marginX
            2 -> size.width - marginX - totalWidth
            else -> (size.width - totalWidth) / 2f
        }
        val y0 = if (spec.anchor.row == 0) marginY else size.height - marginY - cardHeight
        val corner = CornerRadius(px(8f))

        // Map tile: its own rounded square, left of the card.
        spec.mapTile?.let { tile ->
            drawImageClipped(tile, Offset(x0, y0), Size(tileSize, tileSize), corner, crop = true)
            drawRoundRect(pal.border, Offset(x0, y0), Size(tileSize, tileSize), corner, style = Stroke(px(1f)))
        }

        val cardX = x0 + (if (tileSize > 0f) tileSize + gap else 0f)
        drawRoundRect(pal.bg, Offset(cardX, y0), Size(cardWidth, cardHeight), corner)
        if (pal.border.alpha > 0.01f) drawRoundRect(pal.border, Offset(cardX, y0), Size(cardWidth, cardHeight), corner, style = Stroke(px(1f)))

        val textX = cardX + padH
        var y = y0 + padV

        // Line 1: place name + ISO-3 country chip.
        spec.placeName?.let { place ->
            val country = spec.countryCode?.let { tm.measure(it, pal.country, maxLines = 1) }
            val chipPadH = px(3f)
            val chipReserve = country?.let { it.size.width + chipPadH * 2 + px(5f) } ?: 0f
            val title = one(tm, place, pal.title, (textWidth - chipReserve).coerceAtLeast(1f), pal.wrap)
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
        spec.addressLine?.let { y += drawLine(tm, it, pal.body, textX, y, textWidth, pal.wrap) + lineGap }
        spec.coordinatesText?.let { y += drawLine(tm, it, pal.data, textX, y, textWidth, pal.wrap) + lineGap }
        spec.fieldLines.forEach { y += drawLine(tm, it, pal.note, textX, y, textWidth, pal.wrap) + lineGap }
        dateTimeLine(spec)?.let { y += drawLine(tm, it, pal.body, textX, y, textWidth, pal.wrap) + lineGap }
        spec.noteLine?.let { y += drawLine(tm, it, pal.note, textX, y, textWidth, pal.wrap) + lineGap }

        // Last line: meta chips as one dotted run on the left, marks on the right.
        val marks = marksText(spec)
        val meta = spec.chips.joinToString("  ·  ") { it.text }.takeIf { it.isNotBlank() }
        if (meta != null || marks != null) {
            val marksLayout = marksForWidth
            val metaLayout = meta?.let { one(tm, it, pal.meta, metaWidth, pal.wrap) }
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
            val reservedBottom = if (marks != null) tm.measure(marks, pal.mark, maxLines = 1).size.height + lineGap * 2 else 0f
            val colHeight = cardHeight - padV * 2 - reservedBottom
            val label = spec.orgLabel?.let { one(tm, it, pal.caption, rightColWidth, pal.wrap) }
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

    private class BarWidths(val text: Float, val meta: Float, val title: Float)

    /** 文字列总高度。[w] 为 null 时每项按一行估算（只用来定地图方块大小）；否则按实际换行后的高度。 */
    private fun barTextHeight(spec: StampSpec, tm: TextMeasurer, lineGap: Float, pal: Palette, w: BarWidths?): Float {
        fun h(text: String, style: TextStyle, width: Float?): Int =
            if (width == null) tm.measure(PROBE, style, maxLines = 1).size.height
            else one(tm, text, style, width, pal.wrap).size.height
        val tw = w?.text
        val heights = buildList {
            spec.placeName?.let { add(h(it, pal.title, w?.title)) }
            spec.addressLine?.let { add(h(it, pal.body, tw)) }
            spec.coordinatesText?.let { add(h(it, pal.data, tw)) }
            spec.fieldLines.forEach { add(h(it, pal.note, tw)) }
            dateTimeLine(spec)?.let { add(h(it, pal.body, tw)) }
            spec.noteLine?.let { add(h(it, pal.note, tw)) }
            val meta = spec.chips.joinToString("  ·  ") { it.text }.takeIf { it.isNotBlank() }
            val marks = marksText(spec)
            if (meta != null || marks != null) {
                add(maxOf(meta?.let { h(it, pal.meta, w?.meta) } ?: 0, tm.measure(marks ?: PROBE, pal.mark, maxLines = 1).size.height))
            }
        }
        if (heights.isEmpty()) return 0f
        return heights.sum() + lineGap * (heights.size - 1)
    }

    private fun dateTimeLine(spec: StampSpec): String? =
        listOfNotNull(spec.dateTimeText, spec.gmtOffsetText).joinToString(" ").takeIf { it.isNotBlank() }

    private fun marksText(spec: StampSpec): String? =
        listOfNotNull(if (spec.showSignedMark) "已签名" else null, if (spec.showBrandMark) "工程水印" else null)
            .joinToString("  ·  ").takeIf { it.isNotBlank() }

    // ---- Minimal: a small label — custom fields, coordinates and date/time ----

    private fun drawMinimal(scope: DrawScope, spec: StampSpec, tm: TextMeasurer, pal: Palette) = with(scope) {
        val marginX = px(pal.marginX / pal.scale)
        val marginY = px(pal.marginY / pal.scale)
        val padH = px(7f)
        val padV = px(5f)
        val lineGap = px(1.5f)
        // 换行开：水印保持紧凑（约画面六成宽），长内容在框内换行；
        // 换行关：尽量一行，最宽可横跨整个画面，超出画面时才换行。文字从不省略、不缩小。
        val maxW = (size.width - marginX * 2 - padH * 2).coerceAtLeast(1f)
        val lines = spec.fieldLines.map { one(tm, it, pal.note, maxW, pal.wrap) } + listOfNotNull(
            spec.coordinatesText?.let { one(tm, it, pal.data, maxW, pal.wrap) },
            dateTimeLine(spec)?.let { one(tm, it, pal.note, maxW, pal.wrap) }
        )
        if (lines.isEmpty()) return@with

        val width = lines.maxOf { it.size.width } + padH * 2
        val height = lines.sumOf { it.size.height } + lineGap * (lines.size - 1) + padV * 2
        val x = when (spec.anchor.column) {
            0 -> marginX
            2 -> size.width - marginX - width
            else -> (size.width - width) / 2f
        }
        val y = when (spec.anchor.row) {
            0 -> marginY
            1 -> (size.height - height) / 2f
            else -> size.height - marginY - height
        }
        drawRoundRect(pal.bg, Offset(x, y), Size(width, height), CornerRadius(px(7f)))
        var ty = y + padV
        lines.forEach { drawText(it, topLeft = Offset(x + padH, ty)); ty += it.size.height + lineGap }
    }

    // ---- Shared helpers ----

    /**
     * 量一段文字，从不省略、不缩小字号。
     * [wrap] = true：放不下就换行；false：严格单行，宽度不受限（可超出画面）。
     */
    private fun one(tm: TextMeasurer, text: String, style: TextStyle, maxWidth: Float, wrap: Boolean): TextLayoutResult =
        if (wrap) tm.measure(
            text = text,
            style = style,
            softWrap = true,
            constraints = Constraints(maxWidth = maxWidth.roundToInt().coerceAtLeast(1))
        ) else tm.measure(text = text, style = style, softWrap = false, maxLines = 1)

    /** Draws a line (wrapping if needed) and returns its height. */
    private fun DrawScope.drawLine(tm: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float, maxWidth: Float, wrap: Boolean): Float {
        val layout = one(tm, text, style, maxWidth, wrap)
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

    private const val PROBE = "Ag中"
}
