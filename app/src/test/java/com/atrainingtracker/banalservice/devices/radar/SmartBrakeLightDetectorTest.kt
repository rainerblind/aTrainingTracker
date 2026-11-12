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

package com.atrainingtracker.banalservice.devices.radar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unit test suite verifying [SmartBrakeLightDetector] (TST-CON-035.1, TST-CON-035.2).
 */
class SmartBrakeLightDetectorTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        SmartBrakeLightDetector.reset()
        RadarLightController.reset()
    }

    @After
    fun tearDown() {
        SmartBrakeLightDetector.reset()
        RadarLightController.reset()
        unmockkStatic(Log::class)
    }

    @Test
    fun testDecelerationDetection_triggersBrakeEventWhenSustained() {
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f, nowMs = 1000L) // 25.2 km/h >= 2.5 m/s

        // Deceleration a = -2.5 m/s^2 (<= -2.0 m/s^2 threshold)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -2.5f, 0f, nowMs = 1000L)
        assertFalse("Transient deceleration must not trigger immediately", SmartBrakeLightDetector.isBraking)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -2.5f, 0f, nowMs = 1200L)
        assertFalse("Deceleration at 200ms duration must not trigger (< 250ms)", SmartBrakeLightDetector.isBraking)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -2.5f, 0f, nowMs = 1250L)
        assertTrue("Deceleration at 250ms duration must trigger brake event", SmartBrakeLightDetector.isBraking)
        assertTrue("RadarLightController must receive active brake event", RadarLightController.isBrakeActive)

        // Deceleration ceases
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, 0f, 0f, nowMs = 1300L)
        assertFalse("Deceleration cessation must release detector braking flag", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testDecelerationDetection_transientDeceleration_doesNotTrigger() {
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f, nowMs = 1000L)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.0f, 0f, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.0f, 0f, nowMs = 1150L) // 150 ms duration
        // Brake released early:
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, 0f, 0f, nowMs = 1200L)

        assertFalse("Transient 150ms deceleration must not trigger brake event", SmartBrakeLightDetector.isBraking)
        assertFalse(RadarLightController.isBrakeActive)
    }

    @Test
    fun testDecelerationDetection_belowMinSpeed_doesNotTrigger() {
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 1.5f, nowMs = 1000L) // < 2.5 m/s (5.4 km/h)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1500L)

        assertFalse("Deceleration below 2.5 m/s riding speed must not trigger brake light", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testSpeedDerivative_acceleratingSpeed_rejectsDecelerationTrigger() {
        // Accelerating from 5 m/s to 8 m/s: dv/dt = +3.0 m/s^2 > 0 (Finding 2 / REQ-CON-035.1)
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 5.0f, nowMs = 1000L)
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 8.0f, nowMs = 2000L)
        assertTrue("Speed derivative dv/dt must be positive", SmartBrakeLightDetector.currentDvDt > 0f)

        // Pitch/jolt causing forward deceleration reading while accelerating:
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.0f, 0f, nowMs = 2000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.0f, 0f, nowMs = 2300L)

        assertFalse("Deceleration during positive acceleration (dv/dt > 0) must NOT trigger brake light", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testSpeedDerivative_deceleratingSpeed_allowsDecelerationTrigger() {
        // Decelerating from 8 m/s to 6 m/s: dv/dt = -2.0 m/s^2 <= 0 (Finding 2 / REQ-CON-035.1)
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 8.0f, nowMs = 1000L)
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 6.0f, nowMs = 2000L)
        assertTrue("Speed derivative dv/dt must be non-positive", SmartBrakeLightDetector.currentDvDt <= 0f)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.0f, 0f, nowMs = 2000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.0f, 0f, nowMs = 2300L)

        assertTrue("Deceleration during speed drop (dv/dt <= 0) MUST trigger brake light", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testPotholeRejection_highVerticalShock_doesNotTrigger() {
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f, nowMs = 1000L)

        // Vertical shock spike (e.g. hitting pothole at constant speed)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, 0f, 6.5f, nowMs = 1000L)
        assertTrue("Vertical shock must be detected", SmartBrakeLightDetector.lastVerticalShock > 4.0f)
        assertFalse("Vertical shock without forward deceleration must not trigger brake light", SmartBrakeLightDetector.isBraking)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, 0f, 7.0f, nowMs = 1300L)
        assertFalse(SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testAutoBypass_whenRearVue820Connected_bypassesSoftwareDetector() {
        SmartBrakeLightDetector.isAutoBypassed = true
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f, nowMs = 1000L)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1400L)

        assertFalse("Auto-bypassed detector must never trigger software brake light", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testDisabledPreference_doesNotTrigger() {
        SmartBrakeLightDetector.isEnabled = false
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f, nowMs = 1000L)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1400L)

        assertFalse("Disabled detector must never trigger software brake light", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testAttitudeCompensation_angledMount() {
        // Phone tilted 45 degrees: gravity has equal Y and Z components
        val gComp = (9.81f / Math.sqrt(2.0)).toFloat()
        SmartBrakeLightDetector.onGravityChanged(0f, gComp, gComp)
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f, nowMs = 1000L)

        // Deceleration acting along tilted forward axis
        val aComp = (-2.5f / Math.sqrt(2.0)).toFloat()
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, aComp, -aComp, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, aComp, -aComp, nowMs = 1300L)

        assertTrue("Attitude-compensated forward deceleration must trigger brake event", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testSensorLifecycle_startAndStopListening() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockSensorManager = mockk<SensorManager>(relaxed = true)
        val mockLinAccSensor = mockk<Sensor>(relaxed = true)
        val mockGravitySensor = mockk<Sensor>(relaxed = true)

        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) } returns mockLinAccSensor
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) } returns mockGravitySensor
        every { mockSensorManager.registerListener(any(), any<Sensor>(), any()) } returns true

        // Start listening
        SmartBrakeLightDetector.startListening(mockContext, customSensorManager = mockSensorManager)
        assertTrue(SmartBrakeLightDetector.isListening)
        verify { mockSensorManager.registerListener(SmartBrakeLightDetector, mockLinAccSensor, SensorManager.SENSOR_DELAY_GAME) }
        verify { mockSensorManager.registerListener(SmartBrakeLightDetector, mockGravitySensor, SensorManager.SENSOR_DELAY_UI) }

        // Stop listening
        SmartBrakeLightDetector.stopListening()
        assertFalse(SmartBrakeLightDetector.isListening)
        verify { mockSensorManager.unregisterListener(SmartBrakeLightDetector) }
    }

    @Test
    fun testThreadSafety_concurrentAccess_doesNotThrow() {
        val executor = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(100)

        SmartBrakeLightDetector.onSpeedChanged(10.0f, nowMs = 1000L)

        for (i in 0 until 100) {
            executor.submit {
                try {
                    when (i % 4) {
                        0 -> SmartBrakeLightDetector.onGravityChanged(0f, 0f, 9.81f)
                        1 -> SmartBrakeLightDetector.onSpeedChanged(10.0f - (i * 0.05f), nowMs = 1000L + i * 10L)
                        2 -> SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -2.5f, 0f, nowMs = 1000L + i * 10L)
                        else -> SmartBrakeLightDetector.reset()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Concurrent sensor execution timed out", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
    }
}
