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
 * Manages user configuration toggles and hardware/pairing availability guards for ATT Altitude & Slope Fusion
 * (REQ-FIL-016.5, TST-FIL-016.5, ATT-6279).
 */
object AttFusedAltitudePreferences {
    const val KEY_USE_BAROMETER = "pref_att_fused_alt_use_barometer"
    const val KEY_USE_DEM = "pref_att_fused_alt_use_dem"
    const val KEY_USE_GPS = "pref_att_fused_alt_use_gps"
    const val KEY_USE_SPEED = "pref_att_fused_alt_use_speed"

    fun isBarometerEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_BAROMETER, true)

    fun setBarometerEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_USE_BAROMETER, enabled).apply()
    }

    fun isDemEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_DEM, false)

    fun setDemEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_USE_DEM, enabled).apply()
    }

    fun isGpsEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_GPS, false)

    fun setGpsEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_USE_GPS, enabled).apply()
    }

    fun isSpeedEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_USE_SPEED, true)

    fun setSpeedEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_USE_SPEED, enabled).apply()
    }

    // --- Guard Checks ---

    fun isBarometerAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        val hasHardware = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        val db = DevicesDatabaseManager.getInstance(context)
        val pressureDeviceId = db.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_PRESSURE)
        return hasHardware && (pressureDeviceId != -1L && db.isPaired(pressureDeviceId))
    }

    fun isDemAvailable(context: Context): Boolean {
        val db = DevicesDatabaseManager.getInstance(context)
        val demDeviceId = db.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_POSITION)
        return demDeviceId != -1L && db.isPaired(demDeviceId)
    }

    fun isGpsAvailable(context: Context): Boolean {
        if (!TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) return false
        val db = DevicesDatabaseManager.getInstance(context)
        val gpsId = db.getSmartphoneDeviceId(DeviceType.SPEED_AND_LOCATION_GPS)
        val fusedId = db.getSmartphoneDeviceId(DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED)
        val attFusedId = db.getSmartphoneDeviceId(DeviceType.SPEED_AND_LOCATION_ATT_FUSED)
        return (gpsId != -1L && db.isPaired(gpsId)) ||
            (fusedId != -1L && db.isPaired(fusedId)) ||
            (attFusedId != -1L && db.isPaired(attFusedId))
    }

    @Suppress("UnusedParameter")
    fun isSpeedAvailable(context: Context): Boolean = true

    // --- Effective Active States ---

    data class EffectiveState(
        val isBarometerActive: Boolean,
        val isDemActive: Boolean,
        val isGpsActive: Boolean,
        val isSpeedActive: Boolean
    )

    fun getEffectiveState(context: Context): EffectiveState = EffectiveState(
        isBarometerActive = isEffectiveBarometerActive(context),
        isDemActive = isEffectiveDemActive(context),
        isGpsActive = isEffectiveGpsActive(context),
        isSpeedActive = isEffectiveSpeedActive(context)
    )

    fun isEffectiveBarometerActive(context: Context): Boolean =
        isBarometerEnabled(context) && isBarometerAvailable(context)

    fun isEffectiveDemActive(context: Context): Boolean =
        isDemEnabled(context) && isDemAvailable(context)

    fun isEffectiveGpsActive(context: Context): Boolean =
        isGpsEnabled(context) && isGpsAvailable(context)

    fun isEffectiveSpeedActive(context: Context): Boolean =
        isSpeedEnabled(context) && isSpeedAvailable(context)
}
