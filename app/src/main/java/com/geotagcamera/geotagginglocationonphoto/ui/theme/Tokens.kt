package com.geotagcamera.geotagginglocationonphoto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The GeoTag Camera design system in code. Every screen takes colour,
 * spacing, shape and motion from here; no screen keeps its own literals.
 * Rationale and rules: docs/design-study.md (local) and the Design System
 * doc. Principles in one line each:
 *
 *  1. Proof first: integrity states are always visible and unambiguous.
 *  2. The frame is sacred: chrome stays dark, small and out of the photo.
 *  3. Readable in the sun, with gloves on: big targets, strong contrast.
 *  4. Simple by default, deep on demand: the profile decides what shows.
 *  5. Calm and honest: no decoration, no emoji, plain words.
 *  6. Works anywhere: offline, old phones, any screen and rotation.
 */

/** The camera layer: always dark, whatever the system theme. */
object ChromeTokens {
    val Base = Color(0xFF08090A)
    val Raised = Color(0xFF121417)
    val Pill = Color(0xFF1B1E22)
    val Ink = Color(0xFFF5F7F8)
    val InkMuted = Color(0xA6F5F7F8)
    val Muted = Color(0x8CF5F7F8)
    val Outline = Color(0xFF33383D)
    val OutlineSoft = Color(0x38FFFFFF)
    /** Translucent control background over the live preview. */
    val Glass = Color(0x9E141619)
    val GlassBorder = Color(0x21FFFFFF)
    /** Text/icons on an Ink-filled button. */
    val OnInk = Color(0xFF0C0E10)
}

/**
 * Proof and status colours. Fixed in every theme (including dynamic colour)
 * because their meaning must never shift. Always paired with an icon and a
 * word, never colour alone.
 */
object ProofColors {
    val Verified = Color(0xFF56CB98)
    /** Same hue, darker, for 4.5:1 contrast on light surfaces. */
    val VerifiedOnLight = Color(0xFF2FA878)
    val Live = Color(0xFF6DB6FF)
    val Pending = Color(0xFFE5A24B)
    val Tamper = Color(0xFFE24947)

    /** Verified green that reads on whatever surface is current. */
    val verified: Color
        @Composable @ReadOnlyComposable
        get() = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) VerifiedOnLight else Verified
}

/** 4dp grid. Screen margins are [L] on phones, [XL] from 600dp wide. */
object Spacing {
    val XXS = 2.dp
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

/** Corner radii. A nested corner is the outer radius minus the padding between them. */
object Radii {
    val XS = 4.dp
    val S = 8.dp
    val M = 14.dp
    val L = 22.dp
}

/** Durations (ms). Motion explains a change; nothing loops or decorates. */
object Motion {
    const val QUICK = 120
    const val STANDARD = 220
    const val FRAME = 280
    const val EMPHASIZED = 400
}

val GeoTagShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.XS),
    small = RoundedCornerShape(Radii.S),
    medium = RoundedCornerShape(Radii.M),
    large = RoundedCornerShape(Radii.L),
    extraLarge = RoundedCornerShape(28.dp)
)
