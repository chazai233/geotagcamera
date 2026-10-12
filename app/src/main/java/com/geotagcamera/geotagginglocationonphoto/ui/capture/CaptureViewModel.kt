package com.geotagcamera.geotagginglocationonphoto.ui.capture

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotagcamera.geotagginglocationonphoto.data.AppDatabase
import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import com.geotagcamera.geotagginglocationonphoto.exif.ExifWriter
import com.geotagcamera.geotagginglocationonphoto.exif.UserCommentCodec
import com.geotagcamera.geotagginglocationonphoto.exif.XmpWriter
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.GeocoderRepository
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import com.geotagcamera.geotagginglocationonphoto.location.LocationProvider
import com.geotagcamera.geotagginglocationonphoto.location.TileMapRepository
import com.geotagcamera.geotagginglocationonphoto.location.WeatherReading
import com.geotagcamera.geotagginglocationonphoto.location.WeatherRepository
import com.geotagcamera.geotagginglocationonphoto.location.ageMs
import com.geotagcamera.geotagginglocationonphoto.security.PhotoIntegrity
import com.geotagcamera.geotagginglocationonphoto.security.ProofClaims
import com.geotagcamera.geotagginglocationonphoto.signature.SignatureOverlay
import com.geotagcamera.geotagginglocationonphoto.stamp.LogoDecoder
import com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor
import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields
import com.geotagcamera.geotagginglocationonphoto.stamp.StampPreferences
import com.geotagcamera.geotagginglocationonphoto.stamp.StampRenderer
import com.geotagcamera.geotagginglocationonphoto.stamp.StampSpec
import com.geotagcamera.geotagginglocationonphoto.stamp.buildStampSpec
import com.geotagcamera.geotagginglocationonphoto.storage.MediaStoreImageSaver
import com.geotagcamera.geotagginglocationonphoto.ui.review.ReviewFilename
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data object Processing : CaptureUiState
    /** [required]: the user turned on "Require signature", so the sheet offers no Skip. */
    data class AwaitingSignature(val required: Boolean) : CaptureUiState
    /** Post-capture review takeover: the saved, signed photo plus its quotable name/size. */
    data class Review(val photoId: Long, val uri: String, val filename: String, val sizeBytes: Long) : CaptureUiState
    data class Error(val message: String) : CaptureUiState
}

/** Drives the viewfinder's location chip. */
sealed interface LocationChipState {
    data object Locating : LocationChipState
    data class Locked(val accuracyMeters: Float?) : LocationChipState
    data object Unavailable : LocationChipState
    /** No location permission: photos can't be geotagged until it's granted. */
    data object PermissionDenied : LocationChipState
}

/**
 * Photo shape. The viewfinder is laid out at this ratio and CameraX crops the
 * saved photo to exactly that frame (a ViewPort), so the photo always matches
 * what was on screen. FULL uses all the room the screen has.
 */
enum class CaptureAspect { RATIO_4_3, RATIO_16_9, RATIO_1_1, FULL }

/**
 * The slow-to-resolve parts of the stamp (address, map tile, weather) for one
 * position. Re-resolved only when the user moves far enough, so the live
 * overlay stays cheap; the map tile here is the exact bitmap threaded into the
 * burn-in, so the saved photo can't show a tile the user never saw.
 */
data class LiveStampData(
    val fix: LocationFix,
    val geocode: AddressParts?,
    val mapTile: Bitmap?,
    val weather: WeatherReading?,
    val resolvedAtEpochMs: Long
)

/**
 * Owns the shutter-to-saved-row pipeline and the live viewfinder state.
 *
 * Location streams while the viewfinder is visible ([trackLocation]), so each
 * photo is stamped with a fix at most [MAX_FIX_AGE_MS] old. The capture time
 * is taken at the shutter press, not after the signature sheet closes.
 */
