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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Encapsulates battle-tested factory defaults and safety clamping bounds for advanced tuning.
 */
object TuningPreferencesDefaults {
    const val FULL_DIM_FACTOR = 0.25f
    const val MEDIUM_DIM_FACTOR = 0.50f
    const val SLOPE_FLAT_THRESHOLD = 2.0f
    const val SLOPE_STEEP_THRESHOLD = 5.0f
    const val WAKEUP_DURATION_SEC = 15
    const val DOWNWARD_DELAY_SEC = 3
    const val GPS_ACCURACY_THRESHOLD_M = 200.0f
    const val ALTITUDE_FILTER_WINDOW_SEC = 21
    const val SLOPE_MIN_SPEED_MPS = 0.5f

    const val MIN_FULL_DIM_FACTOR = 0.05f
    const val MAX_FULL_DIM_FACTOR = 0.50f
    const val MIN_MEDIUM_DIM_FACTOR = 0.20f
    const val MAX_MEDIUM_DIM_FACTOR = 0.90f
    const val MIN_SLOPE_FLAT = 0.0f
    const val MAX_SLOPE_FLAT = 5.0f
    const val MIN_SLOPE_STEEP = 2.0f
    const val MAX_SLOPE_STEEP = 15.0f
    const val MIN_WAKEUP_SEC = 5
    const val MAX_WAKEUP_SEC = 60
    const val MIN_DOWNWARD_DELAY_SEC = 1
    const val MAX_DOWNWARD_DELAY_SEC = 15
    const val MIN_GPS_ACCURACY_M = 10.0f
    const val MAX_GPS_ACCURACY_M = 500.0f
    const val MIN_ALTITUDE_WINDOW_SEC = 5
    const val MAX_ALTITUDE_WINDOW_SEC = 60
    const val MIN_SLOPE_SPEED_MPS = 0.2f
    const val MAX_SLOPE_SPEED_MPS = 2.0f
}

/**
 * Immutable snapshot of active tuning preferences.
 */
data class TuningConfig(
    val fullDimFactor: Float = TuningPreferencesDefaults.FULL_DIM_FACTOR,
    val mediumDimFactor: Float = TuningPreferencesDefaults.MEDIUM_DIM_FACTOR,
    val slopeFlatThreshold: Float = TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD,
    val slopeSteepThreshold: Float = TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD,
    val wakeupDurationSec: Int = TuningPreferencesDefaults.WAKEUP_DURATION_SEC,
    val downwardDelaySec: Int = TuningPreferencesDefaults.DOWNWARD_DELAY_SEC,
    val gpsAccuracyThresholdMeters: Float = TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M,
    val altitudeFilterWindowSec: Int = TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC,
    val slopeMinSpeedMps: Float = TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS
)

/**
 * DataStore repository managing expert tuning preferences with strict parameter validation and atomic factory reset.
 * Conforms to REQ-SET-073.
 */
class TuningPreferencesDataStore(private val context: Context) {

    companion object {
        val KEY_FULL_DIM_FACTOR: Preferences.Key<Float> = floatPreferencesKey("tuning_battery_saver_full_dim")
        val KEY_MEDIUM_DIM_FACTOR: Preferences.Key<Float> = floatPreferencesKey("tuning_battery_saver_medium_dim")
        val KEY_SLOPE_FLAT: Preferences.Key<Float> = floatPreferencesKey("tuning_battery_saver_slope_flat")
        val KEY_SLOPE_STEEP: Preferences.Key<Float> = floatPreferencesKey("tuning_battery_saver_slope_steep")
        val KEY_WAKEUP_DURATION_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_battery_saver_wakeup_sec")
        val KEY_DOWNWARD_DELAY_SEC: Preferences.Key<Int> = intPreferencesKey("tuning_battery_saver_downward_delay_sec")
        val KEY_GPS_ACCURACY_THRESHOLD: Preferences.Key<Float> = floatPreferencesKey("tuning_gps_accuracy_threshold")
        val KEY_ALTITUDE_FILTER_WINDOW: Preferences.Key<Int> = intPreferencesKey("tuning_altitude_filter_window")
        val KEY_SLOPE_MIN_SPEED: Preferences.Key<Float> = floatPreferencesKey("tuning_slope_min_speed")

        private val ALL_KEYS = listOf(
            KEY_FULL_DIM_FACTOR,
            KEY_MEDIUM_DIM_FACTOR,
            KEY_SLOPE_FLAT,
            KEY_SLOPE_STEEP,
            KEY_WAKEUP_DURATION_SEC,
            KEY_DOWNWARD_DELAY_SEC,
            KEY_GPS_ACCURACY_THRESHOLD,
            KEY_ALTITUDE_FILTER_WINDOW,
            KEY_SLOPE_MIN_SPEED
        )
    }

