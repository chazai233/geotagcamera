package com.geotagcamera.geotagginglocationonphoto.ui.local

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.CapturePreferences
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.GeocoderRepository
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import com.geotagcamera.geotagginglocationonphoto.stamp.LogoDecoder
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import com.geotagcamera.geotagginglocationonphoto.stamp.StampRenderer
import com.geotagcamera.geotagginglocationonphoto.storage.MediaStoreImageSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** 一张待加水印的本地照片。 */
data class LocalPhoto(
    val uri: Uri,
    val name: String,
    /** 摆正后的宽高（已按 EXIF 方向换算）。 */
    val width: Int,
    val height: Int,
    /** EXIF 里的拍摄时间；没有则为 null。 */
    val exifTimeMs: Long?,
    /** 用户手动设定的时间，优先于 EXIF。 */
    val customTimeMs: Long? = null,
    /** EXIF 里的 GPS；没有则为 null。 */
    val fix: LocationFix?,
    val address: AddressParts? = null
) {
    val timeMs: Long? get() = customTimeMs ?: exifTimeMs
    val timeFromExif: Boolean get() = customTimeMs == null && exifTimeMs != null
}

sealed interface LocalSaveState {
    data object Idle : LocalSaveState
    data class Saving(val done: Int, val total: Int) : LocalSaveState
    data class Done(val saved: List<Uri>, val failed: Int) : LocalSaveState
}

/**
 * 给本地照片加水印：读 EXIF 时间和 GPS，没有时间的由用户设定；
 * 用当前的水印设置渲染，另存到 Pictures/GeoTagCamera/本地水印。原图不改动。
 * 这些照片不是本应用拍的，所以不做签名证明，也不进应用相册索引。
 */
class LocalWatermarkViewModel(application: Application) : AndroidViewModel(application) {
    private val stampPreferences = StampPreferences(application)
    private val capturePreferences = CapturePreferences(application)
    private val geocoder = GeocoderRepository(application, AppDatabase.get(application).geoCacheDao())

    val fields: StateFlow<StampFields> = stampPreferences.fields
        .stateIn(viewModelScope, SharingStarted.Eagerly, StampFields())

    val orgLogo: StateFlow<Bitmap?> = fields.map { it.orgLogoUri }.distinctUntilChanged()
        .map { path -> path?.let { withContext(Dispatchers.IO) { LogoDecoder.decode(it) } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _photos = MutableStateFlow<List<LocalPhoto>>(emptyList())
    val photos: StateFlow<List<LocalPhoto>> = _photos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _save = MutableStateFlow<LocalSaveState>(LocalSaveState.Idle)
    val save: StateFlow<LocalSaveState> = _save.asStateFlow()

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            val loaded = withContext(Dispatchers.IO) {
                uris.filter { u -> _photos.value.none { it.uri == u } }
                    .take(MAX_PHOTOS)
                    .mapNotNull { runCatching { read(it) }.getOrNull() }
            }
            _photos.value = (_photos.value + loaded).take(MAX_PHOTOS)
            _loading.value = false
            // 有 GPS 的反查地址（离线缓存优先），查到再补上
            loaded.filter { it.fix != null }.forEach { p ->
                val addr = runCatching { geocoder.reverseGeocode(p.fix!!.latitude, p.fix.longitude) }.getOrNull()
                if (addr != null) _photos.value = _photos.value.map { if (it.uri == p.uri) it.copy(address = addr) else it }
            }
        }
    }

    fun remove(uri: Uri) {
        _photos.value = _photos.value.filter { it.uri != uri }
    }

    fun clear() {
        _photos.value = emptyList()
        _save.value = LocalSaveState.Idle
    }

    /** 给一张设定时间；[toAllMissing] 时同时给所有没有时间的照片。 */
    fun setTime(uri: Uri, timeMs: Long, toAllMissing: Boolean) {
        _photos.value = _photos.value.map {
            when {
                it.uri == uri -> it.copy(customTimeMs = timeMs)
                toAllMissing && it.timeMs == null -> it.copy(customTimeMs = timeMs)
                else -> it
            }
        }
    }

    /** 恢复使用 EXIF 时间。 */
    fun useExifTime(uri: Uri) {
        _photos.value = _photos.value.map { if (it.uri == uri) it.copy(customTimeMs = null) else it }
    }

    fun setFieldValues(values: Map<Long, String>) {
        if (values.isEmpty()) return
        viewModelScope.launch {
            stampPreferences.edit { f ->
                f.copy(customFields = f.customFields.map { c -> values[c.id]?.let { c.copy(value = it) } ?: c })
            }
        }
    }

    fun addPreset(id: Long, value: String) {
        viewModelScope.launch {
            stampPreferences.edit { f -> f.copy(customFields = f.customFields.map { if (it.id == id) it.withPresets(listOf(value)) else it }) }
        }
    }

    fun removeHistory(id: Long, value: String) {
        viewModelScope.launch {
            stampPreferences.edit { f ->
                f.copy(customFields = f.customFields.map { if (it.id == id) it.copy(history = it.history.filter { h -> h != value }) else it })
            }
        }
    }

    fun dismissResult() {
        _save.value = LocalSaveState.Idle
    }

