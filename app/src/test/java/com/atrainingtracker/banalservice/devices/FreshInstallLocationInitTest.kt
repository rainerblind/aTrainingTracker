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
import android.location.LocationProvider
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
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit & Contract tests verifying defensive location device initialization on fresh installs
 * when permissions are granted dynamically (REQ-TRK-017, TST-TRK-009, ATT-3045).
 */
class FreshInstallLocationInitTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    private val gpsDeviceId = 201L
    private val fusedDeviceId = 202L
    private val networkDeviceId = 203L

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
    fun testDeviceManager_whenPermissionGrantedPostInit_instantiatesGpsDevice() {
        // Step 1: Fresh install state - permissions denied
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns false

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        assertNull("GPS device must be null initially on fresh install before permission grant",
            deviceManager.speedAndLocationDevice_GPS)

        // Step 2: Athlete grants permission
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true

        // Step 3: Trigger defensive initialization
        deviceManager.checkOrInitializeLocationDevices()

        // Step 4: GPS device is instantiated
        assertNotNull("GPS device must be lazily instantiated after permission grant (REQ-TRK-017)",
            deviceManager.speedAndLocationDevice_GPS)
    }

    @Test
    fun testSpeedAndLocationDeviceGPS_reregisterUpdates_whenInitiallyUnavailable() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockLocManager = mockk<LocationManager>(relaxed = true)
        val mockGpsProvider = mockk<LocationProvider>(relaxed = true)

        every { mockContext.getSystemService(Context.LOCATION_SERVICE) } returns mockLocManager

        // Initially GPS provider is unavailable
        every { mockLocManager.getProvider(LocationManager.GPS_PROVIDER) } returns null

        val gpsDevice = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)
        assertFalse("Updates must not be registered when GPS provider is null", gpsDevice.isLocationUpdatesRegistered)

        // Provider becomes available
        every { mockLocManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockGpsProvider

        // Re-register call
        gpsDevice.checkOrReRegisterLocationUpdates()

        assertTrue("Updates must be registered after checkOrReRegisterLocationUpdates()", gpsDevice.isLocationUpdatesRegistered)
        verify(atLeast = 1) {
            mockLocManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                gpsDevice
            )
        }
    }

    @Test
    fun testSpeedAndLocationDeviceGPS_checkOrReRegister_isIdempotentWhenAlreadyRegistered() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockLocManager = mockk<LocationManager>(relaxed = true)
        val mockGpsProvider = mockk<LocationProvider>(relaxed = true)

        every { mockContext.getSystemService(Context.LOCATION_SERVICE) } returns mockLocManager
        every { mockLocManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockGpsProvider

        val gpsDevice = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)
        assertTrue(gpsDevice.isLocationUpdatesRegistered)

        // Calling again should not trigger redundant registration
        gpsDevice.checkOrReRegisterLocationUpdates()
        verify(exactly = 1) {
            mockLocManager.requestLocationUpdates(
                any<String>(),
                any<Long>(),
                any<Float>(),
                any<android.location.LocationListener>()
            )
        }
    }

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("File not found: $relativePath")
    }

    @Test
    fun testTrackerService_structuralWiring_callsCheckOrInitializeLocationDevices() {
        val trackerServiceFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java")
        assertTrue("TrackerService.java must exist", trackerServiceFile.exists())

        val content = trackerServiceFile.readText()
        assertTrue(
            "TrackerService must invoke BANALService.checkOrInitializeLocationDevices() in onServiceConnected",
            content.contains("BANALService.checkOrInitializeLocationDevices();")
        )
    }

    @Test
    fun testTrainingApplication_structuralWiring_callsCheckOrInitializeLocationDevices() {
        val appFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java")
        assertTrue("TrainingApplication.java must exist", appFile.exists())

        val content = appFile.readText()
        assertTrue(
            "TrainingApplication must invoke BANALService.checkOrInitializeLocationDevices() in startTracking()",
            content.contains("BANALService.checkOrInitializeLocationDevices();")
        )
    }
}
