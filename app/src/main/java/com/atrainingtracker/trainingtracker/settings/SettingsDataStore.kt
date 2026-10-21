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
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.atrainingtracker.trainingtracker.zones.ValidationResult
import com.atrainingtracker.trainingtracker.zones.ZoneValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Immutable in-memory snapshot of all zone thresholds, baselines, and display options.
 * Eagerly cached for instant O(1) non-blocking access (REQ-SET-079).
 */
data class SettingsSnapshot(
    val hrRunZoneMax: List<Int>,
    val hrBikeZoneMax: List<Int>,
    val pwrBikeZoneMax: List<Int>,
    val ftp: Int?,
    val athleteWeightKg: Float,
    val cpWatts: Int?,
    val wPrimeKj: Float?,
    val hrRunDisplayOptions: ZoneDisplayOptions,
    val hrBikeDisplayOptions: ZoneDisplayOptions,
    val pwrBikeDisplayOptions: ZoneDisplayOptions
) {
    companion object {
        val DEFAULT = SettingsSnapshot(
            hrRunZoneMax = listOf(132, 145, 158, 171),
            hrBikeZoneMax = listOf(127, 140, 153, 166),
            pwrBikeZoneMax = listOf(132, 180, 216, 252, 288, 360),
            ftp = null,
            athleteWeightKg = SettingsDataStore.DEFAULT_ATHLETE_WEIGHT_KG,
            cpWatts = null,
            wPrimeKj = null,
            hrRunDisplayOptions = ZoneDisplayOptions(),
            hrBikeDisplayOptions = ZoneDisplayOptions(),
            pwrBikeDisplayOptions = ZoneDisplayOptions()
        )
    }
}

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

        @Volatile
        private var INSTANCE: SettingsDataStore? = null

        @JvmStatic
        fun getInstance(context: Context): SettingsDataStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsDataStore(context.applicationContext).also { INSTANCE = it }
            }
        }

        @VisibleForTesting
        fun setInstanceForTesting(instance: SettingsDataStore?) {
            synchronized(this) {
                INSTANCE = instance
            }
        }

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

        private val HR_RUN_ZONE_SHOW_BACKGROUND = booleanPreferencesKey("hr_run_zone_show_background")
        private val HR_RUN_ZONE_SHOW_LEFT_BAR = booleanPreferencesKey("hr_run_zone_show_left_bar")
        private val HR_RUN_ZONE_SHOW_RIGHT_BAR = booleanPreferencesKey("hr_run_zone_show_right_bar")
        private val HR_RUN_ZONE_SHOW_TEXT_COLOR = booleanPreferencesKey("hr_run_zone_show_text_color")
        private val HR_RUN_ZONE_SHOW_GAUGE = booleanPreferencesKey("hr_run_zone_show_gauge")

        private val HR_BIKE_ZONE_SHOW_BACKGROUND = booleanPreferencesKey("hr_bike_zone_show_background")
        private val HR_BIKE_ZONE_SHOW_LEFT_BAR = booleanPreferencesKey("hr_bike_zone_show_left_bar")
        private val HR_BIKE_ZONE_SHOW_RIGHT_BAR = booleanPreferencesKey("hr_bike_zone_show_right_bar")
        private val HR_BIKE_ZONE_SHOW_TEXT_COLOR = booleanPreferencesKey("hr_bike_zone_show_text_color")
        private val HR_BIKE_ZONE_SHOW_GAUGE = booleanPreferencesKey("hr_bike_zone_show_gauge")

        private val PWR_BIKE_ZONE_SHOW_BACKGROUND = booleanPreferencesKey("pwr_bike_zone_show_background")
        private val PWR_BIKE_ZONE_SHOW_LEFT_BAR = booleanPreferencesKey("pwr_bike_zone_show_left_bar")
        private val PWR_BIKE_ZONE_SHOW_RIGHT_BAR = booleanPreferencesKey("pwr_bike_zone_show_right_bar")
        private val PWR_BIKE_ZONE_SHOW_TEXT_COLOR = booleanPreferencesKey("pwr_bike_zone_show_text_color")
        private val PWR_BIKE_ZONE_SHOW_GAUGE = booleanPreferencesKey("pwr_bike_zone_show_gauge")

        // Automated Zone Calculation (ATT-3188, REQ-UI-373)
        const val CURRENT_ZONE_CALC_VERSION = 1

        val KEY_ZONE_CALC_VERSION = intPreferencesKey("zone_calc_version")
        val KEY_CYCLING_FTP = intPreferencesKey("cycling_ftp")
        val KEY_CYCLING_FTP_UPDATED_AT = longPreferencesKey("cycling_ftp_updated_at")
        val KEY_PWR_BIKE_ZONES_CUSTOMIZED = booleanPreferencesKey("pwr_bike_zones_customized")
        val KEY_HR_RUN_ZONES_CUSTOMIZED = booleanPreferencesKey("hr_run_zones_customized")
        val KEY_HR_BIKE_ZONES_CUSTOMIZED = booleanPreferencesKey("hr_bike_zones_customized")
        val KEY_HR_MAX = intPreferencesKey("hr_max")
        val KEY_HR_REST = intPreferencesKey("hr_rest")
        val KEY_HR_CALC_METHOD = stringPreferencesKey("hr_calc_method")

        // Zone Configuration Onboarding (ATT-3189, REQ-UI-374)
        val KEY_IS_HR_ZONES_CONFIGURED = booleanPreferencesKey("is_hr_zones_configured")
        val KEY_IS_PWR_ZONES_CONFIGURED = booleanPreferencesKey("is_pwr_zones_configured")

        // Athlete Baselines (ATT-1289, REQ-TRK-019, ATT-3704, REQ-UI-391, ATT-3862, REQ-UI-399)
        val KEY_ATHLETE_WEIGHT_KG = floatPreferencesKey("athlete_weight_kg")
        val KEY_ATHLETE_METRICS_SEEDED = booleanPreferencesKey("athlete_metrics_seeded")
        val KEY_CYCLING_CP_WATTS = intPreferencesKey("cycling_cp_watts")
        val KEY_CYCLING_W_PRIME_KJ = floatPreferencesKey("cycling_w_prime_kj")
        const val DEFAULT_ATHLETE_WEIGHT_KG = 0.0f
        const val MIN_ATHLETE_WEIGHT_KG = 30.0f
        const val MAX_ATHLETE_WEIGHT_KG = 300.0f
        const val MIN_ATHLETE_WEIGHT_LBS = 66.0f
        const val MAX_ATHLETE_WEIGHT_LBS = 660.0f
        const val LBS_PER_KG = 2.20462262185f
        const val DEFAULT_CYCLING_FTP = 240
        const val DEFAULT_HR_MAX = 185
        const val DEFAULT_HR_REST = 55
        const val MIN_CP_WATTS = 50
        const val MAX_CP_WATTS = 1000
        const val MIN_W_PRIME_KJ = 5.0f
        const val MAX_W_PRIME_KJ = 60.0f
        const val DEFAULT_W_PRIME_KJ = 20.0f
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
                Zone.ZONE_1 -> 132 // Recovery
                Zone.ZONE_2 -> 145 // Aerobic
                Zone.ZONE_3 -> 158 // Tempo
                Zone.ZONE_4 -> 171 // Threshold
                else -> 0
            }
            ZoneType.HR_BIKE -> when (zone) {
                Zone.ZONE_1 -> 127 // Recovery
                Zone.ZONE_2 -> 140 // Aerobic
                Zone.ZONE_3 -> 153 // Tempo
                Zone.ZONE_4 -> 166 // Threshold
                else -> 0
            }
            // Power defaults (Watts)
            ZoneType.PWR_BIKE -> when (zone) {
                Zone.ZONE_1 -> 132 // Active Recovery
                Zone.ZONE_2 -> 180 // Endurance
                Zone.ZONE_3 -> 216 // Tempo
                Zone.ZONE_4 -> 252 // Threshold (FTP)
                Zone.ZONE_5 -> 288 // VO2 Max
                Zone.ZONE_6 -> 360 // Anaerobic Capacity
            }
        }
    }

    /**
     * Calculates proportional default values for Zone 5 and Zone 6 based on an existing Zone 4 max (FTP).
     */
    fun calculateProportionalPowerDefaults(z4Max: Int): Pair<Int, Int> {
        if (z4Max <= 0) {
            return Pair(288, 360)
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
                val z4Max = prefs[PWR_BIKE_ZONE4_MAX] ?: 252
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

    fun getCustomizedKey(zoneType: ZoneType): Preferences.Key<Boolean> {
        return when (zoneType) {
            ZoneType.HR_RUN -> KEY_HR_RUN_ZONES_CUSTOMIZED
            ZoneType.HR_BIKE -> KEY_HR_BIKE_ZONES_CUSTOMIZED
            ZoneType.PWR_BIKE -> KEY_PWR_BIKE_ZONES_CUSTOMIZED
        }
    }

    val ftpFlow: Flow<Int?> = dataStore.data.map { prefs ->
        prefs[KEY_CYCLING_FTP]?.takeIf { it > 0 }
    }

    val ftpUpdatedAtFlow: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_CYCLING_FTP_UPDATED_AT]?.takeIf { it > 0L }
    }

    val hrMaxFlow: Flow<Int?> = dataStore.data.map { prefs ->
        prefs[KEY_HR_MAX]?.takeIf { it > 0 }
    }

    val hrRestFlow: Flow<Int?> = dataStore.data.map { prefs ->
        prefs[KEY_HR_REST]?.takeIf { it > 0 }
    }

    val hrCalcMethodFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_HR_CALC_METHOD]
    }

    fun isZoneCustomizedFlow(zoneType: ZoneType): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[getCustomizedKey(zoneType)] ?: false
    }

    val zoneCalcVersionFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_ZONE_CALC_VERSION] ?: CURRENT_ZONE_CALC_VERSION
    }

    /**
     * Flow emitting the athlete's body weight in kilograms (REQ-TRK-019).
     * Defaults to 0.0f when unconfigured.
     */
    val athleteWeightKgFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_ATHLETE_WEIGHT_KG] ?: DEFAULT_ATHLETE_WEIGHT_KG
    }

    /**
     * Sets the athlete's body weight in kilograms (REQ-TRK-019).
     * Validates and clamps input between [MIN_ATHLETE_WEIGHT_KG] and [MAX_ATHLETE_WEIGHT_KG],
     * or sets to 0.0f if non-positive.
     */
    suspend fun setAthleteWeightKg(weightKg: Float) {
        dataStore.edit { prefs ->
            if (weightKg <= 0.0f) {
                prefs[KEY_ATHLETE_WEIGHT_KG] = DEFAULT_ATHLETE_WEIGHT_KG
            } else {
                prefs[KEY_ATHLETE_WEIGHT_KG] = weightKg.coerceIn(MIN_ATHLETE_WEIGHT_KG, MAX_ATHLETE_WEIGHT_KG)
            }
        }
    }

    /**
     * Flow emitting whether initial athlete metrics history seeding from DataStore has completed (`REQ-UI-391.3`).
     */
    val athleteMetricsSeededFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ATHLETE_METRICS_SEEDED] ?: false
    }

    /**
     * Marks initial athlete metrics history seeding as completed (`REQ-UI-391.3`).
     */
    suspend fun setAthleteMetricsSeeded(seeded: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ATHLETE_METRICS_SEEDED] = seeded
        }
    }

    /**
     * Unidirectionally updates or clears the athlete's Resting Heart Rate (`KEY_HR_REST`) in DataStore (`REQ-UI-391.3`).
     */
    suspend fun setHrRest(hrRest: Int?) {
        dataStore.edit { prefs ->
            if (hrRest == null || hrRest <= 0) {
                prefs.remove(KEY_HR_REST)
            } else {
                prefs[KEY_HR_REST] = hrRest.coerceIn(
                    com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath.MIN_HR_REST,
                    com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath.MAX_HR_REST
                )
            }
        }
    }

    /**
     * Unidirectionally updates or clears the athlete's Functional Threshold Power (`KEY_CYCLING_FTP` and `KEY_CYCLING_FTP_UPDATED_AT`) in DataStore (`REQ-UI-391.3`).
     */
    suspend fun setFtp(ftp: Int?, updatedAtMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            if (ftp == null || ftp <= 0) {
                prefs.remove(KEY_CYCLING_FTP)
                prefs.remove(KEY_CYCLING_FTP_UPDATED_AT)
            } else {
                prefs[KEY_CYCLING_FTP] = ftp.coerceIn(
                    com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath.MIN_FTP,
                    com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath.MAX_FTP
                )
                if (updatedAtMillis > 0L) {
                    prefs[KEY_CYCLING_FTP_UPDATED_AT] = updatedAtMillis
                }
            }
        }
    }

    /**
     * Flow emitting the athlete's Critical Power (CP) in Watts (`REQ-UI-399.1`), or `null` when unconfigured.
     */
    val cpWattsFlow: Flow<Int?> = dataStore.data.map { prefs ->
        prefs[KEY_CYCLING_CP_WATTS]?.takeIf { it > 0 }
    }

    /**
     * Flow emitting the athlete's Anaerobic Work Capacity ($W'$) in kilojoules (`REQ-UI-399.1`), or `null` when unconfigured.
     */
    val wPrimeKjFlow: Flow<Float?> = dataStore.data.map { prefs ->
        prefs[KEY_CYCLING_W_PRIME_KJ]?.takeIf { it > 0.0f }
    }

    /**
     * Unidirectionally updates or clears the athlete's Critical Power (`KEY_CYCLING_CP_WATTS`) in DataStore (`REQ-UI-399.1`).
     */
    suspend fun setCpWatts(cpWatts: Int?) {
        dataStore.edit { prefs ->
            if (cpWatts == null || cpWatts <= 0) {
                prefs.remove(KEY_CYCLING_CP_WATTS)
            } else {
                prefs[KEY_CYCLING_CP_WATTS] = cpWatts.coerceIn(MIN_CP_WATTS, MAX_CP_WATTS)
            }
        }
    }

    /**
     * Unidirectionally updates or clears the athlete's Anaerobic Work Capacity $W'$ (`KEY_CYCLING_W_PRIME_KJ`) in DataStore (`REQ-UI-399.1`).
     */
    suspend fun setWPrimeKj(wPrimeKj: Float?) {
        dataStore.edit { prefs ->
            if (wPrimeKj == null || wPrimeKj <= 0.0f) {
                prefs.remove(KEY_CYCLING_W_PRIME_KJ)
            } else {
                prefs[KEY_CYCLING_W_PRIME_KJ] = wPrimeKj.coerceIn(MIN_W_PRIME_KJ, MAX_W_PRIME_KJ)
            }
        }
    }

    /**
     * Flow emitting whether Heart Rate zones have been customized, calculated, or explicitly configured (REQ-UI-374.2).
     * If unset in DataStore, uses migration fallback heuristic checking existing baselines or custom flags.
     */
    val isHrZonesConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        val explicit = prefs[KEY_IS_HR_ZONES_CONFIGURED]
        if (explicit != null) {
            explicit
        } else {
            val hrMax = prefs[KEY_HR_MAX]
            val hrRunCustomized = prefs[KEY_HR_RUN_ZONES_CUSTOMIZED] ?: false
            val hrBikeCustomized = prefs[KEY_HR_BIKE_ZONES_CUSTOMIZED] ?: false
            (hrMax != null && hrMax > 0) || hrRunCustomized || hrBikeCustomized
        }
    }

    /**
     * Flow emitting whether Cycling Power zones have been customized, calculated, or explicitly configured (REQ-UI-374.2).
     * If unset in DataStore, uses migration fallback heuristic checking existing FTP or custom flags.
     */
    val isPwrZonesConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        val explicit = prefs[KEY_IS_PWR_ZONES_CONFIGURED]
        if (explicit != null) {
            explicit
        } else {
            val ftp = prefs[KEY_CYCLING_FTP]
            val pwrCustomized = prefs[KEY_PWR_BIKE_ZONES_CUSTOMIZED] ?: false
            (ftp != null && ftp > 0) || pwrCustomized
        }
    }

    suspend fun setHrZonesConfigured(configured: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_HR_ZONES_CONFIGURED] = configured
        }
    }

    suspend fun setPwrZonesConfigured(configured: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_PWR_ZONES_CONFIGURED] = configured
        }
    }

    // --- NON-BLOCKING IN-MEMORY SNAPSHOT & SYNCHRONOUS GETTERS (REQ-SET-079) ---

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun mapSettingsSnapshot(prefs: Preferences): SettingsSnapshot {
        val hrRunZ1 = prefs[HR_RUN_ZONE1_MAX] ?: 132
        val hrRunZ2 = prefs[HR_RUN_ZONE2_MAX] ?: 145
        val hrRunZ3 = prefs[HR_RUN_ZONE3_MAX] ?: 158
        val hrRunZ4 = prefs[HR_RUN_ZONE4_MAX] ?: 171

        val hrBikeZ1 = prefs[HR_BIKE_ZONE1_MAX] ?: 127
        val hrBikeZ2 = prefs[HR_BIKE_ZONE2_MAX] ?: 140
        val hrBikeZ3 = prefs[HR_BIKE_ZONE3_MAX] ?: 153
        val hrBikeZ4 = prefs[HR_BIKE_ZONE4_MAX] ?: 166

        val pwrBikeZ1 = prefs[PWR_BIKE_ZONE1_MAX] ?: 132
        val pwrBikeZ2 = prefs[PWR_BIKE_ZONE2_MAX] ?: 180
        val pwrBikeZ3 = prefs[PWR_BIKE_ZONE3_MAX] ?: 216
        val pwrBikeZ4 = prefs[PWR_BIKE_ZONE4_MAX] ?: 252

        val pwrBikeZ5 = if (prefs.contains(PWR_BIKE_ZONE5_MAX)) {
            prefs[PWR_BIKE_ZONE5_MAX] ?: 288
        } else if (prefs.contains(PWR_BIKE_ZONE4_MAX)) {
            calculateProportionalPowerDefaults(pwrBikeZ4).first
        } else {
            288
        }

        val pwrBikeZ6 = if (prefs.contains(PWR_BIKE_ZONE6_MAX)) {
            prefs[PWR_BIKE_ZONE6_MAX] ?: 360
        } else if (prefs.contains(PWR_BIKE_ZONE4_MAX)) {
            calculateProportionalPowerDefaults(pwrBikeZ4).second
        } else {
            360
        }

        val hrRunKeys = getZoneDisplayKeys(ZoneType.HR_RUN)
        val hrRunDisplay = ZoneDisplayOptions(
            showBackground = prefs[hrRunKeys.bgKey] ?: true,
            showLeftBar = prefs[hrRunKeys.leftBarKey] ?: true,
            showRightBar = prefs[hrRunKeys.rightBarKey] ?: false,
            showTextColor = prefs[hrRunKeys.textColorKey] ?: false,
            showGauge = prefs[hrRunKeys.gaugeKey] ?: false
        )

        val hrBikeKeys = getZoneDisplayKeys(ZoneType.HR_BIKE)
        val hrBikeDisplay = ZoneDisplayOptions(
            showBackground = prefs[hrBikeKeys.bgKey] ?: true,
            showLeftBar = prefs[hrBikeKeys.leftBarKey] ?: true,
            showRightBar = prefs[hrBikeKeys.rightBarKey] ?: false,
            showTextColor = prefs[hrBikeKeys.textColorKey] ?: false,
            showGauge = prefs[hrBikeKeys.gaugeKey] ?: false
        )

        val pwrBikeKeys = getZoneDisplayKeys(ZoneType.PWR_BIKE)
        val pwrBikeDisplay = ZoneDisplayOptions(
            showBackground = prefs[pwrBikeKeys.bgKey] ?: true,
            showLeftBar = prefs[pwrBikeKeys.leftBarKey] ?: true,
            showRightBar = prefs[pwrBikeKeys.rightBarKey] ?: false,
            showTextColor = prefs[pwrBikeKeys.textColorKey] ?: false,
            showGauge = prefs[pwrBikeKeys.gaugeKey] ?: false
        )

        return SettingsSnapshot(
            hrRunZoneMax = listOf(hrRunZ1, hrRunZ2, hrRunZ3, hrRunZ4),
            hrBikeZoneMax = listOf(hrBikeZ1, hrBikeZ2, hrBikeZ3, hrBikeZ4),
            pwrBikeZoneMax = listOf(pwrBikeZ1, pwrBikeZ2, pwrBikeZ3, pwrBikeZ4, pwrBikeZ5, pwrBikeZ6),
            ftp = prefs[KEY_CYCLING_FTP]?.takeIf { it > 0 },
            athleteWeightKg = prefs[KEY_ATHLETE_WEIGHT_KG] ?: DEFAULT_ATHLETE_WEIGHT_KG,
            cpWatts = prefs[KEY_CYCLING_CP_WATTS]?.takeIf { it > 0 },
            wPrimeKj = prefs[KEY_CYCLING_W_PRIME_KJ]?.takeIf { it > 0.0f },
            hrRunDisplayOptions = hrRunDisplay,
            hrBikeDisplayOptions = hrBikeDisplay,
            pwrBikeDisplayOptions = pwrBikeDisplay
        )
    }

    val snapshotFlow: Flow<SettingsSnapshot> = dataStore.data.map { prefs ->
        mapSettingsSnapshot(prefs)
    }

    val snapshotStateFlow: StateFlow<SettingsSnapshot> by lazy {
        snapshotFlow.stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsSnapshot.DEFAULT
        )
    }

    fun getZoneMaxSync(zoneType: ZoneType, zoneIndex: Int): Int {
        val snapshot = snapshotStateFlow.value
        return when (zoneType) {
            ZoneType.HR_RUN -> snapshot.hrRunZoneMax.getOrElse(zoneIndex - 1) { 0 }
            ZoneType.HR_BIKE -> snapshot.hrBikeZoneMax.getOrElse(zoneIndex - 1) { 0 }
            ZoneType.PWR_BIKE -> snapshot.pwrBikeZoneMax.getOrElse(zoneIndex - 1) { 0 }
        }
    }

    fun getZoneDisplayOptionsSync(zoneType: ZoneType): ZoneDisplayOptions {
        val snapshot = snapshotStateFlow.value
        return when (zoneType) {
            ZoneType.HR_RUN -> snapshot.hrRunDisplayOptions
            ZoneType.HR_BIKE -> snapshot.hrBikeDisplayOptions
            ZoneType.PWR_BIKE -> snapshot.pwrBikeDisplayOptions
        }
    }

    fun getFtpSync(): Int? = snapshotStateFlow.value.ftp
    fun getAthleteWeightKgSync(): Float = snapshotStateFlow.value.athleteWeightKg
    fun getCpWattsSync(): Int? = snapshotStateFlow.value.cpWatts
    fun getWPrimeKjSync(): Float? = snapshotStateFlow.value.wPrimeKj

    fun launchSetAthleteWeightKg(weightKg: Float) {
        appScope.launch {
            setAthleteWeightKg(weightKg)
        }
    }

    // --- WRITE FUNCTIONS ---

    suspend fun saveHrZoneMax(zoneType: ZoneType, zone: Zone, value: Int) {
        val key = getKey(zoneType, zone) ?: return
        val customizedKey = getCustomizedKey(zoneType)
        dataStore.edit { prefs ->
            prefs[key] = value
            prefs[customizedKey] = true
            if (zoneType == ZoneType.PWR_BIKE) {
                prefs[KEY_IS_PWR_ZONES_CONFIGURED] = true
            } else {
                prefs[KEY_IS_HR_ZONES_CONFIGURED] = true
            }
        }
    }

    /**
     * Atomically saves calculated cycling power zones and FTP baseline conforming to REQ-UI-373.1, REQ-UI-373.5, REQ-UI-374.2.
     * All writes occur within a single dataStore.edit { } ACID transaction.
     */
    suspend fun saveCalculatedPowerZones(ftp: Int, cutoffs: List<Int>) {
        require(ZoneValidator.validateFtp(ftp) is ValidationResult.Valid) {
            "FTP must be between ${com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath.MIN_FTP} and ${com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath.MAX_FTP} W, received: $ftp"
        }
        ZoneValidator.validatePowerZoneStack(cutoffs)

        dataStore.edit { prefs ->
            prefs[KEY_CYCLING_FTP] = ftp
            prefs[KEY_CYCLING_FTP_UPDATED_AT] = System.currentTimeMillis()
            prefs[KEY_PWR_BIKE_ZONES_CUSTOMIZED] = false
            prefs[KEY_IS_PWR_ZONES_CONFIGURED] = true
            prefs[KEY_ZONE_CALC_VERSION] = CURRENT_ZONE_CALC_VERSION

            prefs[PWR_BIKE_ZONE1_MAX] = cutoffs[0]
            prefs[PWR_BIKE_ZONE2_MAX] = cutoffs[1]
            prefs[PWR_BIKE_ZONE3_MAX] = cutoffs[2]
            prefs[PWR_BIKE_ZONE4_MAX] = cutoffs[3]
            prefs[PWR_BIKE_ZONE5_MAX] = cutoffs[4]
            prefs[PWR_BIKE_ZONE6_MAX] = cutoffs[5]
        }
    }

    /**
     * Atomically saves calculated heart rate zones and baselines conforming to REQ-UI-373.2, REQ-UI-373.5, REQ-UI-374.2.
     * All writes occur within a single dataStore.edit { } ACID transaction.
     */
    suspend fun saveCalculatedHeartRateZones(
        zoneType: ZoneType,
        hrMax: Int,
        hrRest: Int?,
        method: String,
        cutoffs: List<Int>
    ) {
        require(zoneType != ZoneType.PWR_BIKE) {
            "Heart rate calculation cannot be applied to PWR_BIKE profile"
        }
        require(ZoneValidator.validateHrBaselines(hrMax, hrRest) is ValidationResult.Valid) {
            "Invalid HR baselines: hrMax=$hrMax, hrRest=$hrRest"
        }
        ZoneValidator.validateHeartRateZoneStack(cutoffs)

        val customizedKey = getCustomizedKey(zoneType)
        val (k1, k2, k3, k4) = if (zoneType == ZoneType.HR_BIKE) {
            listOf(HR_BIKE_ZONE1_MAX, HR_BIKE_ZONE2_MAX, HR_BIKE_ZONE3_MAX, HR_BIKE_ZONE4_MAX)
        } else {
            listOf(HR_RUN_ZONE1_MAX, HR_RUN_ZONE2_MAX, HR_RUN_ZONE3_MAX, HR_RUN_ZONE4_MAX)
        }

        dataStore.edit { prefs ->
            prefs[KEY_HR_MAX] = hrMax
            if (hrRest != null && hrRest > 0) {
                prefs[KEY_HR_REST] = hrRest
            }
            prefs[KEY_HR_CALC_METHOD] = method
            prefs[customizedKey] = false
            prefs[KEY_IS_HR_ZONES_CONFIGURED] = true
            prefs[KEY_ZONE_CALC_VERSION] = CURRENT_ZONE_CALC_VERSION

            prefs[k1] = cutoffs[0]
            prefs[k2] = cutoffs[1]
            prefs[k3] = cutoffs[2]
            prefs[k4] = cutoffs[3]
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
                val z1 = prefs[PWR_BIKE_ZONE1_MAX] ?: 132
                val z2 = prefs[PWR_BIKE_ZONE2_MAX] ?: 180
                val z3 = prefs[PWR_BIKE_ZONE3_MAX] ?: 216
                val z4 = prefs[PWR_BIKE_ZONE4_MAX] ?: 252
                val (migratedZ5, migratedZ6) = calculateProportionalPowerDefaults(z4)
                
                // Sanity check: verify monotonic consistency
                if (validatePowerZones(z1, z2, z3, z4, migratedZ5, migratedZ6)) {
                    prefs[PWR_BIKE_ZONE5_MAX] = migratedZ5
                    prefs[PWR_BIKE_ZONE6_MAX] = migratedZ6
                } else {
                    // Fallback to canonical defaults if existing data was corrupt
                    prefs[PWR_BIKE_ZONE1_MAX] = 132
                    prefs[PWR_BIKE_ZONE2_MAX] = 180
                    prefs[PWR_BIKE_ZONE3_MAX] = 216
                    prefs[PWR_BIKE_ZONE4_MAX] = 252
                    prefs[PWR_BIKE_ZONE5_MAX] = 288
                    prefs[PWR_BIKE_ZONE6_MAX] = 360
                }
            }
        }
    }

    /**
     * Resets a profile's zone thresholds to canonical default values.
     */
    suspend fun resetToDefaults(zoneType: ZoneType) {
        dataStore.edit { prefs ->
            prefs[getCustomizedKey(zoneType)] = false
            when (zoneType) {
                ZoneType.HR_RUN -> {
                    prefs[HR_RUN_ZONE1_MAX] = 132
                    prefs[HR_RUN_ZONE2_MAX] = 145
                    prefs[HR_RUN_ZONE3_MAX] = 158
                    prefs[HR_RUN_ZONE4_MAX] = 171
                }
                ZoneType.HR_BIKE -> {
                    prefs[HR_BIKE_ZONE1_MAX] = 127
                    prefs[HR_BIKE_ZONE2_MAX] = 140
                    prefs[HR_BIKE_ZONE3_MAX] = 153
                    prefs[HR_BIKE_ZONE4_MAX] = 166
                }
                ZoneType.PWR_BIKE -> {
                    prefs[PWR_BIKE_ZONE1_MAX] = 132
                    prefs[PWR_BIKE_ZONE2_MAX] = 180
                    prefs[PWR_BIKE_ZONE3_MAX] = 216
                    prefs[PWR_BIKE_ZONE4_MAX] = 252
                    prefs[PWR_BIKE_ZONE5_MAX] = 288
                    prefs[PWR_BIKE_ZONE6_MAX] = 360
                }
            }
        }
    }

    private data class ZoneDisplayKeys(
        val bgKey: Preferences.Key<Boolean>,
        val leftBarKey: Preferences.Key<Boolean>,
        val rightBarKey: Preferences.Key<Boolean>,
        val textColorKey: Preferences.Key<Boolean>,
        val gaugeKey: Preferences.Key<Boolean>
    )

    private fun getZoneDisplayKeys(zoneType: ZoneType): ZoneDisplayKeys {
        return when (zoneType) {
            ZoneType.HR_RUN -> ZoneDisplayKeys(
                HR_RUN_ZONE_SHOW_BACKGROUND,
                HR_RUN_ZONE_SHOW_LEFT_BAR,
                HR_RUN_ZONE_SHOW_RIGHT_BAR,
                HR_RUN_ZONE_SHOW_TEXT_COLOR,
                HR_RUN_ZONE_SHOW_GAUGE
            )
            ZoneType.HR_BIKE -> ZoneDisplayKeys(
                HR_BIKE_ZONE_SHOW_BACKGROUND,
                HR_BIKE_ZONE_SHOW_LEFT_BAR,
                HR_BIKE_ZONE_SHOW_RIGHT_BAR,
                HR_BIKE_ZONE_SHOW_TEXT_COLOR,
                HR_BIKE_ZONE_SHOW_GAUGE
            )
            ZoneType.PWR_BIKE -> ZoneDisplayKeys(
                PWR_BIKE_ZONE_SHOW_BACKGROUND,
                PWR_BIKE_ZONE_SHOW_LEFT_BAR,
                PWR_BIKE_ZONE_SHOW_RIGHT_BAR,
                PWR_BIKE_ZONE_SHOW_TEXT_COLOR,
                PWR_BIKE_ZONE_SHOW_GAUGE
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
                showTextColor = prefs[keys.textColorKey] ?: false,
                showGauge = prefs[keys.gaugeKey] ?: false
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
            prefs[keys.gaugeKey] = options.showGauge
        }
    }
}