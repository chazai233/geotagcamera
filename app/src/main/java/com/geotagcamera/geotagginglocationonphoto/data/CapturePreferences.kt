package com.geotagcamera.geotagginglocationonphoto.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.captureDataStore by preferencesDataStore(
    name = "capture_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * What the next shot is taken under: the active project, and a note that is
 * burned into every stamp until changed ("Pole 12", "North wall, crack 3").
 * Both are sticky across app restarts because field work spans days.
 */
class CapturePreferences(private val context: Context) {

    private object Keys {
        val ACTIVE_PROJECT = longPreferencesKey("active_project_id")
        val NOTE = stringPreferencesKey("capture_note")
    }

    val activeProjectId: Flow<Long?> = context.captureDataStore.data.map { it[Keys.ACTIVE_PROJECT]?.takeIf { id -> id > 0 } }

    val note: Flow<String> = context.captureDataStore.data.map { it[Keys.NOTE] ?: "" }

    suspend fun setActiveProject(id: Long?) {
        context.captureDataStore.edit { if (id == null) it.remove(Keys.ACTIVE_PROJECT) else it[Keys.ACTIVE_PROJECT] = id }
    }

    suspend fun setNote(note: String) {
        context.captureDataStore.edit { it[Keys.NOTE] = note.take(MAX_NOTE_CHARS) }
    }

    companion object {
        /** One stamp line's worth; longer notes would only be ellipsized. */
        const val MAX_NOTE_CHARS = 120
    }
}
