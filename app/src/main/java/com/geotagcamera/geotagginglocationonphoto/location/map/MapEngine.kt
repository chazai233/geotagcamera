package com.geotagcamera.geotagginglocationonphoto.location.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume

/**
 * The stamp's map thumbnail, rendered on the phone itself.
 *
 * Tiles are OpenStreetMap vector tiles from OpenFreeMap (no API key, no
 * request limits, offline use allowed). MapLibre draws them locally, so one
 * engine covers three cases with no extra code:
 *  - online: tiles stream in and land in MapLibre's on-disk cache;
 *  - offline, area seen before: the cache answers;
 *  - offline, area downloaded in Offline maps: the offline database answers.
 *
 * Nothing here can block a capture. Every failure (no network and no cache,
 * style error, timeout, blank render) returns null and the stamp is simply
 * drawn without a map.
 */
class MapEngine(context: Context) {

    private val appContext = context.applicationContext

    /**
     * @return a square map image centred on the point with a pin, or null.
     */
    suspend fun thumbnail(latitude: Double, longitude: Double, sizePx: Int = 480, zoom: Double = 15.0): Bitmap? =
        withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
            val raw = withContext(Dispatchers.Main) { snapshot(latitude, longitude, sizePx, zoom) } ?: return@withTimeoutOrNull null
            withContext(Dispatchers.Default) {
                // Inside a downloaded area the tiles are known to be there; sparse
                // farmland can look "blank" to the heuristic, so trust the area.
                if (!OfflineMaps.covers(latitude, longitude) && looksBlank(raw)) { android.util.Log.i(TAG, "snapshot blank (no tiles cached)"); raw.recycle(); null }
                else decorate(raw)
            }
        }

    private suspend fun snapshot(lat: Double, lon: Double, sizePx: Int, zoom: Double): Bitmap? {
        ensureInit(appContext)
        // The snapshotter works in dp at the given pixel ratio; render at 2x for crisp roads.
        val ratio = 2f
        val sideDp = (sizePx / ratio).toInt().coerceAtLeast(64)
        val options = MapSnapshotter.Options(sideDp, sideDp)
            .withStyle(STYLE_URL)
            .withPixelRatio(ratio)
            .withCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).build())
            .withLogo(false)
            .withAttribution(false)
        val snapshotter = MapSnapshotter(appContext, options)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { runCatching { snapshotter.cancel() } }
            runCatching {
                snapshotter.start(
                    { snap -> if (cont.isActive) cont.resume(snap.bitmap.copy(Bitmap.Config.ARGB_8888, true)) },
                    { error -> android.util.Log.w(TAG, "snapshot failed: $error"); if (cont.isActive) cont.resume(null) }
                )
            }.onFailure { android.util.Log.w(TAG, "snapshot crashed", it); if (cont.isActive) cont.resume(null) }
        }
    }

    /**
     * Overview map for reports: every point as a numbered pin (1-based, in list
     * order), framed to fit them all. Null when the map can't be drawn.
     */
    suspend fun overview(points: List<Pair<Double, Double>>, widthPx: Int, heightPx: Int): Bitmap? {
        if (points.isEmpty()) return null
        return withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                ensureInit(appContext)
                val ratio = 2f
                val wDp = (widthPx / ratio).toInt().coerceAtLeast(64)
                val hDp = (heightPx / ratio).toInt().coerceAtLeast(64)
                val options = MapSnapshotter.Options(wDp, hDp)
                    .withStyle(STYLE_URL)
                    .withPixelRatio(ratio)
                    .withLogo(false)
                    .withAttribution(false)
                if (points.size == 1 || points.all { it == points.first() }) {
                    options.withCameraPosition(CameraPosition.Builder().target(LatLng(points[0].first, points[0].second)).zoom(15.0).build())
                } else {
                    val bounds = org.maplibre.android.geometry.LatLngBounds.Builder().apply {
                        points.forEach { include(LatLng(it.first, it.second)) }
                    }.build()
                    val pad = (minOf(wDp, hDp) * 0.12f).toInt()
                    options.withRegion(bounds).withPadding(pad, pad, pad, pad)
                }
                val snapshotter = MapSnapshotter(appContext, options)
                suspendCancellableCoroutine<Bitmap?> { cont ->
                    cont.invokeOnCancellation { runCatching { snapshotter.cancel() } }
                    runCatching {
                        snapshotter.start(
                            { snap ->
                                val bmp = snap.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                                val pins = points.map { (lat, lon) -> snap.pixelForLatLng(LatLng(lat, lon)) }
                                if (cont.isActive) cont.resume(drawNumberedPins(bmp, pins.map { it.x * 1f to it.y * 1f }))
                            },
                            { error -> android.util.Log.w(TAG, "overview failed: $error"); if (cont.isActive) cont.resume(null) }
                        )
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
        }
    }

    private fun drawNumberedPins(bmp: Bitmap, pins: List<Pair<Float, Float>>): Bitmap {
        val canvas = Canvas(bmp)
        val r = minOf(bmp.width, bmp.height) * 0.028f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PIN_COLOR }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = r * 0.22f }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = r * 1.05f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        // Draw in reverse so pin 1 sits on top where pins overlap.
        pins.withIndex().reversed().forEach { (i, p) ->
            canvas.drawCircle(p.first, p.second, r, fill)
            canvas.drawCircle(p.first, p.second, r, ring)
            canvas.drawText((i + 1).toString(), p.first, p.second + text.textSize * 0.36f, text)
        }
        val label = "© OpenStreetMap contributors, OpenFreeMap"
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 40, 40, 40); textSize = bmp.height * 0.03f }
        val tw = small.measureText(label)
        val pad = small.textSize * 0.4f
        canvas.drawRect(bmp.width - tw - pad * 2, bmp.height - small.textSize - pad * 2, bmp.width.toFloat(), bmp.height.toFloat(), Paint().apply { color = Color.argb(170, 255, 255, 255) })
        canvas.drawText(label, bmp.width - tw - pad, bmp.height - pad * 1.4f, small)
        return bmp
    }

    /**
     * A render with no tiles (offline, never cached) is the style's flat
     * background colour. Burning that in would be a lie, so treat it as no map.
     */
    private fun looksBlank(bmp: Bitmap): Boolean {
        val step = (minOf(bmp.width, bmp.height) / 16).coerceAtLeast(1)
        val first = bmp.getPixel(0, 0)
        var distinct = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                if (colorDistance(bmp.getPixel(x, y), first) > 24) distinct++
                x += step
            }
            y += step
        }
        android.util.Log.d(TAG, "blank check: $distinct distinct samples")
        // A tile-less render is the flat background colour: zero differing samples.
        // Real maps, even empty farmland, have at least a road or field edge.
        return distinct <= 1
    }

    private fun colorDistance(a: Int, b: Int): Int =
        maxOf(kotlin.math.abs(Color.red(a) - Color.red(b)), kotlin.math.abs(Color.green(a) - Color.green(b)), kotlin.math.abs(Color.blue(a) - Color.blue(b)))

    /** Centre pin plus the OSM attribution the licence asks for. */
    private fun decorate(bmp: Bitmap): Bitmap {
        val canvas = Canvas(bmp)
        val w = bmp.width.toFloat()
        val cx = w / 2f
        val cy = bmp.height / 2f
        val r = w * 0.055f

        // Teardrop pin whose tip sits exactly on the capture point.
        val pin = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - r * 0.35f, cy - r * 0.9f, cx - r, cy - r * 1.3f, cx - r, cy - r * 2.0f)
            arcTo(cx - r, cy - r * 3.0f, cx + r, cy - r * 1.0f, 180f, 180f, false)
            cubicTo(cx + r, cy - r * 1.3f, cx + r * 0.35f, cy - r * 0.9f, cx, cy)
            close()
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
        canvas.drawOval(cx - r * 0.6f, cy - r * 0.18f, cx + r * 0.6f, cy + r * 0.18f, shadow)
        canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PIN_COLOR })
        canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = r * 0.16f })
        canvas.drawCircle(cx, cy - r * 2.0f, r * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 40, 40, 40)
            textSize = w * 0.05f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val label = "© OpenStreetMap"
        val tw = textPaint.measureText(label)
        val pad = w * 0.015f
        canvas.drawRect(w - tw - pad * 3, bmp.height - textPaint.textSize - pad * 2, w, bmp.height.toFloat(), Paint().apply { color = Color.argb(150, 255, 255, 255) })
        canvas.drawText(label, w - tw - pad * 1.5f, bmp.height - pad * 1.4f, textPaint)
        return bmp
    }

    companion object {
        /** OpenFreeMap "liberty": a full-colour OSM style that reads well at thumbnail size. */
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles, data © OpenStreetMap contributors"

        private const val TAG = "MapEngine"
        private const val SNAPSHOT_TIMEOUT_MS = 30_000L
        private const val AMBIENT_CACHE_BYTES = 150L * 1024 * 1024
        private val PIN_COLOR = Color.rgb(0xE5, 0x39, 0x35)

        private object NoOpCallback : OfflineManager.FileSourceCallback {
            override fun onSuccess() = Unit
            override fun onError(message: String) = Unit
        }

        @Volatile private var initialised = false

        /** MapLibre must be initialised on the main thread before any map call. */
        fun ensureInit(context: Context) {
            if (initialised) return
            synchronized(this) {
                if (initialised) return
                MapLibre.getInstance(context.applicationContext)
                // Larger cache: every area you shoot in stays available offline for a while.
                OfflineManager.getInstance(context.applicationContext).setMaximumAmbientCacheSize(AMBIENT_CACHE_BYTES, NoOpCallback)
                initialised = true
            }
        }
    }
}
