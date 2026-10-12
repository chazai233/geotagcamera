package com.geotagcamera.geotagginglocationonphoto.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectors from the Open Location Code reference test data (encoding.csv, code length 10). */
class PlusCodeTest {

    @Test
    fun `reference vectors`() {
        assertEquals("7FG49QCJ+2V", PlusCode.encode(20.3700625, 2.7821875))
        assertEquals("8FVC2222+22", PlusCode.encode(47.0000625, 8.0000625))
        assertEquals("4VCPPQGP+Q9", PlusCode.encode(-41.2730625, 174.7859375))
    }

    @Test
    fun `poles and antimeridian stay well formed`() {
        val shape = Regex("^[23456789CFGHJMPQRVWX]{8}\\+[23456789CFGHJMPQRVWX]{2}$")
        listOf(90.0 to 0.0, -90.0 to 0.0, 0.0 to 180.0, 0.0 to -180.0, 89.9999 to 179.9999).forEach { (lat, lng) ->
            val code = PlusCode.encode(lat, lng)
            assertTrue("$lat,$lng -> $code", shape.matches(code))
        }
    }
}
