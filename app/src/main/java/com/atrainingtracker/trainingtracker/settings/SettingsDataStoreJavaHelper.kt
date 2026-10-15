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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A helper object to allow synchronous access to DataStore values from Java code.
 * NOTE: This uses runBlocking and should be used carefully to avoid blocking the UI thread.
 * It is acceptable for reading small, cached preference values.
 */
object SettingsDataStoreJavaHelper {

    /**
     * Generic helper to get a max zone value for a specific profile (Run, Bike, Power).
     * @param context The application context
     * @param zoneType The ZoneType
     * @param zoneIndex The zone number (1-4). Zone 5 is usually > Zone 4.
     * @return The max value for that zone.
     */
    @JvmStatic
    fun getZoneMax(context: Context, zoneType: SettingsDataStore.ZoneType, zoneIndex: Int): Int {
        val dataStore = SettingsDataStore(context)

        return runBlocking {
            when (zoneIndex) {
                1 -> dataStore.getZone1MaxFlow(zoneType).first()
                2 -> dataStore.getZone2MaxFlow(zoneType).first()
                3 -> dataStore.getZone3MaxFlow(zoneType).first()
                4 -> dataStore.getZone4MaxFlow(zoneType).first()
                5 -> if (zoneType == SettingsDataStore.ZoneType.PWR_BIKE) dataStore.getZone5MaxFlow(zoneType).first() else 0
                6 -> if (zoneType == SettingsDataStore.ZoneType.PWR_BIKE) dataStore.getZone6MaxFlow(zoneType).first() else 0
                else -> 0 // Fallback for open-ended or out-of-bounds zones
            }
        }
    }

    /**
     * Synchronous bridge for legacy Java or non-coroutine callers to fetch zone display options.
     * Executes safely on Dispatchers.IO to prevent UI thread blocking.
     */
    @JvmStatic
    fun getZoneDisplayOptions(context: Context, zoneType: SettingsDataStore.ZoneType): ZoneDisplayOptions {
        val dataStore = SettingsDataStore(context)
        return runBlocking(Dispatchers.IO) {
            dataStore.getZoneDisplayOptionsFlow(zoneType).first()
        }
    }

    /**
     * Synchronous bridge for legacy Java sensor drivers to fetch the active tuning configuration.
     */
    @JvmStatic
    fun getTuningConfig(context: Context): TuningConfig {
        return try {
            val tuningDataStore = TuningPreferencesDataStore(context)
            runBlocking(Dispatchers.IO) {
                tuningDataStore.tuningConfigFlow.first()
            }
        } catch (t: Throwable) {
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
     * Synchronous bridge to fetch athlete Functional Threshold Power (FTP) in Watts.
     * Returns null if unconfigured or <= 0.
     */
    @JvmStatic
    fun getFtp(context: Context): Int? {
        return try {
            val dataStore = SettingsDataStore(context)
            runBlocking(Dispatchers.IO) {
                dataStore.ftpFlow.first()
            }
        } catch (t: Throwable) {
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
     * Synchronous bridge to fetch athlete body weight in kilograms (REQ-TRK-019).
     * Returns 0.0f if unconfigured or <= 0.
     */
    @JvmStatic
    fun getAthleteWeightKg(context: Context): Float {
        return try {
            val dataStore = SettingsDataStore.getInstance(context)
            runBlocking(Dispatchers.IO) {
                dataStore.athleteWeightKgFlow.first()
            }
        } catch (t: Throwable) {
            0.0f
        }
    }

    /**
     * Synchronous bridge to persist athlete body weight in kilograms (REQ-TRK-019).
     */
    @JvmStatic
    fun setAthleteWeightKg(context: Context, weightKg: Float) {
        try {
            val dataStore = SettingsDataStore.getInstance(context)
            runBlocking(Dispatchers.IO) {
                dataStore.setAthleteWeightKg(weightKg)
            }
        } catch (t: Throwable) {
            // ignore in tests or headless contexts
        }
    }
}