class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val geocoderRepository = GeocoderRepository(application, AppDatabase.get(application).geoCacheDao())
    private val tileRepository = TileMapRepository(application)
    private val weatherRepository = WeatherRepository()
    private val stampPreferences = StampPreferences(application)
    private val photoDao = AppDatabase.get(application).photoDao()

    val stampFields: StateFlow<StampFields> = stampPreferences.fields
        .stateIn(viewModelScope, SharingStarted.Eagerly, StampFields())

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _locationChip = MutableStateFlow<LocationChipState>(LocationChipState.Locating)
    val locationChip: StateFlow<LocationChipState> = _locationChip.asStateFlow()

    private val _latestFix = MutableStateFlow<LocationFix?>(null)
    private val _liveData = MutableStateFlow<LiveStampData?>(null)

    /** Newest photo in the index: survives restarts and follows deletes made anywhere. */
    val lastCaptureUri: StateFlow<String?> = photoDao.observeLatestPath()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Org logo bitmap, decoded from the path Settings stored. Settings writes
     * every new logo under a new file name, so a path change is a logo change.
     */
    val orgLogo: StateFlow<Bitmap?> = stampFields
        .map { it.orgLogoUri }
        .distinctUntilChanged()
        .map { path ->
            path?.let { withContext(Dispatchers.IO) { LogoDecoder.decode(it) } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val autoDismissReview: StateFlow<Boolean> = stampPreferences.autoDismissReview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Ticks once per minute boundary so the overlay's clock never freezes. */
    private val minuteTicker: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000L - now % 60_000L)
        }
    }

    /**
     * The live overlay spec: the same [StampSpec] type the burn-in draws.
     * Coordinates come from the newest streamed fix; address, tile and
     * weather from the last resolve near it.
     */
    val liveSpec: StateFlow<StampSpec?> =
        combine(stampFields, _liveData, _latestFix, orgLogo, minuteTicker) { fields, data, latest, logo, now ->
            val fix = latest ?: data?.fix ?: return@combine null
            val near = data?.takeIf { distanceMeters(it.fix, fix) <= REUSE_RESOLVE_WITHIN_M }
            runCatching { buildStampSpec(
                fix = fix,
                addressParts = near?.geocode,
                capturedAtEpochMs = now,
                fields = fields,
                mapTile = if (fields.showMap) near?.mapTile else null,
                orgLogo = if (fields.showOrgLogo) logo else null,
                hasSignature = false,
                weatherChipText = near?.weather?.chipText
            ) }.getOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var resolveJob: Job? = null
    private var pending: PendingCapture? = null

    private data class PendingCapture(val file: File, val capturedAtEpochMs: Long, val squareCrop: Boolean)

    init {
        // Turning the map or weather on in Settings should show up on the next
        // look at the viewfinder, not after the user walks 30 m.
        viewModelScope.launch {
            stampFields.map { it.showMap to it.showWeather }
                .distinctUntilChanged()
                .drop(1)
                .collect { _latestFix.value?.let { fix -> resolve(fix, force = true) } }
        }
    }

    /**
     * Streams location for as long as the caller's coroutine is active. Call it
     * from the screen inside `repeatOnLifecycle(STARTED)` so GPS stops the
     * moment the viewfinder isn't visible.
     */
    suspend fun trackLocation(hasPermission: Boolean) {
        if (!hasPermission) {
            _locationChip.value = LocationChipState.PermissionDenied
            return
        }
        fun hasRecentFix() = _latestFix.value?.let { it.ageMs() <= MAX_FIX_AGE_MS } == true
        // A fix from the last visit doesn't count: the chip only says "locked"
        // for a fix the shutter would actually use.
        if (!hasRecentFix()) _locationChip.value = LocationChipState.Locating
        coroutineScope {
            // No usable fix after a while: say so instead of "Locating…" forever.
            val noFixTimeout = launch {
                delay(NO_FIX_TIMEOUT_MS)
                if (!hasRecentFix()) _locationChip.value = LocationChipState.Unavailable
            }
            locationProvider.updates().collect { fix ->
                noFixTimeout.cancel()
                _latestFix.value = fix
                _locationChip.value = LocationChipState.Locked(fix.accuracyMeters)
                resolve(fix, force = false)
            }
        }
    }

    /** Re-resolves address/tile/weather when the user has moved or the data is old. */
    private fun resolve(fix: LocationFix, force: Boolean) {
        val current = _liveData.value
        // A failed lookup (offline start) is retried on the next fix, not in 5 minutes.
        val fresh = current != null && current.geocode != null &&
            distanceMeters(current.fix, fix) <= RERESOLVE_AFTER_M &&
            System.currentTimeMillis() - current.resolvedAtEpochMs <= RERESOLVE_AFTER_MS
        if (!force && (fresh || resolveJob?.isActive == true)) return

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            val fields = stampFields.value
            val geocode = runCatching { geocoderRepository.reverseGeocode(fix.latitude, fix.longitude) }.getOrNull()
            // Publish the address first; tile and weather follow when they land.
            val sameSpot = current?.takeIf { distanceMeters(it.fix, fix) <= REUSE_RESOLVE_WITHIN_M }
            _liveData.value = LiveStampData(fix, geocode, sameSpot?.mapTile, sameSpot?.weather, System.currentTimeMillis())

            val (tile, weather) = coroutineScope {
                val t = async {
                    if (fields.showMap) runCatching { tileRepository.mapThumbnail(fix.latitude, fix.longitude) }.getOrNull()
                    else null
                }
                val w = async {
                    val reusable = sameSpot?.weather?.takeIf {
                        System.currentTimeMillis() - sameSpot.resolvedAtEpochMs <= WEATHER_REUSE_MS
                    }
                    if (!fields.showWeather) null
                    else reusable ?: runCatching { weatherRepository.current(fix.latitude, fix.longitude) }.getOrNull()
                }
                t.await() to w.await()
            }
            _liveData.value = LiveStampData(fix, geocode, tile, weather, System.currentTimeMillis())
        }
    }

    fun updatePosition(anchor: StampAnchor) {
        if (stampFields.value.position == anchor) return
        viewModelScope.launch { stampPreferences.edit { it.copy(position = anchor) } }
    }

    /**
     * [mirror]: front camera. Its preview is mirrored like a mirror, so the
     * saved photo is flipped to match what the user framed (stamp text is
     * drawn afterwards, on the upright image, so it is never mirrored).
     */
    fun capture(imageCapture: ImageCapture, aspect: CaptureAspect, mirror: Boolean = false) {
        if (_uiState.value != CaptureUiState.Idle) return
        val context = getApplication<Application>()
        // The moment the shutter was pressed is the capture time, for the
        // stamp, the EXIF and the signed proof alike.
        val capturedAt = System.currentTimeMillis()
        val file = File(context.cacheDir, "capture_$capturedAt.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = mirror })
            .build()
        val capture = PendingCapture(file, capturedAt, squareCrop = aspect == CaptureAspect.RATIO_1_1)

        _uiState.value = CaptureUiState.Processing
        try {
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val fields = stampFields.value
                        if (fields.requireSignature) {
                            pending = capture
                            _uiState.value = CaptureUiState.AwaitingSignature(required = true)
                        } else {
                            processCapturedPhoto(capture, signature = null)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        _uiState.value = CaptureUiState.Error("Capture failed. Try again.")
                    }
                }
            )
        } catch (e: IllegalStateException) {
            // Camera not bound yet (e.g. a tap during a lens switch).
            _uiState.value = CaptureUiState.Error("Camera isn't ready yet.")
        }
    }

    fun submitSignature(signature: Bitmap?) {
        val capture = pending ?: return
        pending = null
        _uiState.value = CaptureUiState.Processing
        processCapturedPhoto(capture, signature)
    }

    /** User backed out of a required signature: the shot is discarded, nothing saved. */
    fun cancelSignature() {
        pending?.file?.delete()
        pending = null
        _uiState.value = CaptureUiState.Idle
    }

    fun acknowledgeMessage() {
        _uiState.value = CaptureUiState.Idle
    }

    /** Leave the review takeover back to the live viewfinder, keeping the photo. */
    fun dismissReview() {
        _uiState.value = CaptureUiState.Idle
    }

    /**
     * Retake: the photo just saved is discarded (gallery file and index row),
     * then the viewfinder comes back. A photo this app saved moments ago is
     * always still owned by it, so no consent prompt is needed.
     */
    fun retake() {
        val review = _uiState.value as? CaptureUiState.Review ?: return
        _uiState.value = CaptureUiState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = MediaStoreImageSaver.delete(getApplication(), listOf(Uri.parse(review.uri)))
            // Keep the index row if the file couldn't be removed, so it isn't orphaned.
            if (outcome.deleted.isNotEmpty()) photoDao.delete(review.photoId)
        }
    }

    private fun processCapturedPhoto(capture: PendingCapture, signature: Bitmap?) {
        val context = getApplication<Application>()
        val file = capture.file
        val capturedAtEpochMs = capture.capturedAtEpochMs
        viewModelScope.launch {
            try {
                val fix = pickFix(capturedAtEpochMs)
                if (fix == null) {
                    _uiState.value = CaptureUiState.Error(
                        if (_locationChip.value == LocationChipState.PermissionDenied)
                            "Location is off. Allow location to geotag photos."
                        else "Couldn't get a GPS fix. Move to open sky and try again."
                    )
                    return@launch
                }
                // Reuse what the viewfinder showed only if it was resolved for
                // this spot; otherwise geocode the actual capture position.
                val near = _liveData.value?.takeIf { distanceMeters(it.fix, fix) <= REUSE_RESOLVE_WITHIN_M }
                val geocode = near?.geocode
                    ?: runCatching { geocoderRepository.reverseGeocode(fix.latitude, fix.longitude) }.getOrNull()
                val fields = stampPreferences.fields.first()

                val saved = withContext(Dispatchers.IO) {
                    saveStampedPhoto(file, capture.squareCrop, fix, geocode, near, fields, signature, capturedAtEpochMs)
                }
                if (saved == null) {
                    _uiState.value = CaptureUiState.Error("Couldn't save the photo to your gallery.")
                    return@launch
                }
                _uiState.value = saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _uiState.value = CaptureUiState.Error("Not enough memory to process this photo. Close other apps and try again.")
            } catch (e: Exception) {
                _uiState.value = CaptureUiState.Error("Couldn't process the photo. Try again.")
            } finally {
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { file.delete() }
            }
        }
    }

    /** The newest streamed fix if it is recent enough, else a fresh one-shot request. */
    private suspend fun pickFix(capturedAtEpochMs: Long): LocationFix? {
        if (_locationChip.value == LocationChipState.PermissionDenied) return null
        val latest = _latestFix.value
        if (latest != null && latest.ageMs(capturedAtEpochMs) <= MAX_FIX_AGE_MS) return latest
        return locationProvider.getFreshFix()
    }

    private suspend fun saveStampedPhoto(
        file: File,
        squareCrop: Boolean,
        fix: LocationFix,
        geocode: AddressParts?,
        near: LiveStampData?,
        fields: StampFields,
        signature: Bitmap?,
        capturedAtEpochMs: Long
    ): CaptureUiState.Review? {
        val context = getApplication<Application>()
        var image = loadUprightBitmap(file) ?: return null
        if (squareCrop) image = centerSquare(image)

        // Both draws go into the same bitmap when it is mutable: no extra
        // full-resolution copies (the old pipeline held two at once).
        image = StampRenderer.stamp(
            context = context,
            source = image,
            fix = fix,
            addressParts = geocode,
            capturedAtEpochMs = capturedAtEpochMs,
            fields = fields,
            mapTile = if (fields.showMap) near?.mapTile else null,
            orgLogo = if (fields.showOrgLogo) orgLogo.value else null,
            hasSignature = signature != null,
            weatherChipText = if (fields.showWeather) near?.weather?.chipText else null
        ).also { if (it !== image) image.recycle() }
        if (signature != null) {
            image = SignatureOverlay.apply(image, signature).also { if (it !== image) image.recycle() }
            signature.recycle()
        }

        FileOutputStream(file).use { out -> image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        image.recycle()

        // Sign the canonical image first — the hash is blind to metadata (see
        // JpegCanonical), so embedding the proof below can't invalidate it —
        // then carry the proof in EXIF UserComment and mirror it in XMP so any
        // device can verify this photo, not just the one that shot it.
        val integrity = PhotoIntegrity.sign(file, ProofClaims(fix.latitude, fix.longitude, capturedAtEpochMs))
        val proof = UserCommentCodec.encode(integrity, capturedAtEpochMs)
        ExifWriter.write(file, fix, capturedAtEpochMs, proof)
        XmpWriter.write(file, proof)

        val filename = ReviewFilename.generate(geocode?.place, capturedAtEpochMs)
        val sizeBytes = file.length()
        val uri = MediaStoreImageSaver.save(context, file, filename) ?: return null
        val id = photoDao.insert(
            PhotoEntity(
                filePath = uri.toString(),
                capturedAtEpochMs = capturedAtEpochMs,
                latitude = fix.latitude,
                longitude = fix.longitude,
                altitudeMeters = fix.altitudeMeters,
                accuracyMeters = fix.accuracyMeters,
                bearingDegrees = fix.bearingDegrees,
                address = geocode?.addressLine,
                addressFromCache = geocode?.fromCache ?: false,
                orgLabel = fields.orgLabel.ifBlank { null },
                sha256Hash = integrity.sha256Hex,
                signatureBase64 = integrity.signatureBase64,
                signingKeyAlias = integrity.keyAlias,
                fieldWorkerSignature = signature != null
            )
        )
        return CaptureUiState.Review(id, uri.toString(), filename, sizeBytes)
    }

    override fun onCleared() {
        pending?.file?.delete()
        super.onCleared()
    }

    private companion object {
        /** A streamed fix older than this at shutter time is not trusted; a fresh one is requested. */
        const val MAX_FIX_AGE_MS = 30_000L
        const val NO_FIX_TIMEOUT_MS = 15_000L
        const val RERESOLVE_AFTER_M = 30f
        const val RERESOLVE_AFTER_MS = 5 * 60_000L
        /** Address/tile resolved within this distance of the capture point still describe it. */
        const val REUSE_RESOLVE_WITHIN_M = 50f
        const val WEATHER_REUSE_MS = 10 * 60_000L
        const val JPEG_QUALITY = 92

        fun distanceMeters(a: LocationFix, b: LocationFix): Float {
            val out = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
            return out[0]
        }

        /**
         * Center-crops to a square, a fallback for when the camera's own crop
         * didn't (CameraX's ViewPort normally delivers 1:1 already). Draws into
         * a new mutable bitmap so the stamp can draw in place afterwards.
         */
        fun centerSquare(bitmap: Bitmap): Bitmap {
            if (bitmap.width == bitmap.height) return bitmap
            val side = minOf(bitmap.width, bitmap.height)
            val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(out).drawBitmap(
                bitmap, -((bitmap.width - side) / 2f), -((bitmap.height - side) / 2f), null
            )
            bitmap.recycle()
            return out
        }

        /** Decodes mutable (so the stamp draws in place) and applies the EXIF orientation. */
        fun loadUprightBitmap(file: File): Bitmap? {
            val orientation = ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
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

            // Rotate into a new MUTABLE bitmap (Bitmap.createBitmap(src, matrix)
            // returns an immutable one, which would force a third full-size copy
            // when the stamp is drawn).
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
