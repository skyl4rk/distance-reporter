package com.example.distancereporter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val UNIT = stringPreferencesKey("unit")
        val INTERVAL = doublePreferencesKey("interval")
        val VOLUME = floatPreferencesKey("volume")
        val IS_MUTED = booleanPreferencesKey("is_muted")
        val IS_PAUSED = booleanPreferencesKey("is_paused")
        val CURRENT_DISTANCE_METERS = doublePreferencesKey("current_distance_meters")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            unit = DistanceUnit.valueOf(
                preferences[PreferencesKeys.UNIT] ?: DistanceUnit.KILOMETERS.name
            ),
            intervalInUnits = preferences[PreferencesKeys.INTERVAL] ?: 0.5,
            volume = preferences[PreferencesKeys.VOLUME] ?: 1.0f,
            isMuted = preferences[PreferencesKeys.IS_MUTED] ?: false,
            isPaused = preferences[PreferencesKeys.IS_PAUSED] ?: false,
            currentDistanceMeters = preferences[PreferencesKeys.CURRENT_DISTANCE_METERS] ?: 0.0
        )
    }

    suspend fun updateUnit(unit: DistanceUnit) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNIT] = unit.name
        }
    }

    suspend fun updateInterval(interval: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INTERVAL] = interval
        }
    }

    suspend fun updateVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VOLUME] = volume
        }
    }

    suspend fun updateMuted(isMuted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_MUTED] = isMuted
        }
    }

    suspend fun updatePaused(isPaused: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_PAUSED] = isPaused
        }
    }

    suspend fun updateCurrentDistance(meters: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_DISTANCE_METERS] = meters
        }
    }

    suspend fun resetCurrentDistance() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_DISTANCE_METERS] = 0.0
        }
    }
}

data class UserPreferences(
    val unit: DistanceUnit = DistanceUnit.KILOMETERS,
    val intervalInUnits: Double = 0.5,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val isPaused: Boolean = false,
    val currentDistanceMeters: Double = 0.0
) {
    val currentDistanceInUnits: Double
        get() = unit.convertFromMeters(currentDistanceMeters)

    val intervalInMeters: Double
        get() = intervalInUnits * unit.metersPerUnit
}
