package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Create a single instance of DataStore for the entire app context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsDataStore(private val context: Context) {

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

    fun getZonesSummary(): String = runBlocking {
        val prefs = context.dataStore.data.first() // Get current snapshot
        val z1Max = prefs[INT_HR_Zone1_Max] ?: 140
        val z2Max = prefs[INT_HR_Zone2_Max] ?: 160
        val z3Max = prefs[INT_HR_Zone3_Max] ?: 170
        val z4Max = prefs[INT_HR_Zone4_Max] ?: 180

        // Format: "Z1: -140, Z2: 141-160, ..." or just Max values
        // Let's do a simple range summary
        "Z1: <$z1Max, Z2: <$z2Max, Z3: <$z3Max, Z4: <$z4Max"

        val z2Min = z1Max + 1
        val z3Min = z2Max + 1
        val z4Min = z3Max + 1
        val z5Min = z4Max + 1

        "Z1: <$z1Max, Z2: $z2Min-$z2Max, Z3: $z3Min-$z3Max, Z4: $z4Min-$z4Max, Z5: >$z5Min"
    }


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