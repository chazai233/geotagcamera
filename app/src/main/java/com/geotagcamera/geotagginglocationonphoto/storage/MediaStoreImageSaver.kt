package com.geotagcamera.geotagginglocationonphoto.storage

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Publishes a fully processed (stamped, EXIF-tagged, signed) JPEG into the
 * device's real Photos/Gallery app under Pictures/GeoTagCamera, instead of
 * the app-private sandbox nobody but this app can see.
 *
 * Scoped storage (API 29+) needs no permission — insert + IS_PENDING write.
 * API 26-28 predates scoped storage and RELATIVE_PATH, so it writes the file
 * directly into the public Pictures dir (needs WRITE_EXTERNAL_STORAGE,
 * declared maxSdkVersion=28) and registers it via the legacy DATA column.
 */
object MediaStoreImageSaver {
    private const val RELATIVE_DIR = "GeoTagCamera"

    /** 无水印原图的子文件夹：Pictures/GeoTagCamera/原图 */
    const val ORIGINALS_DIR = "原图"

    /** 本地照片加水印后的子文件夹：Pictures/GeoTagCamera/本地水印 */
    const val LOCAL_DIR = "本地水印"

    /** [subDir]：放在 Pictures/GeoTagCamera 下的子文件夹里，null = 直接放在该文件夹。 */
    fun save(context: Context, sourceFile: File, displayName: String, subDir: String? = null): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, sourceFile, displayName, subDir)
        } else {
            saveLegacy(context, sourceFile, displayName, subDir)
        }

    private fun saveScoped(context: Context, sourceFile: File, displayName: String, subDir: String?): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$RELATIVE_DIR" + (subDir?.let { "/$it" } ?: ""))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        val wrote = runCatching {
            resolver.openOutputStream(uri)?.use { out -> sourceFile.inputStream().use { it.copyTo(out) } }
                ?: error("no output stream")
        }.isSuccess

        if (!wrote) {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, sourceFile: File, displayName: String, subDir: String?): Uri? {
        val baseDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            RELATIVE_DIR
        )
        val picturesDir = if (subDir != null) File(baseDir, subDir) else baseDir
        if (!picturesDir.exists() && !picturesDir.mkdirs()) return null

        val destFile = uniqueFile(picturesDir, displayName)
        val copied = runCatching { sourceFile.copyTo(destFile, overwrite = false) }.isSuccess
        if (!copied) return null

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, destFile.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATA, destFile.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    /** `name.jpg`, or `name_2.jpg`, `name_3.jpg`… — never an existing file. */
    private fun uniqueFile(dir: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "jpg")
        var candidate = File(dir, displayName)
        var i = 2
        while (candidate.exists()) candidate = File(dir, "${base}_$i.$ext").also { i++ }
        return candidate
    }

    /**
     * What a delete actually did: [deleted] are gone now, [consent] (Android
     * 11+) is one system prompt covering the Uris this app may no longer
     * delete on its own, [failed] could not be deleted at all.
     */
    data class DeleteOutcome(
        val deleted: List<Uri>,
        val consent: IntentSender?,
        val awaitingConsent: List<Uri>,
        val failed: List<Uri>
    )

    fun delete(context: Context, uris: List<Uri>): DeleteOutcome {
        val resolver = context.contentResolver
        val deleted = mutableListOf<Uri>()
        val blocked = mutableListOf<Uri>()
        val failed = mutableListOf<Uri>()
        for (uri in uris) {
            try {
                // 0 rows means it was already gone: as good as deleted.
                resolver.delete(uri, null, null)
                deleted += uri
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) blocked += uri else failed += uri
            } catch (e: Exception) {
                failed += uri
            }
        }
        val consent = if (blocked.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { MediaStore.createDeleteRequest(resolver, blocked).intentSender }.getOrNull()
        } else null
        return DeleteOutcome(deleted, consent, if (consent != null) blocked else emptyList(), failed + if (consent == null) blocked else emptyList())
    }
}
