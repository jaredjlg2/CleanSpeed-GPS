package com.cleanspeed.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cleanspeed.util.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cleanspeed_prefs")

class UserPrefs(private val context: Context) {
    private val unitKey = stringPreferencesKey("speed_unit")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")

    val speedUnit: Flow<SpeedUnit> = context.dataStore.data.map { prefs ->
        SpeedUnit.valueOf(prefs[unitKey] ?: SpeedUnit.MPH.name)
    }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keepScreenOnKey] ?: false
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        context.dataStore.edit { it[unitKey] = unit.name }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[keepScreenOnKey] = enabled }
    }
}
