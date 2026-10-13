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
    val showCustomText: Boolean = true
) {
    /** Never show an empty line even if the toggle is on and the label is blank. */
    val showOrgLabel: Boolean get() = showOrgLabelToggle && orgLabel.isNotBlank()
}
