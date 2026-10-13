package com.geotagcamera.geotagginglocationonphoto.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Colours burned into photos. Fixed on purpose: a stamp must look the same
 * whatever theme, dynamic colour or contrast setting the phone had.
 */
object StampColors {
    val Scrim = Color(0xB3121417) // 70% near-black
    val Border = Color(0x24FFFFFF)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xE6FFFFFF)
    val TextMuted = Color(0x99FFFFFF)
    val CountryChip = Color(0xFFE9EBEC)
    val CountryText = Color(0xFF0A0C0E)
}

private val Tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/**
 * The stamp's own type scale, in reference dp (the frame's short side is
 * 360). Independent of the user's font-scale setting by design: the stamp
 * is part of the photo, not the UI. Body lines land near 2.4% of the short
 * side, the same proportion professional geotag stamps use.
 */
object StampType {
    val Title = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, color = StampColors.TextPrimary, lineHeightStyle = Tight)
    val Body = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 8.5.sp, lineHeight = 11.sp, color = StampColors.TextSecondary, lineHeightStyle = Tight)
    val Data = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 8.5.sp, lineHeight = 11.sp, color = StampColors.TextPrimary, fontFeatureSettings = "tnum", lineHeightStyle = Tight)
    val Meta = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 7.5.sp, lineHeight = 10.sp, color = StampColors.TextSecondary, fontFeatureSettings = "tnum", lineHeightStyle = Tight)
    val Mark = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Medium, fontSize = 6.5.sp, lineHeight = 10.sp, color = StampColors.TextMuted, letterSpacing = 0.6.sp, lineHeightStyle = Tight)
    val Caption = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 7.sp, lineHeight = 9.sp, color = StampColors.TextSecondary, lineHeightStyle = Tight)
    val Country = TextStyle(fontFamily = RobotoMono, fontWeight = FontWeight.Bold, fontSize = 7.sp, lineHeight = 10.sp, color = StampColors.CountryText, lineHeightStyle = Tight)
}
