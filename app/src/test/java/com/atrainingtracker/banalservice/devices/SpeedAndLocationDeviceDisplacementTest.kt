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

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying geodesic displacement velocity plausibility gating
 * against multipath teleportation spikes (REQ-TRK-041.1, TST-TRK-041.1, ATT-4843).
 */
class SpeedAndLocationDeviceDisplacementTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockHandler: Handler
    private lateinit var device: TestSpeedAndLocationDevice

    class TestSpeedAndLocationDevice(
        context: Context,
        sensorManager: MySensorManager
    ) : SpeedAndLocationDevice(context, sensorManager, DeviceType.SPEED_AND_LOCATION_GPS) {
        fun getSpeedSensor(): MySensor<Double> = mSpeedSensor
        fun getDistanceSensor(): MyDoubleAccumulatorSensor = mDistanceSensor
        fun getLapDistanceSensor(): MyDoubleAccumulatorSensor = mLapDistanceSensor
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Double>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }

        mockkStatic(DevicesDatabaseManager::class)
        mockDevicesDbManager = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.getSpeedAndLocationGPSDeviceId() } returns 101L

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(any()) } returns 200.0f

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isPaused() } returns false

        mockContext = mockk(relaxed = true)
        every { mockContext.packageName } returns "com.atrainingtracker"
        mockSensorManager = mockk(relaxed = true)
        mockHandler = mockk(relaxed = true)

        device = TestSpeedAndLocationDevice(mockContext, mockSensorManager)
        device.setWatchdogHandler(mockHandler)
    }

    @After
    fun tearDown() {
        try {
            unmockkAll()
        } catch (_: ConcurrentModificationException) {
            // Guard against MockK internal concurrency disposal race
        }
    }

    private fun createLocation(
        lat: Double,
        lon: Double,
        timeMs: Long,
        speed: Float = 0.0f,
        accuracy: Float = 5.0f
    ): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.latitude } returns lat
        every { loc.longitude } returns lon
        every { loc.time } returns timeMs
        every { loc.speed } returns speed
        every { loc.accuracy } returns accuracy
        every { loc.bearing } returns 0.0f
        every { loc.altitude } returns 500.0
        every { loc.provider } returns "gps"
        return loc
    }

    @Test
    fun testTeleportationSpike_isRejectedFromDistanceAccumulation() {
        // Fix 1 at t=1000ms
        val loc1 = createLocation(lat = 50.73199, lon = 13.74682, timeMs = 1000L)
        device.onNewLocation(loc1)

        // Initial distance should be 0.0
        assertEquals(0.0, device.getDistanceSensor().value ?: 0.0, 0.001)

        // Fix 2: 1000ms later, huge cell tower multipath jump of 4245.6m (v = 4245.6 m/s > 70.0 m/s)
        val loc2 = createLocation(lat = 50.70461, lon = 13.78871, timeMs = 2000L)
        every { loc1.distanceTo(loc2) } returns 4245.6f
        device.onNewLocation(loc2)

        // Implausible jump must NOT increment distance accumulators
        assertEquals(0.0, device.getDistanceSensor().value ?: 0.0, 0.001)
        assertEquals(0.0, device.getLapDistanceSensor().value ?: 0.0, 0.001)

        // Fix 3: 1000ms later, normal movement of 15.0m (v = 15.0 m/s <= 70.0 m/s)
        val loc3 = createLocation(lat = 50.70470, lon = 13.78880, timeMs = 3000L)
        every { loc2.distanceTo(loc3) } returns 15.0f
        device.onNewLocation(loc3)

        // Only legitimate distance (15.0m) should be accumulated
        assertEquals(15.0, device.getDistanceSensor().value ?: 0.0, 0.001)
        assertEquals(15.0, device.getLapDistanceSensor().value ?: 0.0, 0.001)
    }

    @Test
    fun testPlausibleHighSpeed_isAccumulated() {
        val loc1 = createLocation(lat = 48.0, lon = 11.0, timeMs = 1000L)
        device.onNewLocation(loc1)

        // 1000ms later, moving at 65.0 m/s (234 km/h <= 252 km/h limit)
        val loc2 = createLocation(lat = 48.0005, lon = 11.0005, timeMs = 2000L)
        every { loc1.distanceTo(loc2) } returns 65.0f
        device.onNewLocation(loc2)

        assertEquals(65.0, device.getDistanceSensor().value ?: 0.0, 0.001)
        assertEquals(65.0, device.getLapDistanceSensor().value ?: 0.0, 0.001)
    }

    @Test
    fun testSubSecondTeleportationSpike_isRejected() {
        val loc1 = createLocation(lat = 48.0, lon = 11.0, timeMs = 1000L)
        device.onNewLocation(loc1)

        // Fix arrives 500ms later with distance delta 100m (v = 200 m/s > 70.0 m/s)
        val loc2 = createLocation(lat = 48.0005, lon = 11.0005, timeMs = 1500L)
        every { loc1.distanceTo(loc2) } returns 100.0f
        device.onNewLocation(loc2)

        assertEquals(0.0, device.getDistanceSensor().value ?: 0.0, 0.001)
    }

    @Test
    fun testPauseOrLongInterval_dtOver30Seconds_accumulatesNormally() {
        val loc1 = createLocation(lat = 48.0, lon = 11.0, timeMs = 1000L)
        device.onNewLocation(loc1)

        // Fix arrives 60 seconds later with 50m distance (dt_ms >= 30000)
        val loc2 = createLocation(lat = 48.0005, lon = 11.0005, timeMs = 61000L)
        every { loc1.distanceTo(loc2) } returns 50.0f
        device.onNewLocation(loc2)

        assertEquals(50.0, device.getDistanceSensor().value ?: 0.0, 0.001)
    }
}
