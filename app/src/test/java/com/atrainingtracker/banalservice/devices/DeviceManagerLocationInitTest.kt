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
import android.content.Intent
import android.hardware.SensorManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying lazy dynamic location device initialization and permission rechecks
 * in DeviceManager (REQ-PRI-004, TST-PRI-003, ATT-2357).
 */
class DeviceManagerLocationInitTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    private val gpsDeviceId = 101L
    private val fusedDeviceId = 102L
    private val networkDeviceId = 103L

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns false
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_COARSE_LOCATION) } returns false

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockkStatic(DevicesDatabaseManager::class)
        mockDevicesDbManager = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.getSpeedAndLocationGPSDeviceId() } returns gpsDeviceId
        every { mockDevicesDbManager.getSpeedAndLocationGoogleFusedDeviceId() } returns fusedDeviceId
        every { mockDevicesDbManager.getSpeedAndLocationNetworkDeviceId() } returns networkDeviceId
        every { mockDevicesDbManager.isPaired(gpsDeviceId) } returns true
        every { mockDevicesDbManager.isPaired(fusedDeviceId) } returns false
        every { mockDevicesDbManager.isPaired(networkDeviceId) } returns false

        mockLocationManager = mockk(relaxed = true)
        every { mockLocationManager.allProviders } returns listOf(LocationManager.GPS_PROVIDER)
        every { mockLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true

        mockSensorManager = mockk(relaxed = true)
        mockAndroidSensorManager = mockk(relaxed = true)
        every { mockAndroidSensorManager.getDefaultSensor(any()) } returns null

        mockBanalService = mockk(relaxed = true)
        every { mockBanalService.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockBanalService.getSystemService(Context.SENSOR_SERVICE) } returns mockAndroidSensorManager
        every { mockBanalService.packageName } returns "com.atrainingtracker"

        mockkConstructor(ClockDevice::class)
        mockkConstructor(BatteryDevice::class)
        mockkConstructor(SpeedAndLocationDevice_GPS::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCheckOrInitializeLocationDevices_whenFinePermissionGranted_createsGpsDevice() {
        // Initially, fine location permission is denied
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns false

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        assertNull("GPS device should be null initially when fine location permission is absent",
            deviceManager.speedAndLocationDevice_GPS)

        // Now simulate user granting ACCESS_FINE_LOCATION permission
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true

        // Trigger dynamic check
        deviceManager.checkOrInitializeLocationDevices()

        assertNotNull("GPS device must be created once ACCESS_FINE_LOCATION is granted (REQ-PRI-004)",
            deviceManager.speedAndLocationDevice_GPS)
    }

    @Test
    fun testCheckOrInitializeLocationDevices_whenPermissionDenied_doesNotCreateGpsDevice() {
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns false

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        assertNull("GPS device should be null initially", deviceManager.speedAndLocationDevice_GPS)

        deviceManager.checkOrInitializeLocationDevices()

        assertNull("GPS device must remain null while ACCESS_FINE_LOCATION is not granted",
            deviceManager.speedAndLocationDevice_GPS)
    }

    @Test
    fun testCheckOrInitializeLocationDevices_idempotentWhenAlreadyInitialized() {
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        val initialGpsDevice = deviceManager.speedAndLocationDevice_GPS
        assertNotNull("GPS device should be initialized on startup if permission is present", initialGpsDevice)

        // Subsequent call must not overwrite or recreate the device instance
        deviceManager.checkOrInitializeLocationDevices()
        assertSame("Subsequent checkOrInitializeLocationDevices calls must be idempotent and preserve existing instance",
            initialGpsDevice, deviceManager.speedAndLocationDevice_GPS)
    }
}
