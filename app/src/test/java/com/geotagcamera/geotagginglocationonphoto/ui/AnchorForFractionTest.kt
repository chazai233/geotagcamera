package com.geotagcamera.geotagginglocationonphoto.ui

import com.geotagcamera.geotagginglocationonphoto.stamp.StampAnchor
import com.geotagcamera.geotagginglocationonphoto.ui.common.anchorForFraction
import org.junit.Assert.assertEquals
import org.junit.Test

class AnchorForFractionTest {
    @Test
    fun `corners and centre`() {
        assertEquals(StampAnchor.TOP_LEFT, anchorForFraction(0f, 0f))
        assertEquals(StampAnchor.TOP_RIGHT, anchorForFraction(1f, 0f))
        assertEquals(StampAnchor.MID_CENTER, anchorForFraction(0.5f, 0.5f))
        assertEquals(StampAnchor.BOTTOM_LEFT, anchorForFraction(0f, 1f))
        assertEquals(StampAnchor.BOTTOM_RIGHT, anchorForFraction(1f, 1f))
    }

    @Test
    fun `thirds boundaries belong to the next cell`() {
        assertEquals(StampAnchor.TOP_CENTER, anchorForFraction(1f / 3f, 0.1f))
        assertEquals(StampAnchor.TOP_RIGHT, anchorForFraction(2f / 3f, 0.1f))
        assertEquals(StampAnchor.MID_LEFT, anchorForFraction(0.1f, 1f / 3f))
    }

    @Test
    fun `drags past the frame edge land in the edge cells`() {
        assertEquals(StampAnchor.TOP_LEFT, anchorForFraction(-0.2f, -1f))
        assertEquals(StampAnchor.BOTTOM_RIGHT, anchorForFraction(1.4f, 2f))
    }
}
