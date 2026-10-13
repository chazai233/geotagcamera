package com.geotagcamera.geotagginglocationonphoto.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * A drawn stand-in map for the Settings stamp preview, so the preview shows
 * the real footprint of the map tile without a network call.
 */
internal object SampleMap {
    val tile: Bitmap by lazy { draw(256) }

    private fun draw(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        c.drawColor(Color.rgb(0xF2, 0xEF, 0xE9))
        val park = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xCD, 0xE8, 0xC5) }
        c.drawRect(s * 0.05f, s * 0.6f, s * 0.38f, s * 0.95f, park)
        val water = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xAA, 0xD3, 0xDF) }
        c.drawPath(Path().apply { moveTo(s * 0.7f, 0f); quadTo(s * 0.62f, s * 0.4f, s, s * 0.55f); lineTo(s, 0f); close() }, water)
        val minor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = s * 0.03f }
        listOf(0.18f, 0.42f, 0.8f).forEach { f -> c.drawLine(0f, s * f, s, s * f, minor); c.drawLine(s * f, 0f, s * f, s, minor) }
        val major = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF7, 0xC8, 0x6B); strokeWidth = s * 0.05f }
        c.drawLine(0f, s * 0.3f, s, s * 0.72f, major)
        val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE5, 0x39, 0x35) }
        c.drawCircle(s / 2f, s / 2f - s * 0.06f, s * 0.06f, pin)
        c.drawPath(Path().apply { moveTo(s / 2f - s * 0.05f, s / 2f - s * 0.04f); lineTo(s / 2f, s / 2f + s * 0.03f); lineTo(s / 2f + s * 0.05f, s / 2f - s * 0.04f); close() }, pin)
        c.drawCircle(s / 2f, s / 2f - s * 0.06f, s * 0.022f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return bmp
    }
}
