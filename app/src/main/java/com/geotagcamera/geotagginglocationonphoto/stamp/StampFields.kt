package com.geotagcamera.geotagginglocationonphoto.stamp

/**
 * The two stamp layouts. BAR is the full-information strip (default);
 * MINIMAL is a small coordinates-and-time pill. The old CARD layout was
 * dropped in 1.2.0; a stored "CARD" preference falls back to BAR.
 */
enum class StampTemplate { BAR, MINIMAL }

/** Where the stamp sits: Minimal uses all nine, Bar only the top or bottom row. Set by dragging on the viewfinder. */
enum class StampAnchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT, MID_LEFT, MID_CENTER, MID_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    /** 0 = top, 1 = middle, 2 = bottom. */
    val row: Int get() = ordinal / 3

    /** 0 = left, 1 = centre, 2 = right. */
    val column: Int get() = ordinal % 3
}

/**
 * Which fields the field worker/student wants burned into the stamp, and how
 * the stamp itself is presented. Directly answers the "no way to select/
 * deselect stamp fields" complaint competitors get — see docs/research.md.
 *
 * Defaults match the design system's own component state exactly (its
 * `.dc.html` script block): map/country/address/coords/datetime/gmt/org/
 * logo/signature/brand default on, altitude/bearing/weather default off,
 * template BAR, position BOTTOM_LEFT. Note `showAccuracy` defaults `true`
 * here, a deliberate change from this field's old `false` default, to match
 * the locked design.
 *
 * `requireSignature` and `showSignatureField` are two different things:
 * `requireSignature` gates the *capture flow* (must a signature be collected
 * before saving at all), `showSignatureField` controls whether a captured
 * signature's "SIGNED" mark actually *displays* in the stamp. A signature
 * can be required but hidden from the stamp, or shown whenever present
 * without ever being required — keep these independent.
 */
data class StampFields(
    val template: StampTemplate = StampTemplate.BAR,
    val position: StampAnchor = StampAnchor.BOTTOM_LEFT,
    val showMap: Boolean = true,
    val showCountry: Boolean = true,
    val showAddress: Boolean = true,
    val showCoordinates: Boolean = true,
    val showTimestamp: Boolean = true,
    val showGmtOffset: Boolean = true,
    val showAltitude: Boolean = false,
    val showAccuracy: Boolean = true,
    val showBearing: Boolean = false,
    val showWeather: Boolean = false,
    val showOrgLabelToggle: Boolean = true,
    val orgLabel: String = "",
    val showOrgLogo: Boolean = true,
    val orgLogoUri: String? = null,
    val showSignatureField: Boolean = true,
    val requireSignature: Boolean = false,
    val showBrandMark: Boolean = true,
    /** A fixed line on every photo ("Contract A-123", "Inspector: R. Das"). */
    val customText: String = "",
    val showCustomText: Boolean = true,
    /** 水印整体大小倍数，0.5–2.5。 */
    val styleScale: Float = 1f,
    /** 背景颜色（RGB，不含透明度）。 */
    val bgColor: Int = 0x121417,
    /** 背景不透明度，0–1。 */
    val bgOpacity: Float = 0.70f,
    /** 文字颜色（RGB）。 */
    val textColor: Int = 0xFFFFFF,
    /** 文字不透明度，0.2–1。 */
    val textOpacity: Float = 1f,
    /** 水印离画面左右、上下边缘的距离（参考 dp，画面短边 = 360）。 */
    val marginX: Float = 8f,
    val marginY: Float = 8f,
    /** 日期时间格式（java.text.SimpleDateFormat 格式串）。 */
    val dateFormat: String = DateFormats.DEFAULT
) {
    /** Never show an empty line even if the toggle is on and the label is blank. */
    val showOrgLabel: Boolean get() = showOrgLabelToggle && orgLabel.isNotBlank()
}

/** 日期时间格式预设。星期按中文显示。 */
object DateFormats {
    const val DEFAULT = "yyyy-MM-dd HH:mm:ss EEEE"

    val PRESETS = listOf(
        "yyyy-MM-dd HH:mm:ss EEEE",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy年MM月dd日 EEEE HH:mm:ss",
        "yyyy年MM月dd日 HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy.MM.dd HH:mm",
        "MM-dd HH:mm",
        "dd/MM/yyyy HH:mm:ss",
        "yyyy-MM-dd a hh:mm",
        "yyyy-MM-dd",
        "HH:mm:ss"
    )

    /** 按 [pattern] 格式化；格式串非法时退回默认格式，绝不让水印出错。 */
    fun format(pattern: String, epochMs: Long): String {
        val locale = java.util.Locale.SIMPLIFIED_CHINESE
        val date = java.util.Date(epochMs)
        return runCatching { java.text.SimpleDateFormat(pattern.ifBlank { DEFAULT }, locale).format(date) }
            .getOrElse { java.text.SimpleDateFormat(DEFAULT, locale).format(date) }
    }

    fun isValid(pattern: String): Boolean =
        pattern.isNotBlank() && runCatching { java.text.SimpleDateFormat(pattern, java.util.Locale.SIMPLIFIED_CHINESE) }.isSuccess
}
