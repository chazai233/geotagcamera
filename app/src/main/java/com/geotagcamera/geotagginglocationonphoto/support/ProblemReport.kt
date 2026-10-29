package com.geotagcamera.geotagginglocationonphoto.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import com.geotagcamera.geotagginglocationonphoto.export.ExportFiles
import java.io.File

/** Builds the report email. No server: the user's own email app sends it, or nothing is sent. */
object ProblemReport {

    const val EMAIL = "konkomaji@gmail.com"
    const val ISSUES_URL = "https://github.com/konkomaji/geotagcamera/issues/new"

    enum class Kind(val label: String) { PROBLEM("问题"), IDEA("建议"), QUESTION("提问") }

    /** Facts that help reproduce a problem. Never location, photos or captures. */
    fun deviceInfo(context: Context): String = buildString {
        appendLine("App: GeoTag Camera ${BuildConfig.VERSION_NAME} (${AppVersion.code(context)}, ${BuildConfig.BUILD_TYPE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("CPU: ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
        val dm = Resources.getSystem().displayMetrics
        append("Screen: ${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi} dpi")
    }

    fun subject(kind: Kind, message: String): String =
        "[GeoTag Camera ${BuildConfig.VERSION_NAME}] ${kind.label}: " + message.lineSequence().first().take(60)

    fun body(context: Context, message: String, includeDevice: Boolean, crash: String?): String = buildString {
        appendLine(message.trim())
        if (includeDevice) { appendLine(); appendLine("--"); appendLine(deviceInfo(context)) }
        if (crash != null) { appendLine(); appendLine("-- Crash details --"); appendLine(crash) }
    }

    /**
     * The app's own recent log lines, written to a shareable file. Lines naming
     * map tiles are dropped: tile numbers reveal roughly where you are.
     */
    fun captureLog(context: Context): File? = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "800", "--pid", android.os.Process.myPid().toString()))
        val lines = process.inputStream.bufferedReader().readLines()
            .filterNot { it.contains("openfreemap", ignoreCase = true) || it.contains(".pbf") }
        ExportFiles.newFile(context, "geotag-app-log", "txt").apply { writeText(lines.joinToString("\n")) }
    }.getOrNull()

    /** Opens an email app. Returns false when the phone has none. */
    fun send(context: Context, subject: String, body: String, attachment: File?): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            if (attachment != null) {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, ExportFiles.uriFor(context, attachment))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else type = "message/rfc822"
            // Only email apps, not every app that accepts text.
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        }
        return try {
            context.startActivity(Intent.createChooser(send, "选择发送方式").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun openIssues(context: Context) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
