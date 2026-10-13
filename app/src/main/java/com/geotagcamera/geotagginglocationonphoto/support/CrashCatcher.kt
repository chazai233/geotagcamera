package com.geotagcamera.geotagginglocationonphoto.support

import android.content.Context
import android.os.Build
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the phone so the user can choose to send it.
 * Nothing leaves the device on its own: the next launch offers a report
 * (see [ReportProblemScreen]); declining deletes the file.
 */
object CrashCatcher {

    private const val FILE = "crash/last_crash.txt"
    private const val MAX_CHARS = 20_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("Crash at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }.take(MAX_CHARS)
        File(context.filesDir, FILE).apply { parentFile?.mkdirs() }.writeText(text)
    }
}
