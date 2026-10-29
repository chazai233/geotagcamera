package com.geotagcamera.geotagginglocationonphoto.stamp

import com.geotagcamera.geotagginglocationonphoto.location.AddressParts
import com.geotagcamera.geotagginglocationonphoto.location.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildStampSpecTest {
    private val fix = LocationFix(22.5726, 88.3639, 11.4, 4.6f, 135f, 0L)
    private val address = AddressParts("Kolkata, West Bengal", "BBD Bagh, Kolkata, 700001", "India", "IN", fromCache = false)
    private val t = 1_790_000_000_000L
    private val plusCodeShape = Regex("^[23456789CFGHJMPQRVWX]{8}\\+[23456789CFGHJMPQRVWX]{2}$")

    private fun spec(fields: StampFields, weather: String? = null, signed: Boolean = false) =
        buildStampSpec(fix, address, t, fields, mapTile = null, orgLogo = null, hasSignature = signed, weatherChipText = weather)

    @Test
    fun `defaults show place, address with plus code, coordinates and time`() {
        val s = spec(StampFields())
        assertEquals("Kolkata, West Bengal", s.placeName)
        val (code, rest) = s.addressLine!!.split(", ", limit = 2)
        assertTrue(code, plusCodeShape.matches(code))
        assertEquals("BBD Bagh, Kolkata, 700001", rest)
        assertEquals("纬度 22.572600°  经度 88.363900°", s.coordinatesText)
        assertTrue(s.dateTimeText != null && s.gmtOffsetText != null)
        assertEquals("IND", s.countryCode)
    }

    @Test
    fun `turning fields off removes them`() {
        val s = spec(
            StampFields(
                showAddress = false, showCoordinates = false, showTimestamp = false,
                showCountry = false, showAccuracy = false, showBrandMark = false
            )
        )
        assertNull(s.addressLine)
        assertNull(s.coordinatesText)
        assertNull(s.dateTimeText)
        assertNull(s.gmtOffsetText)
        assertNull(s.countryCode)
        assertTrue(s.chips.isEmpty())
        assertFalse(s.hasFooterRow)
    }

    @Test
    fun `gmt offset needs the timestamp on`() {
        assertNull(spec(StampFields(showTimestamp = false, showGmtOffset = true)).gmtOffsetText)
    }

    @Test
    fun `chips follow their toggles and round sensibly`() {
        val s = spec(
            StampFields(showAltitude = true, showAccuracy = true, showBearing = true, showWeather = true),
            weather = "31°C CLEAR"
        )
        assertEquals(listOf("海拔 11 m", "±5 m", "方向 135°", "31°C CLEAR"), s.chips.map { it.text })
        assertTrue(spec(StampFields(showWeather = false), weather = "31°C CLEAR").chips.none { "CLEAR" in it.text })
    }

    @Test
    fun `org label only when toggled on and non-blank`() {
        assertNull(spec(StampFields(orgLabel = "   ")).orgLabel)
        assertNull(spec(StampFields(orgLabel = "ACME", showOrgLabelToggle = false)).orgLabel)
        assertEquals("ACME", spec(StampFields(orgLabel = "ACME")).orgLabel)
    }

    @Test
    fun `signed mark needs both a signature and the toggle`() {
        assertTrue(spec(StampFields(), signed = true).showSignedMark)
        assertFalse(spec(StampFields(), signed = false).showSignedMark)
        assertFalse(spec(StampFields(showSignatureField = false), signed = true).showSignedMark)
    }

    @Test
    fun `map is off by default`() {
        assertTrue(StampFields().showMap)
    }

    @Test
    fun `no address falls back to the plus code alone`() {
        val s = buildStampSpec(fix, null, t, StampFields(), null, null, false)
        assertNull(s.placeName)
        assertTrue(s.addressLine!!, plusCodeShape.matches(s.addressLine!!))
    }

    @Test
    fun `date format follows the chosen pattern`() {
        val tz = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        try {
            val s = spec(StampFields(dateFormat = "yyyy-MM-dd HH:mm"))
            assertEquals("2026-09-21 14:13", s.dateTimeText)
            val zh = spec(StampFields(dateFormat = "yyyy年MM月dd日 EEEE"))
            assertEquals("2026年09月21日 星期一", zh.dateTimeText)
        } finally {
            java.util.TimeZone.setDefault(tz)
        }
    }

    @Test
    fun `invalid date pattern falls back instead of failing`() {
        assertTrue(DateFormats.format("yyyy-MM-dd qqq", t).isNotBlank())
        assertFalse(DateFormats.isValid("yyyy-MM-dd qqq"))
        assertTrue(DateFormats.isValid("yyyy-MM-dd"))
    }

    @Test
    fun `style carries colour and opacity`() {
        val s = spec(StampFields(bgColor = 0x0D47A1, bgOpacity = 0.5f, textColor = 0xFFEB3B, textOpacity = 1f, styleScale = 1.5f))
        assertEquals(0x7F0D47A1, s.style.bgArgb)
        assertEquals(0xFFFFEB3B.toInt(), s.style.textArgb)
        assertEquals(1.5f, s.style.scale)
    }

    @Test
    fun `custom fields become labelled lines and skip empty or hidden ones`() {
        val s = spec(
            StampFields(
                customFields = listOf(
                    CustomField(1, "施工区域", "右岸坝肩"),
                    CustomField(2, "施工内容", "土方开挖"),
                    CustomField(3, "桩号", ""),
                    CustomField(4, "监理", "张三", enabled = false),
                    CustomField(5, "", "无名称")
                )
            )
        )
        assertEquals(listOf("施工区域：右岸坝肩", "施工内容：土方开挖", "无名称"), s.fieldLines)
    }

    @Test
    fun `time label prefixes the date line`() {
        val s = spec(StampFields(timeLabel = "拍摄时间", dateFormat = "yyyy"))
        assertTrue(s.dateTimeText!!, s.dateTimeText!!.startsWith("拍摄时间：20"))
    }

    @Test
    fun `history keeps newest first without duplicates`() {
        var f = CustomField(1, "施工区域", "A")
        f = f.withValueInHistory().copy(value = "B").withValueInHistory().copy(value = "A").withValueInHistory()
        assertEquals(listOf("A", "B"), f.history)
    }

    @Test
    fun `custom fields survive a json round trip and bad json falls back`() {
        val list = listOf(CustomField(1, "施工区域", "右岸坝肩", true, listOf("右岸坝肩", "左岸"), listOf("右岸坝肩", "右岸基坑")), CustomField(7, "桩号", "", false))
        assertEquals(list, CustomFieldsCodec.decode(CustomFieldsCodec.encode(list)))
        assertEquals(CustomField.DEFAULTS, CustomFieldsCodec.decode("not json"))
    }

    @Test
    fun `presets dedupe, trim, remove and reorder`() {
        var f = CustomField(2, "施工内容").withPresets(listOf(" 一期下游全年围堰石方开挖 ", "", "土方开挖", "土方开挖"))
        assertEquals(listOf("一期下游全年围堰石方开挖", "土方开挖"), f.presets)
        f = f.movePreset("土方开挖", -1)
        assertEquals(listOf("土方开挖", "一期下游全年围堰石方开挖"), f.presets)
        f = f.withoutPreset("土方开挖")
        assertEquals(listOf("一期下游全年围堰石方开挖"), f.presets)
    }

    @Test
    fun `without a location only the time, fields and plain address show`() {
        val s = buildStampSpec(null, address, t, StampFields(showAltitude = true, customFields = listOf(CustomField(1, "施工区域", "右岸基坑"))), null, null, false)
        assertNull(s.coordinatesText)
        assertTrue(s.chips.isEmpty())
        assertEquals("BBD Bagh, Kolkata, 700001", s.addressLine)
        assertEquals(listOf("施工区域：右岸基坑"), s.fieldLines)
        assertTrue(s.dateTimeText != null)
    }

    @Test
    fun `line breaks never get into fields, presets or history`() {
        val f = CustomField(1, "施工内容", "船闸开挖\n石方").withPresets(listOf("右岸\r\n基坑")).withValueInHistory()
        assertEquals("施工内容：船闸开挖 石方", f.line)
        assertEquals(listOf("右岸 基坑"), f.presets)
        assertEquals(listOf("船闸开挖 石方"), f.history)
        val stored = """[{"id":1,"label":"施工内容","value":"a\nb","presets":["x\ny","x y",""],"history":[]}]"""
        val decoded = CustomFieldsCodec.decode(stored).single()
        assertEquals("a b", decoded.value)
        assertEquals(listOf("x y"), decoded.presets)
    }
}
