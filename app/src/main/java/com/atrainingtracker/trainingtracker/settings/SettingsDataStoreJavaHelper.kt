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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A helper object to allow synchronous, non-blocking access to DataStore values from Java code.
 * (REQ-SET-079 / ATT-3999).
 * All queries execute in O(1) from thread-safe in-memory StateFlow caches without blocking caller threads.
 */
object SettingsDataStoreJavaHelper {

    /**
     * Generic helper to get a max zone value for a specific profile (Run, Bike, Power).
     * Non-blocking O(1) in-memory lookup.
     * @param context The application context
     * @param zoneType The ZoneType
     * @param zoneIndex The zone number (1-4 for HR, 1-6 for Power).
     * @return The max value for that zone.
     */
    @JvmStatic
    fun getZoneMax(context: Context, zoneType: SettingsDataStore.ZoneType, zoneIndex: Int): Int {
        return SettingsDataStore.getInstance(context).getZoneMaxSync(zoneType, zoneIndex)
    }

    /**
     * Resolves effective zone boundaries for [zoneType] at [workoutStartTimeMs] (REQ-SET-082.3).
     * If [workoutStartTimeMs] > 0, queries AthleteMetricsDatabaseManager for the snapshot active at that time.
     * Falls back to current DataStore zone settings if no snapshot exists or if an error occurs.
     */
    @JvmStatic
    fun getEffectiveZoneBoundaries(
        context: Context,
        zoneType: SettingsDataStore.ZoneType,
        workoutStartTimeMs: Long
    ): List<Int> {
        if (workoutStartTimeMs > 0L) {
            try {
                val dbManager = com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager.getInstance(context)
                val snapshot = dbManager.getEffectiveZoneSnapshot(zoneType, workoutStartTimeMs)
                if (snapshot != null && snapshot.boundaries.size == zoneType.boundaryCount) {
                    return snapshot.boundaries
                }
            } catch (_: Throwable) {
                // Fallback for mocked unit tests or uninitialized database
            }
        }
        val count = zoneType.boundaryCount
        return (1..count).map { idx -> getZoneMax(context, zoneType, idx) }
    }

    /**
     * Synchronous bridge for legacy Java or non-coroutine callers to fetch zone display options.
     * Non-blocking O(1) in-memory lookup.
     */
    @JvmStatic
    fun getZoneDisplayOptions(context: Context, zoneType: SettingsDataStore.ZoneType): ZoneDisplayOptions {
        return SettingsDataStore.getInstance(context).getZoneDisplayOptionsSync(zoneType)
    }

    /**
     * Synchronous bridge for legacy Java or non-coroutine callers to fetch HRV zone display options.
     * Non-blocking O(1) in-memory lookup.
     */
    @JvmStatic
    fun getHrvZoneDisplayOptions(context: Context): ZoneDisplayOptions {
        return SettingsDataStore.getInstance(context).getHrvZoneDisplayOptionsSync()
    }

    /**
     * Synchronous bridge for legacy Java sensor drivers to fetch the active tuning configuration.
     * Non-blocking O(1) in-memory lookup from TuningPreferencesDataStore.
     */
    private fun isRealFilesDir(dir: java.io.File?): Boolean {
        return dir != null &&
            dir.javaClass == java.io.File::class.java &&
            java.io.File(dir, "datastore").path.isNotEmpty()
    }

    @JvmStatic
    fun getTuningConfig(context: Context): TuningConfig {
        return try {
            val appCtx = context.applicationContext ?: context
            val canReadStore = TuningPreferencesDataStore.hasInitializedInstance() ||
                isRealFilesDir(appCtx.filesDir)
            if (canReadStore) {
                TuningPreferencesDataStore.getInstance(context).getTuningConfig()
            } else {
                TuningConfig()
            }
        } catch (_: Throwable) {
            TuningConfig()
        }
    }

    /**
     * Synchronous bridge to fetch the GPS horizontal accuracy rejection threshold in meters.
     */
    @JvmStatic
    fun getGpsAccuracyThreshold(context: Context): Float {
        return getTuningConfig(context).gpsAccuracyThresholdMeters
    }

    /**
     * Synchronous bridge to fetch the barometric altitude filter moving average window in seconds.
     */
    @JvmStatic
    fun getAltitudeFilterWindow(context: Context): Int {
        return getTuningConfig(context).altitudeFilterWindowSec
    }

    /**
     * Synchronous bridge to fetch the minimum speed for gradient calculation in m/s.
     */
    @JvmStatic
    fun getSlopeMinSpeed(context: Context): Float {
        return getTuningConfig(context).slopeMinSpeedMps
    }

    /**
     * Synchronous bridge to fetch whether post-workout ERTS smoothing is enabled (REQ-FIL-017.3, ATT-7750).
     */
    @JvmStatic
    fun isPostWorkoutErtsSmoothingEnabled(context: Context): Boolean {
        return getTuningConfig(context).postWorkoutErtsSmoothingEnabled
    }

