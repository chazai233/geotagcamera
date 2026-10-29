package com.geotagcamera.geotagginglocationonphoto.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.captureDataStore by preferencesDataStore(
    name = "capture_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * What the next shot is taken under: the active project, and a note that is
 * burned into every stamp until changed ("Pole 12", "North wall, crack 3").
 * Both are sticky across app restarts because field work spans days.
 */
class CapturePreferences(private val context: Context) {

    private object Keys {
        val ACTIVE_PROJECT = longPreferencesKey("active_project_id")
        val NOTE = stringPreferencesKey("capture_note")
        val FLASH = intPreferencesKey("flash_mode")
        val RESOLUTION = stringPreferencesKey("capture_resolution")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val SAVE_ORIGINAL = booleanPreferencesKey("save_original")
        val CUSTOM_TIME_ON = booleanPreferencesKey("custom_time_on")
        val CUSTOM_TIME_OFFSET = longPreferencesKey("custom_time_offset_ms")
    }

    /** 照片分辨率档位。 */
    val resolution: Flow<CaptureResolution> = context.captureDataStore.data.map { prefs ->
        prefs[Keys.RESOLUTION]?.let { runCatching { CaptureResolution.valueOf(it) }.getOrNull() } ?: CaptureResolution.MP12
    }

    suspend fun setResolution(value: CaptureResolution) {
        context.captureDataStore.edit { it[Keys.RESOLUTION] = value.name }
    }

    /** 成片 JPEG 质量（60–100）。 */
    val jpegQuality: Flow<Int> = context.captureDataStore.data.map { (it[Keys.JPEG_QUALITY] ?: DEFAULT_JPEG_QUALITY).coerceIn(60, 100) }

    suspend fun setJpegQuality(value: Int) {
        context.captureDataStore.edit { it[Keys.JPEG_QUALITY] = value.coerceIn(60, 100) }
    }

    /** 同时保存一张无水印原图。 */
    val saveOriginal: Flow<Boolean> = context.captureDataStore.data.map { it[Keys.SAVE_ORIGINAL] ?: false }

    suspend fun setSaveOriginal(value: Boolean) {
        context.captureDataStore.edit { it[Keys.SAVE_ORIGINAL] = value }
    }

    /**
     * 自定义拍摄时间。保存的是与真实时钟的差值，所以设定后时间会继续走，
     * 连拍的照片仍然有先后。
     */
    val customTime: Flow<CustomTime> = context.captureDataStore.data.map {
        CustomTime(enabled = it[Keys.CUSTOM_TIME_ON] ?: false, offsetMs = it[Keys.CUSTOM_TIME_OFFSET] ?: 0L)
    }

    /** 把水印时间设为 [targetEpochMs]（以此刻为基准）。 */
    suspend fun setCustomTime(targetEpochMs: Long) {
        val offset = targetEpochMs - System.currentTimeMillis()
        context.captureDataStore.edit {
            it[Keys.CUSTOM_TIME_OFFSET] = offset
            it[Keys.CUSTOM_TIME_ON] = true
        }
    }

    suspend fun setCustomTimeEnabled(enabled: Boolean) {
        context.captureDataStore.edit { it[Keys.CUSTOM_TIME_ON] = enabled }
    }

    val activeProjectId: Flow<Long?> = context.captureDataStore.data.map { it[Keys.ACTIVE_PROJECT]?.takeIf { id -> id > 0 } }

    val note: Flow<String> = context.captureDataStore.data.map { it[Keys.NOTE] ?: "" }

    /** CameraX ImageCapture.FLASH_MODE_* value; Auto (0) until the user picks one. */
    val flashMode: Flow<Int> = context.captureDataStore.data.map { it[Keys.FLASH] ?: 0 }

    suspend fun setFlashMode(mode: Int) {
        context.captureDataStore.edit { it[Keys.FLASH] = mode }
    }

    suspend fun setActiveProject(id: Long?) {
        context.captureDataStore.edit { if (id == null) it.remove(Keys.ACTIVE_PROJECT) else it[Keys.ACTIVE_PROJECT] = id }
    }

    suspend fun setNote(note: String) {
        context.captureDataStore.edit { it[Keys.NOTE] = note.take(MAX_NOTE_CHARS) }
    }

    companion object {
        /** One stamp line's worth; longer notes would only be ellipsized. */
        const val MAX_NOTE_CHARS = 120
        const val DEFAULT_JPEG_QUALITY = 92
    }
}

/** 自定义时间：开启时，水印、EXIF 和相册记录都使用"真实时间 + offsetMs"。 */
data class CustomTime(val enabled: Boolean, val offsetMs: Long) {
    val active: Boolean get() = enabled && offsetMs != 0L

    /** [realEpochMs] 对应的水印时间。 */
    fun toStampTime(realEpochMs: Long): Long = if (active) realEpochMs + offsetMs else realEpochMs
}

/**
 * 照片分辨率档位。目标尺寸按 4:3 / 16:9 给出，相机会选最接近的实际支持尺寸。
 * [MAX] 不设上限，并允许相机使用高像素模式（如 5000 万像素传感器的全尺寸输出）。
 */
enum class CaptureResolution(val label: String, val detail: String, val size43: android.util.Size?, val size169: android.util.Size?) {
    MAX("最高", "设备支持的最大像素（可达 5000 万+），文件大、处理慢", null, null),
    MP12("1200 万", "4032×3024，清晰度和速度兼顾（默认）", android.util.Size(4032, 3024), android.util.Size(4032, 2268)),
    MP8("800 万", "3264×2448", android.util.Size(3264, 2448), android.util.Size(3840, 2160)),
    MP5("500 万", "2592×1944", android.util.Size(2592, 1944), android.util.Size(2560, 1440)),
    MP3("300 万", "2048×1536，文件最小", android.util.Size(2048, 1536), android.util.Size(1920, 1080))
}
