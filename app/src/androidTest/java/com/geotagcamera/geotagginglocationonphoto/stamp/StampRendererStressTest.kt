package com.geotagcamera.geotagginglocationonphoto.stamp

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Burns every template x anchor x field combination into frames from tiny to
 * full-sensor size, with and without map tile, logo and signature, and with
 * absurdly long text. Any exception from StampPainter fails the test; this is
 * the code path that runs on every style change and every capture.
 */
@RunWith(AndroidJUnit4::class)
class StampRendererStressTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fix = LocationFix(22.5726, 88.3639, 11.4, 4.6f, 135f, 0L)
    private val shortAddress = AddressParts("Kolkata, West Bengal", "BBD Bagh, Kolkata, 700001", "India", "IN", false)
    private val longAddress = AddressParts(
        "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch ".repeat(3),
        "A very long street name that keeps going, ".repeat(12),
        "United Kingdom", "GB", true
    )
    private val sizes = listOf(1 to 1, 40 to 30, 300 to 400, 1080 to 1440, 4032 to 3024, 3024 to 4032)

    private fun solid(w: Int, h: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }

    @Test
    fun everyTemplateAnchorAndSizeRendersWithoutThrowing() {
        val tile = solid(512, 512)
        val logo = solid(300, 120)
        var rendered = 0
        for (template in StampTemplate.entries) for (anchor in StampAnchor.entries) for ((w, h) in sizes) {
            for (address in listOf(shortAddress, longAddress, null)) {
                val fields = StampFields(
                    template = template, position = anchor, showMap = true, showAltitude = true,
                    showBearing = true, showWeather = true, orgLabel = "Org ".repeat(20)
                )
                val out = StampRenderer.stamp(
                    context, solid(w, h), fix, address, 1_790_000_000_000L, fields,
                    mapTile = tile, orgLogo = logo, hasSignature = true, weatherChipText = "31°C CLEAR"
                )
                assertTrue(out.width == w && out.height == h)
                out.recycle()
                rendered++
            }
        }
        assertTrue(rendered > 0)
    }

    @Test
    fun everyFieldOffStillRenders() {
        val none = StampFields(
            showMap = false, showCountry = false, showAddress = false, showCoordinates = false,
            showTimestamp = false, showGmtOffset = false, showAccuracy = false, showOrgLabelToggle = false,
            showOrgLogo = false, showSignatureField = false, showBrandMark = false
        )
        for (template in StampTemplate.entries) for ((w, h) in sizes) {
            StampRenderer.stamp(context, solid(w, h), fix, null, 0L, none.copy(template = template)).recycle()
        }
    }
}
