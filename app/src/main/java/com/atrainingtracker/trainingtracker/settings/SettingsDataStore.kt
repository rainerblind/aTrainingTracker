/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Create a single instance of DataStore for the entire app context
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsDataStore(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.dataStore
) {

    // private val appContext = context.applicationContext // not really needed???

    enum class ZoneType(val id: Int, val zoneCount: Int, val boundaryCount: Int) {
        HR_RUN(0, 5, 4),     // Heart Rate (5 zones, 4 upper boundaries)
        HR_BIKE(1, 5, 4),    // Heart Rate (5 zones, 4 upper boundaries)
        PWR_BIKE(2, 7, 6);   // Power (7 zones, 6 upper boundaries)

        companion object {
            fun fromId(id: Int): ZoneType = entries.find {it.id == id} ?: HR_RUN
        }
    }

    enum class Zone(val index: Int) {
        ZONE_1(1),
        ZONE_2(2),
        ZONE_3(3),
        ZONE_4(4),
        ZONE_5(5),
        ZONE_6(6)
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
        private val PWR_BIKE_ZONE5_MAX = intPreferencesKey("pwr_bike_zone5_max")
        private val PWR_BIKE_ZONE6_MAX = intPreferencesKey("pwr_bike_zone6_max")

        // Zone Display Options
        private val HR_RUN_ZONE_SHOW_BACKGROUND = booleanPreferencesKey("hr_run_zone_show_background")
        private val HR_RUN_ZONE_SHOW_LEFT_BAR = booleanPreferencesKey("hr_run_zone_show_left_bar")
        private val HR_RUN_ZONE_SHOW_RIGHT_BAR = booleanPreferencesKey("hr_run_zone_show_right_bar")
        private val HR_RUN_ZONE_SHOW_TEXT_COLOR = booleanPreferencesKey("hr_run_zone_show_text_color")

        private val HR_BIKE_ZONE_SHOW_BACKGROUND = booleanPreferencesKey("hr_bike_zone_show_background")
        private val HR_BIKE_ZONE_SHOW_LEFT_BAR = booleanPreferencesKey("hr_bike_zone_show_left_bar")
        private val HR_BIKE_ZONE_SHOW_RIGHT_BAR = booleanPreferencesKey("hr_bike_zone_show_right_bar")
        private val HR_BIKE_ZONE_SHOW_TEXT_COLOR = booleanPreferencesKey("hr_bike_zone_show_text_color")

        private val PWR_BIKE_ZONE_SHOW_BACKGROUND = booleanPreferencesKey("pwr_bike_zone_show_background")
        private val PWR_BIKE_ZONE_SHOW_LEFT_BAR = booleanPreferencesKey("pwr_bike_zone_show_left_bar")
        private val PWR_BIKE_ZONE_SHOW_RIGHT_BAR = booleanPreferencesKey("pwr_bike_zone_show_right_bar")
        private val PWR_BIKE_ZONE_SHOW_TEXT_COLOR = booleanPreferencesKey("pwr_bike_zone_show_text_color")
    }

    // --- HELPER: Map Enums to Keys ---
    private fun getKey(zoneType: ZoneType, zone: Zone): Preferences.Key<Int>? {
        return when (zoneType) {
            ZoneType.HR_RUN -> when (zone) {
                Zone.ZONE_1 -> HR_RUN_ZONE1_MAX
                Zone.ZONE_2 -> HR_RUN_ZONE2_MAX
                Zone.ZONE_3 -> HR_RUN_ZONE3_MAX
                Zone.ZONE_4 -> HR_RUN_ZONE4_MAX
                else -> null
            }

            ZoneType.HR_BIKE -> when (zone) {
                Zone.ZONE_1 -> HR_BIKE_ZONE1_MAX
                Zone.ZONE_2 -> HR_BIKE_ZONE2_MAX
                Zone.ZONE_3 -> HR_BIKE_ZONE3_MAX
                Zone.ZONE_4 -> HR_BIKE_ZONE4_MAX
                else -> null
            }

            ZoneType.PWR_BIKE -> when (zone) {
                Zone.ZONE_1 -> PWR_BIKE_ZONE1_MAX
                Zone.ZONE_2 -> PWR_BIKE_ZONE2_MAX
                Zone.ZONE_3 -> PWR_BIKE_ZONE3_MAX
                Zone.ZONE_4 -> PWR_BIKE_ZONE4_MAX
                Zone.ZONE_5 -> PWR_BIKE_ZONE5_MAX
                Zone.ZONE_6 -> PWR_BIKE_ZONE6_MAX
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
                else -> 0
            }
            ZoneType.HR_BIKE -> when (zone) {
                Zone.ZONE_1 -> 130 // Recovery
                Zone.ZONE_2 -> 150 // Aerobic
                Zone.ZONE_3 -> 170 // Tempo
                Zone.ZONE_4 -> 180 // Threshold
                else -> 0
            }
            // Power defaults (Watts)
            ZoneType.PWR_BIKE -> when (zone) {
                Zone.ZONE_1 -> 150 // Active Recovery
                Zone.ZONE_2 -> 200 // Endurance
                Zone.ZONE_3 -> 250 // Tempo
                Zone.ZONE_4 -> 300 // Threshold (FTP)
                Zone.ZONE_5 -> 350 // VO2 Max
                Zone.ZONE_6 -> 450 // Anaerobic Capacity
            }
        }
    }

    /**
     * Calculates proportional default values for Zone 5 and Zone 6 based on an existing Zone 4 max (FTP).
     */
    fun calculateProportionalPowerDefaults(z4Max: Int): Pair<Int, Int> {
        if (z4Max <= 0) {
            return Pair(350, 450)
        }
        val estimatedFtp = maxOf(50, kotlin.math.round(z4Max / 1.05).toInt())
        val defaultZ5 = maxOf(z4Max + 10, kotlin.math.round(estimatedFtp * 1.20).toInt())
        val defaultZ6 = maxOf(defaultZ5 + 10, kotlin.math.round(estimatedFtp * 1.50).toInt())
        return Pair(defaultZ5, defaultZ6)
    }

    /**
     * Validates that power thresholds follow strict monotonic ascending order:
     * 0 < z1 < z2 < z3 < z4 < z5 < z6
     */
    fun validatePowerZones(z1: Int, z2: Int, z3: Int, z4: Int, z5: Int, z6: Int): Boolean {
        return z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3 && z5 > z4 && z6 > z5
    }

    // --- READ FUNCTIONS ---

    // Generic Flow getter with dynamic proportional migration for Power zones 5 and 6
    fun getZoneMaxFlow(zoneType: ZoneType, zone: Zone): Flow<Int> {
        val key = getKey(zoneType, zone)
        val defaultVal = getDefaultValue(zoneType, zone)
        return dataStore.data.map { prefs ->
            if (key == null) return@map defaultVal
            val stored = prefs[key]
            if (stored != null) {
                stored
            } else if (zoneType == ZoneType.PWR_BIKE && (zone == Zone.ZONE_5 || zone == Zone.ZONE_6) && prefs.contains(PWR_BIKE_ZONE4_MAX)) {
                // Dynamic proportional migration from existing z4Max
                val z4Max = prefs[PWR_BIKE_ZONE4_MAX] ?: 300
                val (propZ5, propZ6) = calculateProportionalPowerDefaults(z4Max)
                if (zone == Zone.ZONE_5) propZ5 else propZ6
            } else {
                defaultVal
            }
        }
    }

    // Specific getters for convenience
    fun getZone1MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_1)
    fun getZone2MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_2)
    fun getZone3MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_3)
    fun getZone4MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_4)
    fun getZone5MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_5)
    fun getZone6MaxFlow(zoneType: ZoneType) = getZoneMaxFlow(zoneType, Zone.ZONE_6)

    // -- Helper: getSummary
    fun getSummary(zoneType: ZoneType): String = runBlocking {
        val z1Max = getZone1MaxFlow(zoneType).first()
        val z2Max = getZone2MaxFlow(zoneType).first()
        val z3Max = getZone3MaxFlow(zoneType).first()
        val z4Max = getZone4MaxFlow(zoneType).first()
        if (zoneType == ZoneType.PWR_BIKE) {
            val z5Max = getZone5MaxFlow(zoneType).first()
            val z6Max = getZone6MaxFlow(zoneType).first()
            "Z1: <$z1Max, Z2: <$z2Max, Z3: <$z3Max, Z4: <$z4Max, Z5: <$z5Max, Z6: <$z6Max"
        } else {
            "Z1: <$z1Max, Z2: <$z2Max, Z3: <$z3Max, Z4: <$z4Max"
        }
    }

    // --- WRITE FUNCTIONS ---

    suspend fun saveHrZoneMax(zoneType: ZoneType, zone: Zone, value: Int) {
        val key = getKey(zoneType, zone) ?: return
        dataStore.edit { prefs ->
            prefs[key] = value
        }
    }

    /**
     * Atomically executes migration for Power zones 5 and 6 if they are unconfigured,
     * with immediate post-migration sanity validation.
     */
    suspend fun checkAndMigratePowerZones() {
        dataStore.edit { prefs ->
            val hasZ5 = prefs.contains(PWR_BIKE_ZONE5_MAX)
            val hasZ6 = prefs.contains(PWR_BIKE_ZONE6_MAX)
            if (!hasZ5 || !hasZ6) {
                val z1 = prefs[PWR_BIKE_ZONE1_MAX] ?: 150
                val z2 = prefs[PWR_BIKE_ZONE2_MAX] ?: 200
                val z3 = prefs[PWR_BIKE_ZONE3_MAX] ?: 250
                val z4 = prefs[PWR_BIKE_ZONE4_MAX] ?: 300
                val (migratedZ5, migratedZ6) = calculateProportionalPowerDefaults(z4)
                
                // Sanity check: verify monotonic consistency
                if (validatePowerZones(z1, z2, z3, z4, migratedZ5, migratedZ6)) {
                    prefs[PWR_BIKE_ZONE5_MAX] = migratedZ5
                    prefs[PWR_BIKE_ZONE6_MAX] = migratedZ6
                } else {
                    // Fallback to canonical defaults if existing data was corrupt
                    prefs[PWR_BIKE_ZONE1_MAX] = 150
                    prefs[PWR_BIKE_ZONE2_MAX] = 200
                    prefs[PWR_BIKE_ZONE3_MAX] = 250
                    prefs[PWR_BIKE_ZONE4_MAX] = 300
                    prefs[PWR_BIKE_ZONE5_MAX] = 350
                    prefs[PWR_BIKE_ZONE6_MAX] = 450
                }
            }
        }
    }

    /**
     * Resets a profile's zone thresholds to canonical default values.
     */
    suspend fun resetToDefaults(zoneType: ZoneType) {
        dataStore.edit { prefs ->
            when (zoneType) {
                ZoneType.HR_RUN -> {
                    prefs[HR_RUN_ZONE1_MAX] = 135
                    prefs[HR_RUN_ZONE2_MAX] = 155
                    prefs[HR_RUN_ZONE3_MAX] = 175
                    prefs[HR_RUN_ZONE4_MAX] = 185
                }
                ZoneType.HR_BIKE -> {
                    prefs[HR_BIKE_ZONE1_MAX] = 130
                    prefs[HR_BIKE_ZONE2_MAX] = 150
                    prefs[HR_BIKE_ZONE3_MAX] = 170
                    prefs[HR_BIKE_ZONE4_MAX] = 180
                }
                ZoneType.PWR_BIKE -> {
                    prefs[PWR_BIKE_ZONE1_MAX] = 150
                    prefs[PWR_BIKE_ZONE2_MAX] = 200
                    prefs[PWR_BIKE_ZONE3_MAX] = 250
                    prefs[PWR_BIKE_ZONE4_MAX] = 300
                    prefs[PWR_BIKE_ZONE5_MAX] = 350
                    prefs[PWR_BIKE_ZONE6_MAX] = 450
                }
            }
        }
    }

    private data class ZoneDisplayKeys(
        val bgKey: Preferences.Key<Boolean>,
        val leftBarKey: Preferences.Key<Boolean>,
        val rightBarKey: Preferences.Key<Boolean>,
        val textColorKey: Preferences.Key<Boolean>
    )

    private fun getZoneDisplayKeys(zoneType: ZoneType): ZoneDisplayKeys {
        return when (zoneType) {
            ZoneType.HR_RUN -> ZoneDisplayKeys(
                HR_RUN_ZONE_SHOW_BACKGROUND,
                HR_RUN_ZONE_SHOW_LEFT_BAR,
                HR_RUN_ZONE_SHOW_RIGHT_BAR,
                HR_RUN_ZONE_SHOW_TEXT_COLOR
            )
            ZoneType.HR_BIKE -> ZoneDisplayKeys(
                HR_BIKE_ZONE_SHOW_BACKGROUND,
                HR_BIKE_ZONE_SHOW_LEFT_BAR,
                HR_BIKE_ZONE_SHOW_RIGHT_BAR,
                HR_BIKE_ZONE_SHOW_TEXT_COLOR
            )
            ZoneType.PWR_BIKE -> ZoneDisplayKeys(
                PWR_BIKE_ZONE_SHOW_BACKGROUND,
                PWR_BIKE_ZONE_SHOW_LEFT_BAR,
                PWR_BIKE_ZONE_SHOW_RIGHT_BAR,
                PWR_BIKE_ZONE_SHOW_TEXT_COLOR
            )
        }
    }

    fun getZoneDisplayOptionsFlow(zoneType: ZoneType): Flow<ZoneDisplayOptions> {
        val keys = getZoneDisplayKeys(zoneType)
        return dataStore.data.map { prefs ->
            ZoneDisplayOptions(
                showBackground = prefs[keys.bgKey] ?: true,
                showLeftBar = prefs[keys.leftBarKey] ?: true,
                showRightBar = prefs[keys.rightBarKey] ?: false,
                showTextColor = prefs[keys.textColorKey] ?: false
            )
        }
    }

    suspend fun saveZoneDisplayOptions(zoneType: ZoneType, options: ZoneDisplayOptions) {
        val keys = getZoneDisplayKeys(zoneType)
        dataStore.edit { prefs ->
            prefs[keys.bgKey] = options.showBackground
            prefs[keys.leftBarKey] = options.showLeftBar
            prefs[keys.rightBarKey] = options.showRightBar
            prefs[keys.textColorKey] = options.showTextColor
        }
    }
}