    /**
     * Synchronous bridge to fetch the configured default pool length in meters for swimming FIT export (REQ-SET-107.3).
     */
    @JvmStatic
    fun getDefaultPoolLengthMeters(context: Context): Float {
        return getTuningConfig(context).defaultPoolLengthMeters
    }

    /**
     * Synchronous bridge to fetch the configured default pool length display unit for swimming FIT export (REQ-SET-107.3).
     */
    @JvmStatic
    fun getDefaultPoolLengthUnit(context: Context): PoolLengthUnit {
        return getTuningConfig(context).defaultPoolLengthUnit
    }

    /**
     * Synchronous bridge to fetch athlete Functional Threshold Power (FTP) in Watts.
     * Returns null if unconfigured or <= 0. Non-blocking in-memory read.
     */
    @JvmStatic
    fun getFtp(context: Context): Int? {
        return try {
            SettingsDataStore.getInstance(context).getFtpSync()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Synchronous bridge to determine if auto-pause is enabled for the specified sport type.
     */
    @JvmStatic
    fun isAutoPauseEnabled(context: Context, sportType: BSportType): Boolean {
        val config = getTuningConfig(context)
        return when (sportType) {
            BSportType.BIKE -> config.autoPauseBikeEnabled
            BSportType.RUN -> config.autoPauseRunEnabled
            else -> config.autoPauseOtherEnabled
        }
    }

    /**
     * Synchronous bridge to fetch the auto-pause speed threshold in km/h.
     */
    @JvmStatic
    fun getAutoPauseSpeedThresholdKmh(context: Context): Float {
        return getTuningConfig(context).autoPauseSpeedThresholdKmh
    }

    /**
     * Synchronous bridge to fetch the auto-pause debounce window in seconds.
     */
    @JvmStatic
    fun getAutoPauseDebounceSec(context: Context): Int {
        return getTuningConfig(context).autoPauseDebounceSec
    }

    /**
     * Synchronous bridge to fetch the full sport-specific auto-pause configuration.
     * Evaluates active unit mode (Metric vs Imperial) via MyHelper.getUnits().
     */
    @JvmStatic
    fun getAutoPauseConfig(context: Context): AutoPauseConfig {
        val tuningConfig = getTuningConfig(context)
        val isMetric = (TrainingApplication.getUnit() == MyUnits.METRIC)
        return AutoPauseConfig(
            bikeEnabled = tuningConfig.autoPauseBikeEnabled,
            runEnabled = tuningConfig.autoPauseRunEnabled,
            otherEnabled = tuningConfig.autoPauseOtherEnabled,
            startInAutoPauseEnabled = tuningConfig.startInAutoPauseEnabled,
            bikeSpeedThreshold = tuningConfig.autoPauseBikeSpeedThreshold,
            bikeDebounceSec = tuningConfig.autoPauseBikeDebounceSec,
            bikeResumeDebounceSec = tuningConfig.autoPauseBikeResumeDebounceSec,
            runSpeedThreshold = tuningConfig.autoPauseRunSpeedThreshold,
            runDebounceSec = tuningConfig.autoPauseRunDebounceSec,
            runResumeDebounceSec = tuningConfig.autoPauseRunResumeDebounceSec,
            otherSpeedThreshold = tuningConfig.autoPauseOtherSpeedThreshold,
            otherDebounceSec = tuningConfig.autoPauseOtherDebounceSec,
            otherResumeDebounceSec = tuningConfig.autoPauseOtherResumeDebounceSec,
            isMetric = isMetric
        )
    }

    /**
     * Synchronous bridge to fetch athlete body weight in kilograms (REQ-TRK-019).
     * Returns 0.0f if unconfigured or <= 0. Non-blocking in-memory read.
     */
    @JvmStatic
    fun getAthleteWeightKg(context: Context): Float {
        return try {
            SettingsDataStore.getInstance(context).getAthleteWeightKgSync()
        } catch (_: Throwable) {
            0.0f
        }
    }

    /**
     * Asynchronous bridge to persist athlete body weight in kilograms (REQ-TRK-019).
     * Dispatches to application coroutine scope without blocking the caller thread.
     */
    @JvmStatic
    fun setAthleteWeightKg(context: Context, weightKg: Float) {
        try {
            SettingsDataStore.getInstance(context).launchSetAthleteWeightKg(weightKg)
        } catch (_: Throwable) {
            // ignore in tests or headless contexts
        }
    }

    /**
     * Synchronous bridge to fetch configured Critical Power (CP) in Watts (`REQ-UI-399.1`), or `null` if unconfigured.
     * Non-blocking in-memory read.
     */
    @JvmStatic
    fun getCpWatts(context: Context): Int? {
        return try {
            SettingsDataStore.getInstance(context).getCpWattsSync()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Canonical resolver for effective Critical Power (CP) in Watts (`REQ-UI-399.1`):
     * returns configured `cpWatts` if $> 0$, otherwise falls back to configured `getFtp(context)`.
     */
    @JvmStatic
    fun getEffectiveCpWatts(context: Context): Int? {
        return getCpWatts(context)?.takeIf { it > 0 } ?: getFtp(context)?.takeIf { it > 0 }
    }

    /**
     * Synchronous bridge to fetch configured Anaerobic Work Capacity ($W'$) in kJ (`REQ-UI-399.1`), or `null` if unconfigured.
     * Non-blocking in-memory read.
     */
    @JvmStatic
    fun getWPrimeKj(context: Context): Float? {
        return try {
            SettingsDataStore.getInstance(context).getWPrimeKjSync()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Canonical resolver for effective Anaerobic Work Capacity ($W'$) in kJ (`REQ-UI-399.1`):
     * returns configured `wPrimeKj` if $> 0$, otherwise defaults to `20.0f` kJ.
     */
    @JvmStatic
    fun getEffectiveWPrimeKj(context: Context): Float {
        return getWPrimeKj(context)?.takeIf { it > 0.0f } ?: SettingsDataStore.DEFAULT_W_PRIME_KJ
    }

    /**
     * Resolves the athlete's Functional Threshold Power (FTP, in W) effective at [timestampMs] (`REQ-MIG-039.2`):
     * queries [com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager.getEntryEffectiveAt]
     * for [com.atrainingtracker.trainingtracker.database.AthleteMetricType.FTP_WATTS], falling back to [getFtp].
     */
    @JvmStatic
    fun resolveEffectiveFtpAt(context: Context, timestampMs: Long?): Int? {
        if (timestampMs != null && timestampMs > 0L) {
            try {
                val entry = com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager
                    .getInstance(context)
                    .getEntryEffectiveAt(
                        com.atrainingtracker.trainingtracker.database.AthleteMetricType.FTP_WATTS,
                        timestampMs
                    )
                if (entry != null && entry.value > 0.0) {
                    return Math.round(entry.value).toInt().takeIf { it > 0 }
                }
            } catch (_: Throwable) {
                // Fallback to DataStore in headless or unit test environments
            }
        }
        return getFtp(context)?.takeIf { it > 0 }
    }

    /**
     * Resolves the athlete's body weight (in kg) effective at [timestampMs] (`REQ-MIG-039.2`):
     * queries [com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager.getEntryEffectiveAt]
     * for [com.atrainingtracker.trainingtracker.database.AthleteMetricType.WEIGHT_KG], falling back to [getAthleteWeightKg].
     */
    @JvmStatic
    fun resolveEffectiveWeightKgAt(context: Context, timestampMs: Long?): Float {
        if (timestampMs != null && timestampMs > 0L) {
            try {
                val entry = com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager
                    .getInstance(context)
                    .getEntryEffectiveAt(
                        com.atrainingtracker.trainingtracker.database.AthleteMetricType.WEIGHT_KG,
                        timestampMs
                    )
                if (entry != null && entry.value > 0.0) {
                    return entry.value.toFloat()
                }
            } catch (_: Throwable) {
                // Fallback to DataStore in headless or unit test environments
            }
        }
        return getAthleteWeightKg(context)
    }

    /**
     * Synchronous bridge to retrieve the raw sensor telemetry retention policy (REQ-SET-117.3, ATT-8019).
     */
    @JvmStatic
    fun getRawSensorRetentionPolicy(context: Context): RawSensorRetentionPolicy {
        return try {
            TuningPreferencesDataStore.getInstance(context).rawSensorRetentionPolicy.value
        } catch (_: Throwable) {
            TuningPreferencesDefaults.RAW_SENSOR_RETENTION_POLICY
        }
    }

    /**
     * Synchronous bridge to retrieve whether opt-in smartphone raw sensor sidecar recording is enabled
     * (REQ-EXP-045.1, ATT-9348). Guards plain-JVM MockK contexts lacking a real `filesDir` and
     * awaits the first persisted DataStore emission so cold-start / post-crash lifecycle checks
     * never observe a stale `StateFlow` `initialValue = false`.
     */
    @JvmStatic
    fun isRecordSmartphoneRawTelemetryEnabled(context: Context): Boolean {
        return try {
            val appCtx = context.applicationContext ?: context
            val canReadStore = TuningPreferencesDataStore.hasInitializedInstance() ||
                isRealFilesDir(appCtx.filesDir)
            if (canReadStore) {
                runBlocking {
                    TuningPreferencesDataStore.getInstance(context).recordSmartphoneRawTelemetryFlow.first()
                }
            } else {
                TuningPreferencesDefaults.DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED
            }
        } catch (_: Throwable) {
            TuningPreferencesDefaults.DEFAULT_RECORD_SMARTPHONE_RAW_TELEMETRY_ENABLED
        }
    }

    /**
     * Synchronous bridge for BTLERadarDevice to check whether developer forced legacy V1 BLE protocol.
     * Strictly returns false in release builds (BuildConfig.DEBUG == false). (ATT-7659 / REQ-CON-030.8)
     */
    @JvmStatic
    fun isRadarV1OnlyOverrideEnabled(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        return try {
            SettingsDataStore.getInstance(context).isRadarV1OnlyOverrideEnabledSync()
        } catch (_: Throwable) {
            false
        }
    }
}