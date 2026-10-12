package com.geotagcamera.geotagginglocationonphoto.ui

import com.geotagcamera.geotagginglocationonphoto.ui.review.ReviewFilename
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewFilenameTest {
    private val t = 1_790_000_000_000L

    @Test
    fun `place slug keeps only letters and digits of the first part`() {
        val name = ReviewFilename.generate("Salt Lake, Kolkata", t)
        assertTrue(name, name.startsWith("IMG_") && name.endsWith("_SaltLake.jpg"))
    }

    @Test
    fun `no usable place falls back to timestamp only`() {
        val shape = Regex("^IMG_\\d{8}_\\d{6}\\.jpg$")
        assertTrue(shape.matches(ReviewFilename.generate(null, t)))
        assertTrue(shape.matches(ReviewFilename.generate(" , ", t)))
    }

    @Test
    fun `two shots in the same minute get different names`() {
        assertNotEquals(ReviewFilename.generate("Here", t), ReviewFilename.generate("Here", t + 5_000))
    }

    @Test
    fun `size formatting boundaries`() {
        assertEquals("999 B", ReviewFilename.formatSize(999))
        assertEquals("1 KB", ReviewFilename.formatSize(1_000))
        assertEquals("999 KB", ReviewFilename.formatSize(999_999))
        assertEquals("1.0 MB", ReviewFilename.formatSize(1_000_000))
        assertEquals("3.2 MB", ReviewFilename.formatSize(3_200_000))
    }
}
