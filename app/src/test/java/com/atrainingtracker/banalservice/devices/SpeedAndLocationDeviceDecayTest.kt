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
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying GPS speed inactivity watchdog, progressive exponential decay,
 * pace nullification, zero-division protection, and distance accumulation immunity
 * (REQ-TRK-013, TST-TRK-005, ATT-2616).
 */
class SpeedAndLocationDeviceDecayTest {

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
        fun getPaceSensor(): MySensor<Double> = mPaceSensor
        fun getDistanceSensor(): MyDoubleAccumulatorSensor = mDistanceSensor
        fun getLapDistanceSensor(): MyDoubleAccumulatorSensor = mLapDistanceSensor
        fun callLocationUnavailable() = LocationUnavailable()
        fun callAccumulatorsReset() = onAccumulatorsReset()
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
        unmockkAll()
    }

    private fun createMockLocation(speed: Float, accuracy: Float = 5.0f, distanceDelta: Float = 0.0f): Location {
        val loc = mockk<Location>(relaxed = true)
        every { loc.speed } returns speed
        every { loc.accuracy } returns accuracy
        every { loc.longitude } returns 11.58
        every { loc.latitude } returns 48.14
        every { loc.bearing } returns 90.0f
        every { loc.altitude } returns 520.0
        every { loc.provider } returns "gps"
        every { loc.distanceTo(any()) } returns distanceDelta
        return loc
    }

    @Test
    fun testInitialState_speedIsZeroAndPaceIsNull() {
        assertEquals(0.0, device.speed, 0.001)
        assertEquals(0.0, device.getSpeedSensor().value ?: -1.0, 0.001)
        assertNull(device.getPaceSensor().value)
    }

    @Test
    fun testContinuousFixes_calculateSmoothedSpeedAndPace() {
        val loc1 = createMockLocation(speed = 10.0f)
        device.onNewLocation(loc1)

        // Initial speed was 0.0, smoothed: (0 + 10) / 2 = 5.0 m/s
        assertEquals(5.0, device.speed, 0.001)
        assertEquals(5.0, device.getSpeedSensor().value ?: 0.0, 0.001)
        // Pace = 1 / 5.0 = 0.2 s/m
        assertEquals(0.2, device.getPaceSensor().value ?: 0.0, 0.001)

        val loc2 = createMockLocation(speed = 10.0f)
        device.onNewLocation(loc2)

        // Smoothed: (5.0 + 10.0) / 2 = 7.5 m/s
        assertEquals(7.5, device.speed, 0.001)
        assertEquals(7.5, device.getSpeedSensor().value ?: 0.0, 0.001)
        assertEquals(1.0 / 7.5, device.getPaceSensor().value ?: 0.0, 0.001)

        // Verify watchdog was rescheduled with 5000ms delay
        verify { mockHandler.postDelayed(any(), SpeedAndLocationDevice.INACTIVITY_TIMEOUT_MS) }
    }

    @Test
    fun testInactivityTimeout_triggersProgressiveExponentialDecay() {
        // Establish initial speed
        val loc = createMockLocation(speed = 8.0f)
        device.onNewLocation(loc)
        val initialSpeed = device.speed
        assertEquals(4.0, initialSpeed, 0.001)

        // Trigger inactivity timeout -> first decay tick
        device.triggerInactivityTimeoutForTesting()
        // Speed halved: 4.0 * 0.5 = 2.0 m/s
        assertEquals(2.0, device.speed, 0.001)
        assertEquals(2.0, device.getSpeedSensor().value ?: 0.0, 0.001)
        assertEquals(0.5, device.getPaceSensor().value ?: 0.0, 0.001)

        // Tick 2: 2.0 * 0.5 = 1.0 m/s
        device.triggerDecayTickForTesting()
        assertEquals(1.0, device.speed, 0.001)
        assertEquals(1.0, device.getSpeedSensor().value ?: 0.0, 0.001)
        assertEquals(1.0, device.getPaceSensor().value ?: 0.0, 0.001)

        // Tick 3: 1.0 * 0.5 = 0.5 m/s
        device.triggerDecayTickForTesting()
        assertEquals(0.5, device.speed, 0.001)
        assertEquals(0.5, device.getSpeedSensor().value ?: 0.0, 0.001)
        assertEquals(2.0, device.getPaceSensor().value ?: 0.0, 0.001)

        // Tick 4: 0.5 * 0.5 = 0.25 m/s
        device.triggerDecayTickForTesting()
        assertEquals(0.25, device.speed, 0.001)

        // Tick 5: 0.25 * 0.5 = 0.125 m/s
        device.triggerDecayTickForTesting()
        assertEquals(0.125, device.speed, 0.001)

        // Tick 6: 0.125 * 0.5 = 0.0625 m/s <= 0.1 -> clamps strictly to 0.0
        device.triggerDecayTickForTesting()
        assertEquals(0.0, device.speed, 0.0001)
        assertEquals(0.0, device.getSpeedSensor().value ?: -1.0, 0.0001)
        assertNull("Pace must be null when speed drops to 0.0", device.getPaceSensor().value)
    }

    @Test
    fun testPaceNullification_neverEmitsInfinityOrNaN() {
        val loc = createMockLocation(speed = 0.0f)
        device.onNewLocation(loc)

        assertEquals(0.0, device.speed, 0.001)
        assertNull("Pace must be null for 0 speed", device.getPaceSensor().value)

        // Simulate decay tick when speed is already 0.0
        device.triggerDecayTickForTesting()
        assertEquals(0.0, device.speed, 0.001)
        assertNull(device.getPaceSensor().value)
    }

    @Test
    fun testDecayTicks_doNotAccumulatePhantomDistance() {
        val loc1 = createMockLocation(speed = 10.0f, distanceDelta = 50.0f)
        device.onNewLocation(loc1)
        val loc2 = createMockLocation(speed = 10.0f, distanceDelta = 50.0f)
        device.onNewLocation(loc2)

        val distBefore = device.getDistanceSensor().value ?: 0.0
        val lapDistBefore = device.getLapDistanceSensor().value ?: 0.0
        assertEquals(50.0, distBefore, 0.001)
        assertEquals(50.0, lapDistBefore, 0.001)

        // Run multiple decay ticks
        for (i in 1..10) {
            device.triggerDecayTickForTesting()
        }

        // Speed is now 0
        assertEquals(0.0, device.speed, 0.0001)

        // Distance sensors MUST be completely unchanged
        assertEquals(distBefore, device.getDistanceSensor().value ?: 0.0, 0.0001)
        assertEquals(lapDistBefore, device.getLapDistanceSensor().value ?: 0.0, 0.0001)
    }

    @Test
    fun testFreshLocationFix_cancelsDecayAndResumesActiveSpeed() {
        val loc1 = createMockLocation(speed = 10.0f)
        device.onNewLocation(loc1)

        // Decay to zero
        for (i in 1..8) {
            device.triggerDecayTickForTesting()
        }
        assertEquals(0.0, device.speed, 0.0001)
        assertNull(device.getPaceSensor().value)

        // Fresh fix arrives with speed 6.0 m/s
        val locFresh = createMockLocation(speed = 6.0f)
        device.onNewLocation(locFresh)

        // Speed recovers: (0 + 6) / 2 = 3.0 m/s
        assertEquals(3.0, device.speed, 0.001)
        assertEquals(3.0, device.getSpeedSensor().value ?: 0.0, 0.001)
        assertEquals(1.0 / 3.0, device.getPaceSensor().value ?: 0.0, 0.001)
    }

    @Test
    fun testLocationUnavailable_decaysSpeedImmediately() {
        val loc = createMockLocation(speed = 10.0f)
        device.onNewLocation(loc)
        assertEquals(5.0, device.speed, 0.001)

        device.callLocationUnavailable()

        // Speed decayed immediately by 1 tick upon becoming unavailable
        assertEquals(2.5, device.speed, 0.001)
    }

    @Test
    fun testOnAccumulatorsReset_clearsSpeedPaceAndWatchdog() {
        val loc = createMockLocation(speed = 10.0f)
        device.onNewLocation(loc)
        assertTrue(device.speed > 0)
        assertNotNull(device.getPaceSensor().value)

        device.callAccumulatorsReset()

        assertEquals(0.0, device.speed, 0.0001)
        assertEquals(0.0, device.getSpeedSensor().value ?: -1.0, 0.0001)
        assertNull(device.getPaceSensor().value)
        verify { mockHandler.removeCallbacks(any()) }
    }

    @Test
    fun testShutDown_cancelsWatchdogCallbacks() {
        val loc = createMockLocation(speed = 10.0f)
        device.onNewLocation(loc)

        device.shutDown()

        verify { mockHandler.removeCallbacks(any()) }
    }
}
