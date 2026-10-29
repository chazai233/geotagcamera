package com.geotagcamera.geotagginglocationonphoto.location

import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** One current-weather reading, ready to become a stamp chip like "31°C CLEAR". */
data class WeatherReading(val temperatureC: Double, val weatherCode: Int) {
    val chipText: String
        get() = if (temperatureC.isFinite()) "${temperatureC.roundToInt()}°C ${describe(weatherCode)}" else describe(weatherCode)
}

/**
 * Current weather for the capture point, via Open-Meteo.
 *
 * Open-Meteo is free, keyless, and not Google — same OSM-not-Google posture as
 * the map tiles. Plain [HttpURLConnection] (no new HTTP-client dependency).
 * Strictly opt-in: only called when `StampFields.showWeather` is on, which
 * defaults false. Any failure (offline, timeout, malformed response) returns
 * null and the weather chip simply doesn't render — never a blocked capture.
 * Requires INTERNET (see AndroidManifest).
 */
class WeatherRepository {

    suspend fun current(latitude: Double, longitude: Double): WeatherReading? =
        withContext(Dispatchers.IO) {
            // Two decimals is ~1 km: plenty for current weather, and Open-Meteo
            // never learns the exact spot the photo was taken.
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${"%.2f".format(Locale.US, latitude)}" +
                "&longitude=${"%.2f".format(Locale.US, longitude)}" +
                "&current=temperature_2m,weather_code"
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    setRequestProperty("User-Agent", "GeoTagCamera/${BuildConfig.VERSION_NAME}")
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
                    val current = JSONObject(body).getJSONObject("current")
                    WeatherReading(
                        temperatureC = current.getDouble("temperature_2m"),
                        weatherCode = current.getInt("weather_code")
                    )
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
}

/** WMO weather-interpretation code → short uppercase label for the stamp chip. */
private fun describe(code: Int): String = when (code) {
    0 -> "晴"
    1, 2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    51, 53, 55, 56, 57 -> "毛毛雨"
    61, 63, 65, 66, 67 -> "雨"
    71, 73, 75, 77 -> "雪"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95 -> "雷阵雨"
    96, 99 -> "雷阵雨伴冰雹"
    else -> "—"
}
