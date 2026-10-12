package com.geotagcamera.geotagginglocationonphoto.location

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherReadingTest {
    @Test
    fun `chip text rounds temperature and maps WMO codes`() {
        assertEquals("31°C CLEAR", WeatherReading(30.6, 0).chipText)
        assertEquals("-2°C SNOW", WeatherReading(-2.4, 73).chipText)
        assertEquals("18°C RAIN", WeatherReading(18.0, 63).chipText)
        assertEquals("25°C THUNDERSTORM", WeatherReading(25.0, 99).chipText)
        assertEquals("20°C —", WeatherReading(20.0, 1234).chipText)
    }
}
