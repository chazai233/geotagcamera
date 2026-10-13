package com.geotagcamera.geotagginglocationonphoto.ui.onboarding

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geotagcamera.geotagginglocationonphoto.profile.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(
    name = "onboarding_prefs",
    // A corrupt file (killed mid-write, bad flash) resets to defaults instead of crashing.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** First-run state: whether onboarding is done, and which profile the user picked. */
class OnboardingPreferences(private val context: Context) {

    private object Keys {
        val COMPLETED = booleanPreferencesKey("has_completed_onboarding")
        val PROFILE = stringPreferencesKey("user_profile")
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.onboardingDataStore.data.map { prefs ->
        prefs[Keys.COMPLETED] ?: false
    }

    /** Null for people who onboarded before profiles existed; they behave as Field. */
    val profile: Flow<UserProfile?> = context.onboardingDataStore.data.map { prefs ->
        UserProfile.fromName(prefs[Keys.PROFILE])
    }

    suspend fun setCompleted() {
        context.onboardingDataStore.edit { prefs -> prefs[Keys.COMPLETED] = true }
    }

    suspend fun setProfile(profile: UserProfile) {
        context.onboardingDataStore.edit { prefs -> prefs[Keys.PROFILE] = profile.name }
    }
}
