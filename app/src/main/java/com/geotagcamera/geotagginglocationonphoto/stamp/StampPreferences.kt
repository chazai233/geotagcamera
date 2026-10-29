package com.geotagcamera.geotagginglocationonphoto.stamp

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.stampDataStore by preferencesDataStore(
    name = "stamp_prefs",
    // A corrupt file (killed mid-write, bad flash) resets to defaults instead of crashing.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * No cloud sync, no account required — preferences live only on this device.
 *
 * Migration note: every key string that existed before the 1.1.0 redesign
 * (COORDINATES, ADDRESS, TIMESTAMP, ALTITUDE, ACCURACY, BEARING, ORG_LABEL,
 * REQUIRE_SIGNATURE) is kept byte-for-byte unchanged below, so an existing
 * install's choices load exactly as before. Everything new is a fresh key
 * with its own default — that's the entire migration strategy, no
 * DataStore.Migration object needed for a preferences store this simple.
 */
class StampPreferences(private val context: Context) {

    private object Keys {
        val TEMPLATE = stringPreferencesKey("template")
        val POSITION = stringPreferencesKey("position")
        // v2 (1.3): the map became keyless and default-on; the old key held "off" for everyone.
        val MAP = booleanPreferencesKey("show_map_v2")
        val COUNTRY = booleanPreferencesKey("show_country")
        val COORDINATES = booleanPreferencesKey("show_coordinates")
        val ADDRESS = booleanPreferencesKey("show_address")
        val TIMESTAMP = booleanPreferencesKey("show_timestamp")
        val GMT_OFFSET = booleanPreferencesKey("show_gmt_offset")
        val ALTITUDE = booleanPreferencesKey("show_altitude")
        val ACCURACY = booleanPreferencesKey("show_accuracy")
        val BEARING = booleanPreferencesKey("show_bearing")
        val WEATHER = booleanPreferencesKey("show_weather")
        val ORG_LABEL_TOGGLE = booleanPreferencesKey("show_org_label_toggle")
        val ORG_LABEL = stringPreferencesKey("org_label")
        val ORG_LOGO_TOGGLE = booleanPreferencesKey("show_org_logo")
        val ORG_LOGO_URI = stringPreferencesKey("org_logo_uri")
        val SIGNATURE_FIELD = booleanPreferencesKey("show_signature_field")
        val REQUIRE_SIGNATURE = booleanPreferencesKey("require_signature")
        val BRAND_MARK = booleanPreferencesKey("show_brand_mark")
        val AUTO_DISMISS_REVIEW = booleanPreferencesKey("auto_dismiss_review")
        val CUSTOM_TEXT = stringPreferencesKey("custom_text")
        val CUSTOM_TEXT_TOGGLE = booleanPreferencesKey("show_custom_text")
        val STYLE_SCALE = floatPreferencesKey("style_scale")
        val BG_COLOR = intPreferencesKey("style_bg_color")
        val BG_OPACITY = floatPreferencesKey("style_bg_opacity")
        val TEXT_COLOR = intPreferencesKey("style_text_color")
        val TEXT_OPACITY = floatPreferencesKey("style_text_opacity")
        val MARGIN_X = floatPreferencesKey("style_margin_x")
        val MARGIN_Y = floatPreferencesKey("style_margin_y")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val TIME_LABEL = stringPreferencesKey("time_label")
        val WRAP_TEXT = booleanPreferencesKey("wrap_text")
        val CUSTOM_FIELDS = stringPreferencesKey("custom_fields_json")
    }

    val fields: Flow<StampFields> = context.stampDataStore.data.map { it.toFields() }

    private fun Preferences.toFields(): StampFields {
        val prefs = this
        return StampFields(
            template = prefs[Keys.TEMPLATE]?.let { runCatching { StampTemplate.valueOf(it) }.getOrNull() }
                ?: StampTemplate.BAR,
            position = prefs[Keys.POSITION]?.let { runCatching { StampAnchor.valueOf(it) }.getOrNull() }
                ?: StampAnchor.BOTTOM_LEFT,
            showMap = prefs[Keys.MAP] ?: true,
            showCountry = prefs[Keys.COUNTRY] ?: true,
            showAddress = prefs[Keys.ADDRESS] ?: true,
            showCoordinates = prefs[Keys.COORDINATES] ?: true,
            showTimestamp = prefs[Keys.TIMESTAMP] ?: true,
            showGmtOffset = prefs[Keys.GMT_OFFSET] ?: true,
            showAltitude = prefs[Keys.ALTITUDE] ?: false,
            showAccuracy = prefs[Keys.ACCURACY] ?: true,
            showBearing = prefs[Keys.BEARING] ?: false,
            showWeather = prefs[Keys.WEATHER] ?: false,
            showOrgLabelToggle = prefs[Keys.ORG_LABEL_TOGGLE] ?: true,
            orgLabel = prefs[Keys.ORG_LABEL] ?: "",
            showOrgLogo = prefs[Keys.ORG_LOGO_TOGGLE] ?: true,
            orgLogoUri = prefs[Keys.ORG_LOGO_URI],
            showSignatureField = prefs[Keys.SIGNATURE_FIELD] ?: true,
            requireSignature = prefs[Keys.REQUIRE_SIGNATURE] ?: false,
            showBrandMark = prefs[Keys.BRAND_MARK] ?: true,
            customText = prefs[Keys.CUSTOM_TEXT] ?: "",
            showCustomText = prefs[Keys.CUSTOM_TEXT_TOGGLE] ?: true,
            styleScale = (prefs[Keys.STYLE_SCALE] ?: 1f).coerceIn(0.5f, 2.5f),
            bgColor = prefs[Keys.BG_COLOR] ?: 0x121417,
            bgOpacity = (prefs[Keys.BG_OPACITY] ?: 0.70f).coerceIn(0f, 1f),
            textColor = prefs[Keys.TEXT_COLOR] ?: 0xFFFFFF,
            textOpacity = (prefs[Keys.TEXT_OPACITY] ?: 1f).coerceIn(0.2f, 1f),
            marginX = (prefs[Keys.MARGIN_X] ?: 8f).coerceIn(0f, 80f),
            marginY = (prefs[Keys.MARGIN_Y] ?: 8f).coerceIn(0f, 80f),
            dateFormat = prefs[Keys.DATE_FORMAT] ?: DateFormats.DEFAULT,
            timeLabel = prefs[Keys.TIME_LABEL] ?: "",
            wrapText = prefs[Keys.WRAP_TEXT] ?: true,
            customFields = prefs[Keys.CUSTOM_FIELDS]?.let { CustomFieldsCodec.decode(it) } ?: CustomField.DEFAULTS
        )
    }

    /** Post-capture review auto-dismiss — a capture default, off by default (design section 04). */
    val autoDismissReview: Flow<Boolean> = context.stampDataStore.data.map { it[Keys.AUTO_DISMISS_REVIEW] ?: false }

    suspend fun setAutoDismissReview(enabled: Boolean) {
        context.stampDataStore.edit { it[Keys.AUTO_DISMISS_REVIEW] = enabled }
    }

    /** Removes the org-logo key entirely — update() only ever writes it when non-null, so it can't clear it. */
    suspend fun clearOrgLogo() {
        context.stampDataStore.edit { it.remove(Keys.ORG_LOGO_URI) }
    }

    /** Wipes every stamp preference back to defaults — for About & legal's delete-all-data path. */
    suspend fun clearAll() {
        context.stampDataStore.edit { it.clear() }
    }

    /**
     * Read-modify-write inside one DataStore transaction, so the transform
     * always sees the stored state, never a stale UI snapshot. Two quick edits
     * (a toggle tapped while typing the org label) can no longer overwrite
     * each other, and an edit before the first emission can't reset
     * everything to defaults.
     */
    suspend fun edit(transform: (StampFields) -> StampFields) {
        context.stampDataStore.edit { prefs -> prefs.write(transform(prefs.toFields())) }
    }

    suspend fun update(fields: StampFields) {
        context.stampDataStore.edit { prefs -> prefs.write(fields) }
    }

    private fun MutablePreferences.write(fields: StampFields) {
        val prefs = this
        prefs[Keys.TEMPLATE] = fields.template.name
        prefs[Keys.POSITION] = fields.position.name
        prefs[Keys.MAP] = fields.showMap
        prefs[Keys.COUNTRY] = fields.showCountry
        prefs[Keys.COORDINATES] = fields.showCoordinates
        prefs[Keys.ADDRESS] = fields.showAddress
        prefs[Keys.TIMESTAMP] = fields.showTimestamp
        prefs[Keys.GMT_OFFSET] = fields.showGmtOffset
        prefs[Keys.ALTITUDE] = fields.showAltitude
        prefs[Keys.ACCURACY] = fields.showAccuracy
        prefs[Keys.BEARING] = fields.showBearing
        prefs[Keys.WEATHER] = fields.showWeather
        prefs[Keys.ORG_LABEL_TOGGLE] = fields.showOrgLabelToggle
        prefs[Keys.ORG_LABEL] = fields.orgLabel
        prefs[Keys.ORG_LOGO_TOGGLE] = fields.showOrgLogo
        fields.orgLogoUri?.let { prefs[Keys.ORG_LOGO_URI] = it }
        prefs[Keys.SIGNATURE_FIELD] = fields.showSignatureField
        prefs[Keys.REQUIRE_SIGNATURE] = fields.requireSignature
        prefs[Keys.BRAND_MARK] = fields.showBrandMark
        prefs[Keys.CUSTOM_TEXT] = fields.customText
        prefs[Keys.CUSTOM_TEXT_TOGGLE] = fields.showCustomText
        prefs[Keys.STYLE_SCALE] = fields.styleScale
        prefs[Keys.BG_COLOR] = fields.bgColor
        prefs[Keys.BG_OPACITY] = fields.bgOpacity
        prefs[Keys.TEXT_COLOR] = fields.textColor
        prefs[Keys.TEXT_OPACITY] = fields.textOpacity
        prefs[Keys.MARGIN_X] = fields.marginX
        prefs[Keys.MARGIN_Y] = fields.marginY
        prefs[Keys.DATE_FORMAT] = fields.dateFormat
        prefs[Keys.TIME_LABEL] = fields.timeLabel
        prefs[Keys.WRAP_TEXT] = fields.wrapText
        prefs[Keys.CUSTOM_FIELDS] = CustomFieldsCodec.encode(fields.customFields)
    }
}
