package com.geotagcamera.geotagginglocationonphoto.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.geotagcamera.geotagginglocationonphoto.exif.ProofReader
import com.geotagcamera.geotagginglocationonphoto.security.OriginalBytes
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime

/**
 * Re-adds GeoTag Camera photos to the in-app list: after a reinstall (the
 * 1.4.0 move to the permanent signing key needs one), on a new phone, or for
 * photos a colleague sent. Only files carrying a GeoTag proof are taken, and
 * everything is read from the file itself, so the list stays trustworthy.
 */
class PhotoImporter(private val context: Context) {

    data class Result(val added: Int, val alreadyThere: Int, val noProof: Int, val unreadable: Int) {
        fun summary(): String = buildList {
            add("已添加 $added 张")
            if (alreadyThere > 0) add("$alreadyThere 张已在列表中")
            if (noProof > 0) add("$noProof 张不是本应用拍的")
            if (unreadable > 0) add("$unreadable 张无法读取")
        }.joinToString("，")
    }

    suspend fun import(uris: List<Uri>, projectId: Long?): Result {
        val dao = AppDatabase.get(context).photoDao()
        var added = 0; var dup = 0; var noProof = 0; var bad = 0
        for (uri in uris) {
            // Keep read access across restarts for document-picker URIs.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val bytes = OriginalBytes.read(context, uri)
            if (bytes == null) { bad++; continue }
            val payload = ProofReader.read(bytes)
            if (payload == null) { noProof++; continue }
            if (dao.countBySha(payload.sha256Hex) > 0) { dup++; continue }

            val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
            val latLong = exif?.latLong
            val claims = payload.claims
            val lat = claims?.latitude ?: latLong?.getOrNull(0)
            val lon = claims?.longitude ?: latLong?.getOrNull(1)
            if (lat == null || lon == null) { noProof++; continue }
            val time = claims?.epochMs
                ?: runCatching { OffsetDateTime.parse(payload.timestampIso).toInstant().toEpochMilli() }.getOrNull()
                ?: System.currentTimeMillis()

            dao.insert(
                PhotoEntity(
                    filePath = uri.toString(),
                    capturedAtEpochMs = time,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = exif?.getAltitude(Double.NaN)?.takeIf { !it.isNaN() },
                    accuracyMeters = exif?.getAttributeDouble(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, Double.NaN)?.takeIf { !it.isNaN() }?.toFloat(),
                    bearingDegrees = exif?.getAttributeDouble(ExifInterface.TAG_GPS_IMG_DIRECTION, Double.NaN)?.takeIf { !it.isNaN() }?.toFloat(),
                    address = null,
                    addressFromCache = false,
                    orgLabel = null,
                    sha256Hash = payload.sha256Hex,
                    signatureBase64 = payload.signatureBase64,
                    signingKeyAlias = "imported",
                    fieldWorkerSignature = false,
                    projectId = projectId
                )
            )
            added++
        }
        return Result(added, dup, noProof, bad)
    }
}
