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

    // private val appContext = context.applicationContext // not really needed???

    enum class ZoneType(val id: Int) {
        HR_RUN(0),     // Heart Rate
        HR_BIKE(1),    // Heart Rate
        PWR_BIKE(2);   // Power

        companion object {
            fun fromId(id: Int): ZoneType = entries.find {it.id == id} ?: HR_RUN
        }
    }

    enum class Zone (val index: Int) {
        ZONE_1(1),
        ZONE_2(2),
        ZONE_3(3),
        ZONE_4(4)
    }

    // Define integer keys
    companion object {

        // Zone Type HR Run
        private val HR_RUN_ZONE1_MAX = intPreferencesKey("hr_run_zone1_max")
        private val HR_RUN_ZONE2_MAX = intPreferencesKey("hr_run_zone2_max")
        private val HR_RUN_ZONE3_MAX = intPreferencesKey("hr_run_zone3_max")
        private val HR_RUN_ZONE4_MAX = intPreferencesKey("hr_run_zone4_max")

        // Zone Type HR Bike
        private val HR_BIKE_ZONE1_MAX = intPreferencesKey("hr_bike_zone1_max")
        private val HR_BIKE_ZONE2_MAX = intPreferencesKey("hr_bike_zone2_max")
        private val HR_BIKE_ZONE3_MAX = intPreferencesKey("hr_bike_zone3_max")
        private val HR_BIKE_ZONE4_MAX = intPreferencesKey("hr_bike_zone4_max")

        // Zone Type PWR Bike
        private val PWR_BIKE_ZONE1_MAX = intPreferencesKey("pwr_bike_zone1_max")
        private val PWR_BIKE_ZONE2_MAX = intPreferencesKey("pwr_bike_zone2_max")
        private val PWR_BIKE_ZONE3_MAX = intPreferencesKey("pwr_bike_zone3_max")
        private val PWR_BIKE_ZONE4_MAX = intPreferencesKey("pwr_bike_zone4_max")
    }

    // --- HELPER: Map Enums to Keys ---
    private fun getKey(zoneType: ZoneType, zone: Zone): Preferences.Key<Int> {
        return when (zoneType) {
            ZoneType.HR_RUN -> when (zone) {
                Zone.ZONE_1 -> HR_RUN_ZONE1_MAX
                Zone.ZONE_2 -> HR_RUN_ZONE2_MAX
                Zone.ZONE_3 -> HR_RUN_ZONE3_MAX
                Zone.ZONE_4 -> HR_RUN_ZONE4_MAX
            }

            ZoneType.HR_BIKE -> when (zone) {
                Zone.ZONE_1 -> HR_BIKE_ZONE1_MAX
                Zone.ZONE_2 -> HR_BIKE_ZONE2_MAX
                Zone.ZONE_3 -> HR_BIKE_ZONE3_MAX
                Zone.ZONE_4 -> HR_BIKE_ZONE4_MAX
            }

            ZoneType.PWR_BIKE -> when (zone) {
                Zone.ZONE_1 -> PWR_BIKE_ZONE1_MAX
                Zone.ZONE_2 -> PWR_BIKE_ZONE2_MAX
                Zone.ZONE_3 -> PWR_BIKE_ZONE3_MAX
                Zone.ZONE_4 -> PWR_BIKE_ZONE4_MAX
            }
        }
    }

    // --- HELPER: Get Good Default Values ---
    fun getDefaultValue(zoneType: ZoneType, zone: Zone): Int {
        return when (zoneType) {
            // Heart Rate defaults
            ZoneType.HR_RUN -> when (zone) {
                Zone.ZONE_1 -> 135 // Recovery
                Zone.ZONE_2 -> 155 // Aerobic
                Zone.ZONE_3 -> 175 // Tempo
                Zone.ZONE_4 -> 185 // Threshold
            }
            ZoneType.HR_BIKE -> when (zone) {
                Zone.ZONE_1 -> 130 // Recovery
                Zone.ZONE_2 -> 150 // Aerobic
                Zone.ZONE_3 -> 170 // Tempo
                Zone.ZONE_4 -> 180 // Threshold
            }
            // Power defaults (Watts)
            ZoneType.PWR_BIKE -> when (zone) {
                Zone.ZONE_1 -> 150 // Active Recovery
                Zone.ZONE_2 -> 200 // Endurance
                Zone.ZONE_3 -> 250 // Tempo
                Zone.ZONE_4 -> 300 // Threshold (FTP)
            }
        }
    }

    // --- READ FUNCTIONS ---

    // Generic Flow getter
    // Generic Flow getter
    fun getZoneMaxFlow(zoneType: ZoneType, zone: Zone): Flow<Int> {
        val defaultVal = getDefaultValue(zoneType, zone)
        return context.dataStore.data.map { prefs ->
            prefs[getKey(zoneType, zone)] ?: defaultVal
        }
    }

    // Specific getters for convenience (optional, but makes UI code cleaner)
    fun getZone1MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_1)
    fun getZone2MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_2)
    fun getZone3MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_3)
    fun getZone4MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_4)

    // -- Helper: getSummary
    fun getSummary(zoneType: ZoneType): String = runBlocking {
        val z1Max = getZone1MaxFlow(zoneType).first()
        val z2Max = getZone2MaxFlow(zoneType).first()
        val z3Max = getZone3MaxFlow(zoneType).first()
        val z4Max = getZone4MaxFlow(zoneType).first()

        val z2Min = z1Max + 1
        val z3Min = z2Max + 1
        val z4Min = z3Max + 1
        val z5Min = z4Max + 1

        "Z1: <$z1Max, Z2: $z2Min-$z2Max, Z3: $z3Min-$z3Max, Z4: $z4Min-$z4Max, Z5: >$z5Min"
    }

    // --- WRITE FUNCTIONS ---

    suspend fun saveHrZoneMax(zoneType: ZoneType, zone: Zone, value: Int) {
        context.dataStore.edit { prefs ->
            prefs[getKey(zoneType, zone)] = value
        }
    }
}