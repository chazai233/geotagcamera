package com.geotagcamera.geotagginglocationonphoto.stamp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Decodes the org logo at most [maxPx] on its long side. Logos picked in 1.1.0
 * were stored at full camera resolution; decoding one of those as-is costs
 * 100+ MB and Android refuses to draw a bitmap that large ("Canvas: trying to
 * draw too large bitmap"), so every caller goes through here.
 */
object LogoDecoder {
    fun decode(path: String, maxPx: Int = 512): Bitmap? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val longSide = maxOf(decoded.width, decoded.height)
        if (longSide <= maxPx) return decoded
        val scale = maxPx.toFloat() / longSide
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        scaled
    }.getOrNull()
}
