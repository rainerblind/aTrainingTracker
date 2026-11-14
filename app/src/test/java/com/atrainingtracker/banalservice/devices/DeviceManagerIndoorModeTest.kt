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
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * Unit tests verifying indoor mode propagation through DeviceManager to SpeedAndLocationDevice
 * instances and GPS update suppression (REQ-TRK-077.1, TST-TRK-077.1, ATT-47).
 */
class DeviceManagerIndoorModeTest {

    private lateinit var mockBanalService: BANALService
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockAndroidSensorManager: SensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    private val gpsDeviceId = 101L
    private val fusedDeviceId = 102L

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            mockkConstructor(Intent::class)
            every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
            every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers { self as Intent }
            every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
            mockkConstructor(ClockDevice::class)
            mockkConstructor(BatteryDevice::class)
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            try {
                unmockkAll()
            } catch (e: Exception) {
                // Defensive teardown
            }
        }
    }

    @Before
    fun setUp() {
        DeviceDataRepository.resetForTesting()
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(any()) } returns 200.0f

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.startSearchWhenAppStarts() } returns false
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_COARSE_LOCATION) } returns true
        every { TrainingApplication.isTracking() } returns true

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockkStatic(DevicesDatabaseManager::class)
        mockDevicesDbManager = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.getSpeedAndLocationGPSDeviceId() } returns gpsDeviceId
        every { mockDevicesDbManager.getSpeedAndLocationGoogleFusedDeviceId() } returns fusedDeviceId
        every { mockDevicesDbManager.isPaired(gpsDeviceId) } returns true
        every { mockDevicesDbManager.isPaired(fusedDeviceId) } returns false

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
    }

    @After
    fun tearDown() {
        DeviceDataRepository.resetForTesting()
        clearAllMocks(answers = false)
    }

    @Test
    fun testSetIndoorMode_propagatesToActiveSpeedAndLocationDeviceAndSuppressesGpsUpdates() {
        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        val gpsDevice = deviceManager.speedAndLocationDevice_GPS
        assertNotNull("GPS device must be initialized", gpsDevice)
        assertFalse("Indoor mode must be false by default", deviceManager.isIndoorMode)
        assertFalse("GPS device indoor mode must be false by default", gpsDevice.isIndoorMode)

        // Enable indoor mode on DeviceManager
        deviceManager.setIndoorMode(true)
        assertTrue("DeviceManager indoor mode must be true", deviceManager.isIndoorMode)
        assertTrue("Active GPS device must have indoor mode set to true", gpsDevice.isIndoorMode)

        // Deliver initial location fix
        val loc1 = mockk<Location>(relaxed = true)
        every { loc1.latitude } returns 48.137154
        every { loc1.longitude } returns 11.576124
        every { loc1.accuracy } returns 5.0f
        every { loc1.speed } returns 0.0f
        every { loc1.time } returns 1000L
        every { loc1.hasSpeed() } returns true

        gpsDevice.onNewLocation(loc1)
        assertTrue("GPS device must be stationary pinned on initial fix in indoor mode", gpsDevice.isStationaryPinned)

        // Deliver multipath jump fix with high velocity
        val loc2 = mockk<Location>(relaxed = true)
        every { loc2.latitude } returns 48.137500 // ~40m jump
        every { loc2.longitude } returns 11.576500
        every { loc2.accuracy } returns 10.0f
        every { loc2.speed } returns 6.0f
        every { loc2.time } returns 2000L
        every { loc2.hasSpeed() } returns true
        every { loc1.distanceTo(loc2) } returns 40.0f

        gpsDevice.onNewLocation(loc2)

        // Assert GPS update suppression: pinning holds, speed clamped to 0.0, published coordinates stay clamped
        assertTrue("Stationary pinning must remain true under indoor mode despite large jump", gpsDevice.isStationaryPinned)
        assertEquals("Speed must be clamped to 0.0 under indoor mode", 0.0, gpsDevice.speed, 1e-4)
        assertNotNull("Stationary anchor must not be null", gpsDevice.stationaryAnchor)
        assertEquals("Stationary anchor latitude must match initial fix", loc1.latitude, gpsDevice.stationaryAnchor.latitude, 1e-6)
        assertEquals("Stationary anchor longitude must match initial fix", loc1.longitude, gpsDevice.stationaryAnchor.longitude, 1e-6)

        // Disabling indoor mode restores normal state
        deviceManager.setIndoorMode(false)
        assertFalse("DeviceManager indoor mode must be false after disabling", deviceManager.isIndoorMode)
        assertFalse("GPS device indoor mode must be false after disabling", gpsDevice.isIndoorMode)
    }

    @Test
    fun testNewlyInitializedDevice_inheritsCachedIndoorMode() {
        // Initially, fine permission is false so no device is created
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns false

        val deviceManager = DeviceManager(mockBanalService, mockSensorManager)
        assertNull("GPS device must be null initially", deviceManager.speedAndLocationDevice_GPS)

        // Enable indoor mode while no device is active
        deviceManager.setIndoorMode(true)
        assertTrue("DeviceManager indoor mode must be cached as true", deviceManager.isIndoorMode)

        // Grant permission and trigger checkOrInitializeLocationDevices
        every { TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) } returns true
        deviceManager.checkOrInitializeLocationDevices()

        val newlyCreatedDevice = deviceManager.speedAndLocationDevice_GPS
        assertNotNull("GPS device must be created", newlyCreatedDevice)
        assertTrue("Newly created GPS device must inherit cached indoor mode as true", newlyCreatedDevice.isIndoorMode)
    }

    @Test
    fun testSportTypesRepository_indoorKeywordsClassification() {
        // Positive indoor keyword matches
        assertTrue(SportTypesRepository.isIndoorName("Indoor Cycling"))
        assertTrue(SportTypesRepository.isIndoorName("Rollentrainer"))
        assertTrue(SportTypesRepository.isIndoorName("Treadmill Run"))
        assertTrue(SportTypesRepository.isIndoorName("Laufband"))
        assertTrue(SportTypesRepository.isIndoorName("Spinning"))
        assertTrue(SportTypesRepository.isIndoorName("Ergometer"))
        assertTrue(SportTypesRepository.isIndoorName("Stationary Bike"))
        assertTrue(SportTypesRepository.isIndoorName("indoor"))
        assertTrue(SportTypesRepository.isIndoorName("TRAINER"))

        // Negative outdoor matches
        assertFalse(SportTypesRepository.isIndoorName("Ride"))
        assertFalse(SportTypesRepository.isIndoorName("Running"))
        assertFalse(SportTypesRepository.isIndoorName("Mountain Bike"))
        assertFalse(SportTypesRepository.isIndoorName("Walking"))
        assertFalse(SportTypesRepository.isIndoorName("Other"))
        assertFalse(SportTypesRepository.isIndoorName(null))
        assertFalse(SportTypesRepository.isIndoorName(""))
    }
}