    val tuningConfigFlow: Flow<TuningConfig> = context.dataStore.data.map { prefs ->
        val rawFullDim = prefs[KEY_FULL_DIM_FACTOR] ?: TuningPreferencesDefaults.FULL_DIM_FACTOR
        val rawMediumDim = prefs[KEY_MEDIUM_DIM_FACTOR] ?: TuningPreferencesDefaults.MEDIUM_DIM_FACTOR
        val clampedFullDim = rawFullDim.coerceIn(
            TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR,
            TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR
        )
        val clampedMediumDim = rawMediumDim.coerceIn(
            clampedFullDim,
            TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR
        )

        val rawSlopeFlat = prefs[KEY_SLOPE_FLAT] ?: TuningPreferencesDefaults.SLOPE_FLAT_THRESHOLD
        val rawSlopeSteep = prefs[KEY_SLOPE_STEEP] ?: TuningPreferencesDefaults.SLOPE_STEEP_THRESHOLD
        val clampedSlopeFlat = rawSlopeFlat.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_FLAT,
            TuningPreferencesDefaults.MAX_SLOPE_FLAT
        )
        val clampedSlopeSteep = rawSlopeSteep.coerceIn(
            clampedSlopeFlat,
            TuningPreferencesDefaults.MAX_SLOPE_STEEP
        )

        val rawWakeup = prefs[KEY_WAKEUP_DURATION_SEC] ?: TuningPreferencesDefaults.WAKEUP_DURATION_SEC
        val clampedWakeup = rawWakeup.coerceIn(
            TuningPreferencesDefaults.MIN_WAKEUP_SEC,
            TuningPreferencesDefaults.MAX_WAKEUP_SEC
        )

        val rawDownward = prefs[KEY_DOWNWARD_DELAY_SEC] ?: TuningPreferencesDefaults.DOWNWARD_DELAY_SEC
        val clampedDownward = rawDownward.coerceIn(
            TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC,
            TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC
        )

        val rawAccuracy = prefs[KEY_GPS_ACCURACY_THRESHOLD] ?: TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M
        val clampedAccuracy = rawAccuracy.coerceIn(
            TuningPreferencesDefaults.MIN_GPS_ACCURACY_M,
            TuningPreferencesDefaults.MAX_GPS_ACCURACY_M
        )

        val rawAltWindow = prefs[KEY_ALTITUDE_FILTER_WINDOW] ?: TuningPreferencesDefaults.ALTITUDE_FILTER_WINDOW_SEC
        val clampedAltWindow = rawAltWindow.coerceIn(
            TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC,
            TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC
        )

        val rawSlopeSpeed = prefs[KEY_SLOPE_MIN_SPEED] ?: TuningPreferencesDefaults.SLOPE_MIN_SPEED_MPS
        val clampedSlopeSpeed = rawSlopeSpeed.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS,
            TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS
        )

        TuningConfig(
            fullDimFactor = clampedFullDim,
            mediumDimFactor = clampedMediumDim,
            slopeFlatThreshold = clampedSlopeFlat,
            slopeSteepThreshold = clampedSlopeSteep,
            wakeupDurationSec = clampedWakeup,
            downwardDelaySec = clampedDownward,
            gpsAccuracyThresholdMeters = clampedAccuracy,
            altitudeFilterWindowSec = clampedAltWindow,
            slopeMinSpeedMps = clampedSlopeSpeed
        )
    }

    suspend fun saveTuningConfig(config: TuningConfig) {
        val clampedFullDim = config.fullDimFactor.coerceIn(
            TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR,
            TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR
        )
        val clampedMediumDim = config.mediumDimFactor.coerceIn(
            clampedFullDim,
            TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR
        )
        val clampedSlopeFlat = config.slopeFlatThreshold.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_FLAT,
            TuningPreferencesDefaults.MAX_SLOPE_FLAT
        )
        val clampedSlopeSteep = config.slopeSteepThreshold.coerceIn(
            clampedSlopeFlat,
            TuningPreferencesDefaults.MAX_SLOPE_STEEP
        )
        val clampedWakeup = config.wakeupDurationSec.coerceIn(
            TuningPreferencesDefaults.MIN_WAKEUP_SEC,
            TuningPreferencesDefaults.MAX_WAKEUP_SEC
        )
        val clampedDownward = config.downwardDelaySec.coerceIn(
            TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC,
            TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC
        )
        val clampedAccuracy = config.gpsAccuracyThresholdMeters.coerceIn(
            TuningPreferencesDefaults.MIN_GPS_ACCURACY_M,
            TuningPreferencesDefaults.MAX_GPS_ACCURACY_M
        )
        val clampedAltWindow = config.altitudeFilterWindowSec.coerceIn(
            TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC,
            TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC
        )
        val clampedSlopeSpeed = config.slopeMinSpeedMps.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS,
            TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS
        )

        context.dataStore.edit { prefs ->
            prefs[KEY_FULL_DIM_FACTOR] = clampedFullDim
            prefs[KEY_MEDIUM_DIM_FACTOR] = clampedMediumDim
            prefs[KEY_SLOPE_FLAT] = clampedSlopeFlat
            prefs[KEY_SLOPE_STEEP] = clampedSlopeSteep
            prefs[KEY_WAKEUP_DURATION_SEC] = clampedWakeup
            prefs[KEY_DOWNWARD_DELAY_SEC] = clampedDownward
            prefs[KEY_GPS_ACCURACY_THRESHOLD] = clampedAccuracy
            prefs[KEY_ALTITUDE_FILTER_WINDOW] = clampedAltWindow
            prefs[KEY_SLOPE_MIN_SPEED] = clampedSlopeSpeed
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
            for (key in ALL_KEYS) {
                prefs.remove(key)
            }
        }
    }
}
