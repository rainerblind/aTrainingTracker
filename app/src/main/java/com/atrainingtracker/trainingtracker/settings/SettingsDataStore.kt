package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create a single instance of DataStore for the entire app context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsDataStore(context: Context) {

    private val appContext = context.applicationContext

    // Define integer keys
    companion object {
        val INT_HR_Zone1_Max = intPreferencesKey("int_hr_zone1_max")

        val INT_HR_Zone2_Max = intPreferencesKey("int_hr_zone2_max")

        val INT_HR_Zone3_Max = intPreferencesKey("int_hr_zone3_max")

        val INT_HR_Zone4_Max = intPreferencesKey("int_hr_zone4_max")


        // TODO same as for HR
        val INT_PWR_Zone1_Max = intPreferencesKey("int_pwr_zone1_max")
        val INT_PWR_Zone2_Max = intPreferencesKey("int_pwr_zone2_max")
        val INT_PWR_Zone3_Max = intPreferencesKey("int_pwr_zone3_max")
        val INT_PWR_Zone4_Max = intPreferencesKey("int_pwr_zone4_max")
    }

    // --- READ (Expose as Flows) ---
    val hrZone1MaxFlow: Flow<Int> = appContext.dataStore.data.map { it[INT_HR_Zone1_Max] ?: 140 }

    val hrZone2MaxFlow: Flow<Int> = appContext.dataStore.data.map { it[INT_HR_Zone2_Max] ?: 160 }

    val hrZone3MaxFlow: Flow<Int> = appContext.dataStore.data.map { it[INT_HR_Zone3_Max] ?: 170}

    val hrZone4MaxFlow: Flow<Int> = appContext.dataStore.data.map { it[INT_HR_Zone4_Max] ?: 180 }


    // TODO same as for HR
    val pwrZone1MaxFlow: Flow<Int> = appContext.dataStore.data.map { it[INT_PWR_Zone1_Max] ?: 150 }
    val pwrZone2MaxFlow: Flow<Int> = appContext.dataStore.data.map { it[INT_PWR_Zone2_Max] ?: 200 }
    val pwrZone3MaxFlow: Flow<Int> = appContext.dataStore.data.map { it[INT_PWR_Zone3_Max] ?: 250 }
    val pwrZone4MaxFlow: Flow<Int> = appContext.dataStore.data.map { it[INT_PWR_Zone4_Max] ?: 300 }

    // --- WRITE (Suspend functions) ---
    suspend fun saveHrZone1Max(value: Int) {
        appContext.dataStore.edit { it[INT_HR_Zone1_Max] = value }
    }

    suspend fun saveHrZone2Max(value: Int) {
        appContext.dataStore.edit { it[INT_HR_Zone2_Max] = value }
    }

    suspend fun saveHrZone3Max(value: Int) {
        appContext.dataStore.edit { it[INT_HR_Zone3_Max] = value }
    }

    suspend fun saveHrZone4Max(value: Int) {
        appContext.dataStore.edit { it[INT_HR_Zone4_Max] = value }
    }



    // TODO: same as for HR
    suspend fun savePwrZone1Max(value: Int) {
        appContext.dataStore.edit { it[INT_PWR_Zone1_Max] = value }
    }

    suspend fun savePwrZone2Max(value: Int) {
        appContext.dataStore.edit { it[INT_PWR_Zone2_Max] = value }
    }

    suspend fun savePwrZone3Max(value: Int) {
        appContext.dataStore.edit { it[INT_PWR_Zone3_Max] = value }
    }

    suspend fun savePwrZone4Max(value: Int) {
        appContext.dataStore.edit { it[INT_PWR_Zone4_Max] = value }
    }
}