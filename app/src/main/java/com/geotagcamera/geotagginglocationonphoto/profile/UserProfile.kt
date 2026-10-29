package com.geotagcamera.geotagginglocationonphoto.profile

import com.geotagcamera.geotagginglocationonphoto.stamp.StampFields

/**
 * Who the app is set up for. One design system serves both. The profile
 * changes defaults and how much Settings shows, never the look or the proof.
 * Anything Professional has can still be switched on from Field's settings.
 */
enum class UserProfile(val title: String, val summary: String, val details: List<String>) {
    FIELD(
        title = "简单",
        summary = "学生、一般现场人员：快速、简单、省电。",
        details = listOf(
            "即拍即用：每张照片都带地点、地址、坐标和时间",
            "需要关心的设置更少",
            "签名在后台自动完成"
        )
    ),
    PROFESSIONAL(
        title = "专业",
        summary = "测量、监理、工程师：信息完整，可签名、可出报告。",
        details = listOf(
            "水印包含海拔、精度和方向",
            "每张照片保存前手写签名",
            "显示全部水印和拍摄设置"
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
