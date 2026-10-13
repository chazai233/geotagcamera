package com.geotagcamera.geotagginglocationonphoto.profile

import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields

/**
 * Who the app is set up for. One design system serves both. The profile
 * changes defaults and how much Settings shows, never the look or the proof.
 * Anything Professional has can still be switched on from Field's settings.
 */
enum class UserProfile(val title: String, val summary: String, val details: List<String>) {
    FIELD(
        title = "Field",
        summary = "Students, NGO and field staff. Quick, simple, light on the phone.",
        details = listOf(
            "Point and shoot: place, address, coordinates and time on every photo",
            "Fewer settings to think about",
            "Signing happens quietly in the background"
        )
    ),
    PROFESSIONAL(
        title = "Professional",
        summary = "Surveyors, inspectors, engineers. Every detail, signed and reportable.",
        details = listOf(
            "Altitude, accuracy and bearing on the stamp",
            "Field worker signature before each photo is saved",
            "All stamp and capture settings visible"
        )
    );

    /** The stamp defaults this profile starts from. Applied once, when the profile is chosen. */
    fun applyDefaults(fields: StampFields): StampFields = when (this) {
        FIELD -> fields.copy(
            showAltitude = false,
            showBearing = false,
            showWeather = false,
            requireSignature = false
        )
        PROFESSIONAL -> fields.copy(
            showAltitude = true,
            showAccuracy = true,
            showBearing = true,
            requireSignature = true,
            showSignatureField = true
        )
    }

    /** Professional sees every setting at once; Field gets the essentials with the rest one tap away. */
    val showsAllSettings: Boolean get() = this == PROFESSIONAL

    companion object {
        fun fromName(name: String?): UserProfile? = entries.firstOrNull { it.name == name }
    }
}
