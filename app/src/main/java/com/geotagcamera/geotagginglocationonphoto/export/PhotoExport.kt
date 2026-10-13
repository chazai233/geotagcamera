package com.geotagcamera.geotagginglocationonphoto.export

import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Formats the user can pick when exporting photo locations. */
enum class ExportFormat(val label: String, val extension: String, val mimeType: String, val hint: String) {
    CSV("CSV", "csv", "text/csv", "Spreadsheets: Excel, Google Sheets, LibreOffice"),
    KML("KML", "kml", "application/vnd.google-earth.kml+xml", "Google Earth, Google My Maps"),
    GEOJSON("GeoJSON", "geojson", "application/geo+json", "QGIS, ArcGIS, web maps")
}

/**
 * Turns photo records into CSV, KML or GeoJSON. Pure functions over
 * [PhotoEntity], so they are unit tested without a device.
 *
 * Every format carries the same facts: time (ISO 8601 with offset), position,
 * altitude/accuracy/bearing, place, address, note, project, and the SHA-256
 * and key alias that let anyone tie a row back to a signed photo.
 */
object PhotoExport {

    fun render(format: ExportFormat, photos: List<PhotoEntity>, projectNames: Map<Long, String>, title: String): String = when (format) {
        ExportFormat.CSV -> csv(photos, projectNames)
        ExportFormat.KML -> kml(photos, projectNames, title)
        ExportFormat.GEOJSON -> geoJson(photos, projectNames, title)
    }

    private val HEADER = listOf(
        "id", "captured_at", "latitude", "longitude", "altitude_m", "accuracy_m", "bearing_deg",
        "place", "address", "note", "project", "organisation", "field_worker_signature", "sha256", "signing_key", "file"
    )

    fun csv(photos: List<PhotoEntity>, projectNames: Map<Long, String>): String = buildString {
        // BOM so Excel opens UTF-8 (Bengali, Hindi place names) correctly.
        append('﻿')
        appendLine(HEADER.joinToString(","))
        photos.forEach { p ->
            val row = listOf(
                p.id.toString(), iso(p.capturedAtEpochMs), coord(p.latitude), coord(p.longitude),
                p.altitudeMeters?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                p.accuracyMeters?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                p.bearingDegrees?.let { "%.0f".format(Locale.US, it) }.orEmpty(),
                p.placeName.orEmpty(), p.address.orEmpty(), p.note.orEmpty(),
                p.projectId?.let { projectNames[it] }.orEmpty(), p.orgLabel.orEmpty(),
                if (p.fieldWorkerSignature) "yes" else "no",
                p.sha256Hash, p.signingKeyAlias, p.filePath
            )
            appendLine(row.joinToString(",") { csvCell(it) })
        }
    }

    fun kml(photos: List<PhotoEntity>, projectNames: Map<Long, String>, title: String): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        appendLine("<Document>")
        appendLine("  <name>${xml(title)}</name>")
        photos.forEach { p ->
            appendLine("  <Placemark>")
            appendLine("    <name>${xml(p.note ?: p.placeName ?: shortTime(p.capturedAtEpochMs))}</name>")
            appendLine("    <TimeStamp><when>${iso(p.capturedAtEpochMs)}</when></TimeStamp>")
            val lines = listOfNotNull(
                p.placeName, p.address, p.note?.let { "Note: $it" },
                p.projectId?.let { projectNames[it] }?.let { "Project: $it" },
                "Taken: ${iso(p.capturedAtEpochMs)}",
                p.accuracyMeters?.let { "Accuracy: ±%.0f m".format(Locale.US, it) },
                "SHA-256: ${p.sha256Hash}"
            )
            appendLine("    <description>${xml(lines.joinToString("\n"))}</description>")
            appendLine("    <ExtendedData>")
            extended(p, projectNames).forEach { (k, v) -> appendLine("      <Data name=\"${xml(k)}\"><value>${xml(v)}</value></Data>") }
            appendLine("    </ExtendedData>")
            val alt = p.altitudeMeters?.let { ",%.1f".format(Locale.US, it) }.orEmpty()
            appendLine("    <Point><coordinates>${coord(p.longitude)},${coord(p.latitude)}$alt</coordinates></Point>")
            appendLine("  </Placemark>")
        }
        appendLine("</Document>")
        appendLine("</kml>")
    }

    fun geoJson(photos: List<PhotoEntity>, projectNames: Map<Long, String>, title: String): String {
        val features = JSONArray()
        photos.forEach { p ->
            val coords = JSONArray().put(round6(p.longitude)).put(round6(p.latitude))
            p.altitudeMeters?.let { coords.put(Math.round(it * 10) / 10.0) }
            val props = JSONObject()
            extended(p, projectNames).forEach { (k, v) -> props.put(k, v) }
            props.put("id", p.id)
            p.accuracyMeters?.let { props.put("accuracy_m", Math.round(it * 10) / 10.0) }
            p.bearingDegrees?.let { props.put("bearing_deg", Math.round(it).toInt()) }
            features.put(
                JSONObject()
                    .put("type", "Feature")
                    .put("geometry", JSONObject().put("type", "Point").put("coordinates", coords))
                    .put("properties", props)
            )
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("name", title)
            .put("features", features)
            .toString(2)
    }

    /** Text facts shared by KML ExtendedData and GeoJSON properties; empty values are left out. */
    private fun extended(p: PhotoEntity, projectNames: Map<Long, String>): List<Pair<String, String>> = listOfNotNull(
        "captured_at" to iso(p.capturedAtEpochMs),
        p.placeName?.let { "place" to it },
        p.address?.let { "address" to it },
        p.note?.let { "note" to it },
        p.projectId?.let { projectNames[it] }?.let { "project" to it },
        p.orgLabel?.let { "organisation" to it },
        "field_worker_signature" to if (p.fieldWorkerSignature) "yes" else "no",
        "sha256" to p.sha256Hash,
        "signing_key" to p.signingKeyAlias
    )

    fun iso(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(epochMs))

    private fun shortTime(epochMs: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))

    private fun coord(v: Double) = "%.6f".format(Locale.US, v)
    private fun round6(v: Double) = Math.round(v * 1_000_000) / 1_000_000.0

    /** RFC 4180: quote when needed, double inner quotes. Leading = + - @ are prefixed to stop spreadsheet formula injection. */
    fun csvCell(raw: String): String {
        val v = if (raw.isNotEmpty() && raw[0] in "=+-@" && raw.toDoubleOrNull() == null) "'$raw" else raw
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    fun xml(v: String): String = buildString(v.length) {
        v.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c >= ' ' || c == '\n' || c == '\t') append(c)
            }
        }
    }
}
