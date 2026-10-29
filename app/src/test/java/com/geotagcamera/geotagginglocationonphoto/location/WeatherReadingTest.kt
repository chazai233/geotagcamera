package com.geotagcamera.geotagginglocationonphoto.location

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherReadingTest {
    @Test
    fun `chip text rounds temperature and maps WMO codes`() {
        assertEquals("31°C 晴", WeatherReading(30.6, 0).chipText)
        assertEquals("-2°C 雪", WeatherReading(-2.4, 73).chipText)
        assertEquals("18°C 雨", WeatherReading(18.0, 63).chipText)
        assertEquals("25°C 雷阵雨伴冰雹", WeatherReading(25.0, 99).chipText)
        assertEquals("20°C —", WeatherReading(20.0, 1234).chipText)
    }
}
