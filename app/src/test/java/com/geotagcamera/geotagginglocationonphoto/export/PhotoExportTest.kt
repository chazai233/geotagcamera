package com.geotagcamera.geotagginglocationonphoto.export

import com.geotagcamera.geotagginglocationonphoto.data.PhotoEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoExportTest {

    private fun photo(id: Long, note: String? = null, place: String? = "Champadanga, West Bengal", project: Long? = 7) = PhotoEntity(
        id = id, filePath = "content://media/$id", capturedAtEpochMs = 1_760_000_000_000L,
        latitude = 22.834455, longitude = 87.970336, altitudeMeters = 20.0, accuracyMeters = 5f, bearingDegrees = 90f,
        address = "7MJ9RXMC+Q4, Champadanga, West Bengal, 712401", addressFromCache = false, orgLabel = "ACME, Survey",
        sha256Hash = "ab".repeat(32), signatureBase64 = "sig", signingKeyAlias = "k", fieldWorkerSignature = true,
        projectId = project, note = note, placeName = place
    )

    private val names = mapOf(7L to "Bridge 14")

    @Test
    fun csv_quotesCommasAndQuotes_andHasBom() {
        val out = PhotoExport.csv(listOf(photo(1, note = "Crack \"A\", north")), names)
        assertTrue(out.startsWith("﻿id,captured_at,latitude"))
        assertTrue(out.contains("\"Crack \"\"A\"\", north\""))
        assertTrue(out.contains(",22.834455,87.970336,"))
        assertTrue(out.contains(",Bridge 14,"))
        assertTrue(out.contains("\"ACME, Survey\""))
    }

    @Test
    fun csv_neutralisesFormulaInjection() {
        assertEquals("'=HYPERLINK(1)", PhotoExport.csvCell("=HYPERLINK(1)"))
        assertEquals("-12.5", PhotoExport.csvCell("-12.5"))
    }

    @Test
    fun kml_escapesXml_andUsesLonLatOrder() {
        val out = PhotoExport.kml(listOf(photo(1, note = "<b>&</b>")), names, "Bridge & road")
        assertTrue(out.contains("<name>Bridge &amp; road</name>"))
        assertTrue(out.contains("<name>&lt;b&gt;&amp;&lt;/b&gt;</name>"))
        assertTrue(out.contains("<coordinates>87.970336,22.834455,20.0</coordinates>"))
        assertFalse(out.contains("<b>"))
    }

    @Test
    fun geoJson_isValidFeatureCollection() {
        val json = JSONObject(PhotoExport.geoJson(listOf(photo(1), photo(2, project = null)), names, "Bridge 14"))
        assertEquals("FeatureCollection", json.getString("type"))
        val features = json.getJSONArray("features")
        assertEquals(2, features.length())
        val first = features.getJSONObject(0)
        val coords = first.getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(87.970336, coords.getDouble(0), 1e-9)
        assertEquals(22.834455, coords.getDouble(1), 1e-9)
        assertEquals("Bridge 14", first.getJSONObject("properties").getString("project"))
        assertFalse(features.getJSONObject(1).getJSONObject("properties").has("project"))
    }
}
