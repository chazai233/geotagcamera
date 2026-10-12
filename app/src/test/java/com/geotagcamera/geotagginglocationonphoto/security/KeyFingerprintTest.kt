package com.geotagcamera.geotagginglocationonphoto.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class KeyFingerprintTest {

    private fun b64(vararg bytes: Int) = Base64.getEncoder().encodeToString(ByteArray(bytes.size) { bytes[it].toByte() })

    @Test
    fun `format is four groups of four uppercase hex digits`() {
        val fp = KeyFingerprint.of(b64(1, 2, 3, 4))!!
        assertTrue(fp, Regex("^[0-9A-F]{4}( [0-9A-F]{4}){3}$").matches(fp))
    }

    @Test
    fun `same key gives the same fingerprint and a different key a different one`() {
        assertEquals(KeyFingerprint.of(b64(9, 9, 9)), KeyFingerprint.of(b64(9, 9, 9)))
        assertNotEquals(KeyFingerprint.of(b64(9, 9, 9)), KeyFingerprint.of(b64(9, 9, 8)))
    }

    @Test
    fun `known vector for a single zero byte`() {
        // SHA-256(0x00) = 6e340b9c ffb37a98 9ca544e6 bb780a2c ...
        assertEquals("6E34 0B9C FFB3 7A98", KeyFingerprint.of(b64(0)))
    }

    @Test
    fun `garbage and empty input give null`() {
        assertNull(KeyFingerprint.of("not base64 !!"))
        assertNull(KeyFingerprint.of(""))
    }
}
