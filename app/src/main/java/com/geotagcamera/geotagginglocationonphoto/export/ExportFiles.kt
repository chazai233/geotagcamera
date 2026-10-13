package com.geotagcamera.geotagginglocationonphoto.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where exports are written and how they are handed to other apps. */
object ExportFiles {

    /** A fresh file in cache/exports. Old exports are cleared first: they are copies, never the only record. */
    fun newFile(context: Context, baseName: String, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return File(dir, "${safeName(baseName)}_$stamp.$extension")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.exports", file)

    fun share(context: Context, file: File, mimeType: String, title: String) {
        val uri = uriFor(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** File-system-safe, keeps letters in any script (Bengali, Hindi project names). */
    fun safeName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(60).ifEmpty { "geotag" }
}
