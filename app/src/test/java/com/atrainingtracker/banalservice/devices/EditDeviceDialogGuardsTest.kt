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
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [AttFusedPreferences] guarded availability checks and preference persistence
 * (REQ-SEN-028.4, TST-SEN-028.5, ATT-7751).
 */
class EditDeviceDialogGuardsTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockSensorManager: SensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    private val prefMap = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockDevicesDbManager = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        prefMap.clear()

        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockSensorManager

        mockStaticHelpers()
    }

    private fun mockStaticHelpers() {
        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

        every { mockPrefs.getBoolean(any(), any()) } answers {
            prefMap[firstArg()] as? Boolean ?: secondArg()
        }
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } answers {
            prefMap[firstArg()] = secondArg()
            mockEditor
        }
        every { mockEditor.apply() } returns Unit

        mockkStatic(TrainingApplication::class)
        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGnssGuard_whenPermissionDenied_isUnavailableAndInactive() {
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns false

        assertFalse(
            "GNSS should be unavailable when permission is denied",
            AttFusedPreferences.isGnssAvailable(mockContext)
        )
        assertFalse("Effective GNSS should be inactive", AttFusedPreferences.isEffectiveGnssActive(mockContext))
        assertFalse(
            "Auto-calib requires GNSS and should be unavailable",
            AttFusedPreferences.isAutoCalibAvailable(mockContext)
        )
    }

    @Test
    fun testWheelSensorGuard_whenNoWheelSensorPaired_isUnavailableAndInactive() {
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true
        every { mockDevicesDbManager.hasPairedWheelSpeedSensor() } returns false

        assertFalse(
            "Wheel speed should be unavailable when no wheel sensor is paired",
            AttFusedPreferences.isWheelAvailable(mockContext)
        )
        assertFalse("Effective wheel speed should be inactive", AttFusedPreferences.isEffectiveWheelActive(mockContext))
        assertFalse(
            "Auto-calib requires wheel sensor and should be unavailable",
            AttFusedPreferences.isAutoCalibAvailable(mockContext)
        )
    }

    @Test
    fun testGyroGuard_whenNoHardwareGyro_isUnavailableAndInactive() {
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) } returns null

        assertFalse(
            "Gyro should be unavailable when phone lacks gyroscope sensor",
            AttFusedPreferences.isGyroAvailable(mockContext)
        )
        assertFalse("Effective gyro should be inactive", AttFusedPreferences.isEffectiveGyroActive(mockContext))
    }

    @Test
    fun testAllSensorsAvailable_allowsUserPreferencesToControlState() {
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true
        every { mockDevicesDbManager.hasPairedWheelSpeedSensor() } returns true
        val mockGyro = mockk<Sensor>(relaxed = true)
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) } returns mockGyro

        assertTrue(AttFusedPreferences.isGnssAvailable(mockContext))
        assertTrue(AttFusedPreferences.isWheelAvailable(mockContext))
        assertTrue(AttFusedPreferences.isGyroAvailable(mockContext))
        assertTrue(AttFusedPreferences.isAutoCalibAvailable(mockContext))

        // Default state: sensors enabled, auto-calibration defaults to false (opt-in per REQ-SEN-028.4(d))
        assertTrue(AttFusedPreferences.isEffectiveGnssActive(mockContext))
        assertTrue(AttFusedPreferences.isEffectiveWheelActive(mockContext))
        assertTrue(AttFusedPreferences.isEffectiveGyroActive(mockContext))
        assertFalse(AttFusedPreferences.isAutoCalibEnabled(mockContext))
        assertFalse(AttFusedPreferences.isEffectiveAutoCalibActive(mockContext))

        // Toggle user preference on for AutoCalib
        AttFusedPreferences.setAutoCalibEnabled(mockContext, true)
        assertTrue(AttFusedPreferences.isAutoCalibEnabled(mockContext))
        assertTrue(AttFusedPreferences.isEffectiveAutoCalibActive(mockContext))

        // Toggle user preference off
        AttFusedPreferences.setGnssEnabled(mockContext, false)
        assertFalse(AttFusedPreferences.isGnssEnabled(mockContext))
        assertFalse(AttFusedPreferences.isEffectiveGnssActive(mockContext))

        AttFusedPreferences.setWheelEnabled(mockContext, false)
        assertFalse(AttFusedPreferences.isWheelEnabled(mockContext))
        assertFalse(AttFusedPreferences.isEffectiveWheelActive(mockContext))

        AttFusedPreferences.setGyroEnabled(mockContext, false)
        assertFalse(AttFusedPreferences.isGyroEnabled(mockContext))
        assertFalse(AttFusedPreferences.isEffectiveGyroActive(mockContext))

        AttFusedPreferences.setAutoCalibEnabled(mockContext, false)
        assertFalse(AttFusedPreferences.isAutoCalibEnabled(mockContext))
        assertFalse(AttFusedPreferences.isEffectiveAutoCalibActive(mockContext))
    }
}
