package com.geotagcamera.geotagginglocationonphoto.stamp

import org.json.JSONArray
import org.json.JSONObject

/** 自定义字段与 JSON 字符串互转（存进 DataStore）。损坏的数据退回默认字段，不会崩溃。 */
object CustomFieldsCodec {
    fun encode(fields: List<CustomField>): String {
        val arr = JSONArray()
        fields.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("label", f.label)
                    .put("value", f.value)
                    .put("enabled", f.enabled)
                    .put("history", JSONArray(f.history))
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<CustomField> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val h = o.optJSONArray("history")
            CustomField(
                id = o.getLong("id"),
                label = o.optString("label", ""),
                value = o.optString("value", ""),
                enabled = o.optBoolean("enabled", true),
                history = if (h == null) emptyList() else (0 until h.length()).map { h.getString(it) }
            )
        }
    }.getOrElse { CustomField.DEFAULTS }
}