    /** 全部加水印并保存。没有时间的照片会被跳过（界面上保存按钮此时不可用）。 */
    fun saveAll() {
        val list = _photos.value.filter { it.timeMs != null }
        if (list.isEmpty() || _save.value is LocalSaveState.Saving) return
        viewModelScope.launch {
            val fieldsNow = stampPreferences.fields.first()
            val quality = capturePreferences.jpegQuality.first()
            val logo = orgLogo.value
            val saved = mutableListOf<Uri>()
            var failed = 0
            list.forEachIndexed { i, p ->
                _save.value = LocalSaveState.Saving(i, list.size)
                val uri = try {
                    withContext(Dispatchers.IO) { render(p, fieldsNow, quality, logo) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    null
                }
                if (uri != null) saved += uri else failed++
            }
            // 用过的字段内容记进历史
            stampPreferences.edit { f -> f.copy(customFields = f.customFields.map { it.withValueInHistory() }) }
            _save.value = LocalSaveState.Done(saved, failed)
        }
    }

    // ---- 读取 ----

    private fun read(uri: Uri): LocalPhoto? {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val exif = resolver.openInputStream(uri)?.use { runCatching { ExifInterface(it) }.getOrNull() }
        val rotation = exif?.rotationDegrees ?: 0
        val (w, h) = if (rotation == 90 || rotation == 270) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
        val latLong = exif?.latLong
        val fix = latLong?.let {
            LocationFix(
                latitude = it[0], longitude = it[1],
                altitudeMeters = exif.getAltitude(Double.NaN).takeIf { a -> !a.isNaN() },
                accuracyMeters = null, bearingDegrees = null, timestampEpochMs = 0L
            )
        }
        return LocalPhoto(uri, displayName(uri), w, h, exif?.let { exifTime(it) }, null, fix)
    }

    private fun displayName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "照片"

    // ---- 渲染与保存 ----

    private suspend fun render(p: LocalPhoto, fields: StampFields, quality: Int, logo: Bitmap?): Uri? {
        val context = getApplication<Application>()
        val time = p.timeMs ?: return null
        val src = File(context.cacheDir, "local_src_${System.nanoTime()}.jpg")
        val out = File(context.cacheDir, "local_out_${System.nanoTime()}.jpg")
        try {
            context.contentResolver.openInputStream(p.uri)?.use { input -> src.outputStream().use { input.copyTo(it) } } ?: return null
            val srcExif = runCatching { ExifInterface(src.absolutePath) }.getOrNull()
            var image = try {
                decodeUpright(src, 1)
            } catch (e: OutOfMemoryError) {
                System.gc()
                decodeUpright(src, 2)
            } ?: return null
            image = StampRenderer.stamp(
                context = context,
                source = image,
                fix = p.fix,
                addressParts = p.address,
                capturedAtEpochMs = time,
                fields = fields,
                mapTile = null,
                orgLogo = if (fields.showOrgLogo) logo else null,
                hasSignature = false,
                weatherChipText = null,
                signature = null,
                note = null
            ).also { if (it !== image) image.recycle() }
            FileOutputStream(out).use { image.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            image.recycle()
            writeExif(out, srcExif, p.fix, time)
            val base = p.name.substringBeforeLast('.').ifBlank { "IMG" }
            return MediaStoreImageSaver.save(context, out, "${base}_水印.jpg", subDir = MediaStoreImageSaver.LOCAL_DIR)
        } finally {
            src.delete()
            out.delete()
        }
    }

    private fun writeExif(file: File, source: ExifInterface?, fix: LocationFix?, timeMs: Long) {
        val exif = ExifInterface(file.absolutePath)
        // 保留相机参数
        source?.let { s ->
            COPY_TAGS.forEach { tag -> s.getAttribute(tag)?.let { exif.setAttribute(tag, it) } }
        }
        val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val t = fmt.format(timeMs)
        exif.setAttribute(ExifInterface.TAG_DATETIME, t)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, t)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, t)
        val offset = SimpleDateFormat("XXX", Locale.US).format(timeMs)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        fix?.let {
            exif.setLatLong(it.latitude, it.longitude)
            it.altitudeMeters?.let { a -> exif.setAltitude(a) }
        }
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GeoTag Camera CN ${BuildConfig.VERSION_NAME}")
        exif.saveAttributes()
    }

    companion object {
        const val MAX_PHOTOS = 50

        private val COPY_TAGS = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH
        )

        /** EXIF 拍摄时间：DateTimeOriginal > Digitized > DateTime；有时区偏移就用，没有按手机时区。 */
        fun exifTime(exif: ExifInterface): Long? {
            val pairs = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL to ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED to ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
                ExifInterface.TAG_DATETIME to ExifInterface.TAG_OFFSET_TIME
            )
            for ((dateTag, offsetTag) in pairs) {
                val raw = exif.getAttribute(dateTag)?.trim()?.takeIf { it.length >= 19 && !it.startsWith("0000") } ?: continue
                val offset = exif.getAttribute(offsetTag)?.trim()
                val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                    timeZone = if (offset != null && Regex("[+-]\\d{2}:\\d{2}").matches(offset)) TimeZone.getTimeZone("GMT$offset") else TimeZone.getDefault()
                    isLenient = false
                }
                val ms = runCatching { fmt.parse(raw.substring(0, 19))?.time }.getOrNull()
                if (ms != null && ms > 0) return ms
            }
            return null
        }

        /** 解码并按 EXIF 方向摆正，返回可写的位图。 */
        fun decodeUpright(file: File, sampleSize: Int): Bitmap? {
            val orientation = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            }
            if (matrix.isIdentity) return bitmap
            val bounds = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            matrix.mapRect(bounds)
            matrix.postTranslate(-bounds.left, -bounds.top)
            val out = Bitmap.createBitmap(bounds.width().toInt(), bounds.height().toInt(), Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(out).drawBitmap(bitmap, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
            return out
        }
    }
}
