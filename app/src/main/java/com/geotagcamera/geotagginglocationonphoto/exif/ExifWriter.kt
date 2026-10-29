package com.geotagcamera.geotagginglocationonphoto.exif

import androidx.exifinterface.media.ExifInterface
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Embeds GPS + timing data into the JPEG's own EXIF tags, on top of the
 * visible stamp — so location survives even if a viewer strips or crops the
 * watermark, and GIS/photo tools that read EXIF pick it up automatically.
 */
object ExifWriter {

    /**
     * [keepOrientation]：原图（未经本应用旋转的相机原始 JPEG）保留相机写入的方向标记。
     */
    fun write(file: File, fix: LocationFix, capturedAtEpochMs: Long, userComment: String? = null, keepOrientation: Boolean = false) {
        val exif = ExifInterface(file.absolutePath)

        exif.setLatLong(fix.latitude, fix.longitude)
        fix.altitudeMeters?.let { exif.setAltitude(it) }

        val dateTimeFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        exif.setAttribute(ExifInterface.TAG_DATETIME, dateTimeFormat.format(capturedAtEpochMs))
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTimeFormat.format(capturedAtEpochMs))
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTimeFormat.format(capturedAtEpochMs))
        // 相机自己写的亚秒值对应真实时间，改了时间就不能留着
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME, null)
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, null)
        exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, null)

        val gpsDateFormat = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val gpsTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateFormat.format(capturedAtEpochMs))
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeFormat.format(capturedAtEpochMs))

        // The pixels are already upright (the pipeline applies the camera's
        // rotation before stamping), so say so explicitly: 0 is not a valid value.
        if (!keepOrientation) exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        // Local time offset, so DateTimeOriginal isn't ambiguous across time zones.
        val offset = SimpleDateFormat("XXX", Locale.US).format(capturedAtEpochMs)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
        // Horizontal accuracy and direction of travel, when the fix has them.
        fix.accuracyMeters?.takeIf { it.isFinite() && it >= 0f }?.let {
            exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, "${(it * 100).toInt()}/100")
        }
        fix.bearingDegrees?.takeIf { it.isFinite() }?.let {
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.GPS_DIRECTION_TRUE)
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "${(it * 100).toInt()}/100")
        }
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoTag Camera CN ${BuildConfig.VERSION_NAME}")

        userComment?.let { exif.setAttribute(ExifInterface.TAG_USER_COMMENT, it) }

        exif.saveAttributes()
    }
}
