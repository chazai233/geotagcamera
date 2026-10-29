package com.geotagcamera.geotagginglocationonphoto.location.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.pow

/** One downloaded (or downloading) map area. */
data class OfflineArea(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Int,
    val createdAtEpochMs: Long,
    val completedBytes: Long = 0,
    val completedCount: Long = 0,
    val requiredCount: Long = 0,
    val complete: Boolean = false,
    val downloading: Boolean = false,
    val error: String? = null
) {
    val progress: Float get() = if (requiredCount <= 0) 0f else (completedCount.toFloat() / requiredCount).coerceIn(0f, 1f)
}

/**
 * Areas saved for offline use, so the stamp map works with no signal.
 *
 * Wraps MapLibre's offline database: an area is a circle (centre + radius)
 * downloaded as OpenFreeMap vector tiles from zoom [MIN_ZOOM] to [MAX_ZOOM]
 * (the source's own maximum; closer views over-zoom those tiles). MapLibre
 * calls back on the main thread, so every call here hops there first.
 */
object OfflineMaps {

    const val MIN_ZOOM = 6.0
    const val MAX_ZOOM = 14.0
    val RADIUS_OPTIONS_KM = listOf(2, 5, 15, 30)

    private val _areas = MutableStateFlow<List<OfflineArea>>(emptyList())
    val areas: StateFlow<List<OfflineArea>> = _areas.asStateFlow()

    private val regions = mutableMapOf<Long, OfflineRegion>()
    private var loaded = false

    /** Loads the saved list once and resumes any download that was cut off. */
    suspend fun load(context: Context) = withContext(Dispatchers.Main) {
        if (loaded) return@withContext
        MapEngine.ensureInit(context)
        val list = suspendCancellableCoroutine<Array<OfflineRegion>?> { cont ->
            OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) { if (cont.isActive) cont.resume(offlineRegions ?: emptyArray()) }
                override fun onError(error: String) { if (cont.isActive) cont.resume(null) }
            })
        } ?: return@withContext
        loaded = true
        _areas.value = list.mapNotNull { region -> parse(region)?.also { regions[region.id] = region } }
        list.forEach { region -> observe(region); refreshStatus(region) }
    }

    /** Starts downloading a circle around the point. Returns an error message, or null on success. */
    suspend fun download(context: Context, name: String, latitude: Double, longitude: Double, radiusKm: Int): String? =
        withContext(Dispatchers.Main) {
            MapEngine.ensureInit(context)
            val bounds = boundsAround(latitude, longitude, radiusKm)
            val definition = OfflineTilePyramidRegionDefinition(
                MapEngine.STYLE_URL, bounds, MIN_ZOOM, MAX_ZOOM, context.resources.displayMetrics.density
            )
            val created = System.currentTimeMillis()
            val meta = JSONObject()
                .put("name", name).put("lat", latitude).put("lon", longitude)
                .put("radiusKm", radiusKm).put("created", created)
                .toString().toByteArray()
            var failure: String? = null
            val region = suspendCancellableCoroutine<OfflineRegion?> { cont ->
                OfflineManager.getInstance(context).createOfflineRegion(definition, meta, object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) { if (cont.isActive) cont.resume(offlineRegion) }
                    override fun onError(error: String) { failure = error; if (cont.isActive) cont.resume(null) }
                })
            } ?: return@withContext (failure ?: "无法创建地图区域")
            regions[region.id] = region
            _areas.update { it + OfflineArea(region.id, name, latitude, longitude, radiusKm, created, downloading = true) }
            observe(region)
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            null
        }

    suspend fun pause(id: Long) = withContext(Dispatchers.Main) {
        regions[id]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        patch(id) { it.copy(downloading = false) }
    }

    suspend fun resume(id: Long) = withContext(Dispatchers.Main) {
        regions[id]?.setDownloadState(OfflineRegion.STATE_ACTIVE)
        patch(id) { it.copy(downloading = true, error = null) }
    }

    /** Deletes an area. Tiles shared with another saved area stay. */
    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.Main) {
        val region = regions[id] ?: return@withContext false
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        val ok = suspendCancellableCoroutine<Boolean> { cont ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() { if (cont.isActive) cont.resume(true) }
                override fun onError(error: String) { if (cont.isActive) cont.resume(false) }
            })
        }
        if (ok) {
            regions.remove(id)
            _areas.update { list -> list.filterNot { it.id == id } }
        }
        ok
    }

    /** True when a saved, fully downloaded area covers the point. */
    fun covers(latitude: Double, longitude: Double): Boolean = _areas.value.any { area ->
        area.complete && distanceKm(area.latitude, area.longitude, latitude, longitude) <= area.radiusKm
    }

    /**
     * Rough tile count and size for a circle, shown before downloading.
     * Measured: about 90 KB per tile including style, fonts and sprites.
     */
    fun estimate(latitude: Double, radiusKm: Int): Pair<Long, Long> {
        var tiles = 0L
        var z = MIN_ZOOM.toInt()
        while (z <= MAX_ZOOM.toInt()) {
            val tileKm = 40075.0 * cos(Math.toRadians(latitude)) / 2.0.pow(z)
            val across = (radiusKm * 2 / tileKm).toLong() + 2
            tiles += across * across
            z++
        }
        return tiles to tiles * 90_000L
    }

    private fun observe(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                patch(region.id) { apply(it, status) }
                if (status.isComplete) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
            override fun onError(error: OfflineRegionError) {
                val message = when (error.reason) {
                    OfflineRegionError.REASON_CONNECTION -> "无网络。恢复联网后会继续下载。"
                    OfflineRegionError.REASON_SERVER -> "地图服务器繁忙，正在重试。"
                    else -> "下载出错：${error.message}"
                }
                patch(region.id) { it.copy(error = message) }
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                patch(region.id) { it.copy(downloading = false, error = "区域太大（最多 $limit 个瓦片），请选小一点的半径。") }
            }
        })
    }

    private fun refreshStatus(region: OfflineRegion) {
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null) return
                patch(region.id) { apply(it, status) }
                // Resume an interrupted download (app was closed mid-way).
                if (!status.isComplete && status.completedResourceCount > 0) {
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    patch(region.id) { it.copy(downloading = true) }
                }
            }
            override fun onError(error: String?) = Unit
        })
    }

    private fun apply(area: OfflineArea, status: OfflineRegionStatus) = area.copy(
        completedBytes = status.completedResourceSize,
        completedCount = status.completedResourceCount,
        requiredCount = status.requiredResourceCount,
        complete = status.isComplete,
        downloading = !status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE,
        error = if (status.isComplete) null else area.error
    )

    private fun patch(id: Long, change: (OfflineArea) -> OfflineArea) {
        _areas.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private fun parse(region: OfflineRegion): OfflineArea? = runCatching {
        val json = JSONObject(String(region.metadata))
        OfflineArea(
            id = region.id,
            name = json.optString("name", "已保存区域"),
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
            radiusKm = json.optInt("radiusKm", 5),
            createdAtEpochMs = json.optLong("created", 0L)
        )
    }.getOrNull()

    private fun boundsAround(lat: Double, lon: Double, radiusKm: Int): LatLngBounds {
        val dLat = radiusKm / 111.32
        val dLon = radiusKm / (111.32 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return LatLngBounds.Builder()
            .include(LatLng((lat - dLat).coerceAtLeast(-85.0), lon - dLon))
            .include(LatLng((lat + dLat).coerceAtMost(85.0), lon + dLon))
            .build()
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * kotlin.math.sin(dLon / 2).pow(2)
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
    }

}
