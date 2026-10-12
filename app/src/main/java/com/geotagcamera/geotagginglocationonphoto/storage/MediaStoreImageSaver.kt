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

    fun save(context: Context, sourceFile: File, displayName: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, sourceFile, displayName)
        } else {
            saveLegacy(context, sourceFile, displayName)
        }

    private fun saveScoped(context: Context, sourceFile: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$RELATIVE_DIR")
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
    private fun saveLegacy(context: Context, sourceFile: File, displayName: String): Uri? {
        val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            RELATIVE_DIR
        )
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

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        /** Android 11+ wants the user to confirm (e.g. the app was reinstalled and no longer owns the files). */
        data class NeedsConsent(val request: IntentSender) : DeleteResult
        data object Failed : DeleteResult
    }

    /**
     * Deletes photos this app saved. Files the app still owns go straight
     * away; on Android 11+ anything else comes back as one system consent
     * request covering every Uri that couldn't be deleted directly.
     */
    fun delete(context: Context, uris: List<Uri>): DeleteResult {
        val resolver = context.contentResolver
        val blocked = mutableListOf<Uri>()
        var failed = false
        for (uri in uris) {
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) blocked += uri else failed = true
            } catch (e: Exception) {
                failed = true
            }
        }
        if (blocked.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return DeleteResult.NeedsConsent(MediaStore.createDeleteRequest(resolver, blocked).intentSender)
        }
        return if (failed) DeleteResult.Failed else DeleteResult.Deleted
    }
}
