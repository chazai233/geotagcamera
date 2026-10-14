package com.geotagcamera.geotagginglocationonphoto.support

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/**
 * The installed APK's versionCode, read at runtime. Not BuildConfig.VERSION_CODE:
 * that is baked in at build time and differs between an all-splits build and a
 * one-ABI build (-Pabi), which breaks reproducible builds for F-Droid. The runtime
 * value is also the real per-ABI code (e.g. 62 for arm64-v8a).
 */
object AppVersion {
    fun code(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)
}
