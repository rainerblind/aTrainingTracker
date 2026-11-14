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
 * Unit tests verifying [AttFusedAltitudePreferences] guarded availability checks and preference persistence
 * (REQ-FIL-016.5, TST-FIL-016.5, ATT-6279).
 */
class EditDeviceDialogAltitudeGuardsTest {

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
    fun testBarometerGuard_whenNoHardwareOrNotPaired_isUnavailableAndInactive() {
        // Case 1: No hardware sensor
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns null
        every { mockDevicesDbManager.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_PRESSURE) } returns 10L
        every { mockDevicesDbManager.isPaired(10L) } returns true

        assertFalse(
            "Barometer should be unavailable without hardware",
            AttFusedAltitudePreferences.isBarometerAvailable(mockContext)
        )
        assertFalse(
            "Effective barometer should be inactive",
            AttFusedAltitudePreferences.isEffectiveBarometerActive(mockContext)
        )

        // Case 2: Hardware present, but not paired in DB
        val mockPressure = mockk<Sensor>(relaxed = true)
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns mockPressure
        every { mockDevicesDbManager.isPaired(10L) } returns false

        assertFalse(
            "Barometer should be unavailable when unpaired in DB",
            AttFusedAltitudePreferences.isBarometerAvailable(mockContext)
        )
        assertFalse(
            "Effective barometer should be inactive",
            AttFusedAltitudePreferences.isEffectiveBarometerActive(mockContext)
        )
    }

    @Test
    fun testDemGuard_whenNotPaired_isUnavailableAndInactive() {
        every { mockDevicesDbManager.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_POSITION) } returns 20L
        every { mockDevicesDbManager.isPaired(20L) } returns false

        assertFalse(
            "DEM should be unavailable when unpaired in DB",
            AttFusedAltitudePreferences.isDemAvailable(mockContext)
        )
        assertFalse(
            "Effective DEM should be inactive",
            AttFusedAltitudePreferences.isEffectiveDemActive(mockContext)
        )
    }

    @Test
    fun testGpsGuard_whenLocationUnpairedOrPermissionDenied_isUnavailableAndInactive() {
        // Case 1: Permission denied
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns false
        every { mockDevicesDbManager.getSmartphoneDeviceId(any()) } returns 30L
        every { mockDevicesDbManager.isPaired(30L) } returns true

        assertFalse(
            "GPS altitude should be unavailable when permission is denied",
            AttFusedAltitudePreferences.isGpsAvailable(mockContext)
        )

        // Case 2: Permission granted, but no location device paired
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true
        every { mockDevicesDbManager.isPaired(any()) } returns false

        assertFalse(
            "GPS altitude should be unavailable when no location device is paired",
            AttFusedAltitudePreferences.isGpsAvailable(mockContext)
        )
    }

    @Test
    fun testAllSensorsAvailable_allowsUserPreferencesToControlState() {
        val mockPressure = mockk<Sensor>(relaxed = true)
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns mockPressure
        every { mockDevicesDbManager.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_PRESSURE) } returns 10L
        every { mockDevicesDbManager.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_POSITION) } returns 20L
        every { mockDevicesDbManager.getSmartphoneDeviceId(DeviceType.SPEED_AND_LOCATION_GPS) } returns 30L
        every { mockDevicesDbManager.isPaired(any()) } returns true
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true

        assertTrue(AttFusedAltitudePreferences.isBarometerAvailable(mockContext))
        assertTrue(AttFusedAltitudePreferences.isDemAvailable(mockContext))
        assertTrue(AttFusedAltitudePreferences.isGpsAvailable(mockContext))
        assertTrue(AttFusedAltitudePreferences.isSpeedAvailable(mockContext))

        // Check defaults: Barometer=true, DEM=false, GPS=false, Speed=true
        assertTrue(AttFusedAltitudePreferences.isEffectiveBarometerActive(mockContext))
        assertFalse(AttFusedAltitudePreferences.isEffectiveDemActive(mockContext))
        assertFalse(AttFusedAltitudePreferences.isEffectiveGpsActive(mockContext))
        assertTrue(AttFusedAltitudePreferences.isEffectiveSpeedActive(mockContext))

        // Toggle DEM on
        AttFusedAltitudePreferences.setDemEnabled(mockContext, true)
        assertTrue(AttFusedAltitudePreferences.isDemEnabled(mockContext))
        assertTrue(AttFusedAltitudePreferences.isEffectiveDemActive(mockContext))

        // Toggle Barometer off
        AttFusedAltitudePreferences.setBarometerEnabled(mockContext, false)
        assertFalse(AttFusedAltitudePreferences.isBarometerEnabled(mockContext))
        assertFalse(AttFusedAltitudePreferences.isEffectiveBarometerActive(mockContext))
    }
}
