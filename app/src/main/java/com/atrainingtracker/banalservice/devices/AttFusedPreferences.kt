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

package com.atrainingtracker.banalservice.devices

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication

/**
 * Manages user toggles and hardware/pairing availability guards for ATT Odometry Fusion
 * (REQ-SEN-028.4, TST-SEN-028.5).
 */
object AttFusedPreferences {
    const val KEY_USE_GNSS = "pref_att_fused_use_gnss"
    const val KEY_USE_WHEEL = "pref_att_fused_use_wheel"
    const val KEY_USE_GYRO = "pref_att_fused_use_gyro"
    const val KEY_USE_AUTOCALIB = "pref_att_fused_use_autocalib"

    fun isGnssEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_GNSS, true)

    fun setGnssEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_USE_GNSS, enabled).apply()
    }

    fun isWheelEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_WHEEL, true)

    fun setWheelEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_USE_WHEEL, enabled).apply()
    }

    fun isGyroEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_GYRO, true)

    fun setGyroEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_USE_GYRO, enabled).apply()
    }

    fun isAutoCalibEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_AUTOCALIB, true)

    fun setAutoCalibEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_USE_AUTOCALIB, enabled).apply()
    }

    // --- Guard Checks ---

    @Suppress("UnusedParameter")
    fun isGnssAvailable(context: Context): Boolean =
        TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)

    fun isWheelAvailable(context: Context): Boolean =
        DevicesDatabaseManager.getInstance(context).hasPairedWheelSpeedSensor()

    fun isGyroAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }

    fun isAutoCalibAvailable(context: Context): Boolean =
        isGnssAvailable(context) && isWheelAvailable(context)

    // --- Effective Active States ---

    fun isEffectiveGnssActive(context: Context): Boolean =
        isGnssEnabled(context) && isGnssAvailable(context)

    fun isEffectiveWheelActive(context: Context): Boolean =
        isWheelEnabled(context) && isWheelAvailable(context)

    fun isEffectiveGyroActive(context: Context): Boolean =
        isGyroEnabled(context) && isGyroAvailable(context)

    fun isEffectiveAutoCalibActive(context: Context): Boolean =
        isAutoCalibEnabled(context) && isAutoCalibAvailable(context)
}
