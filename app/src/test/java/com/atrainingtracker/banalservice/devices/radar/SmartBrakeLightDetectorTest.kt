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

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f) // 25.2 km/h >= 2.5 m/s

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
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.0f, 0f, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -3.0f, 0f, nowMs = 1150L) // 150 ms duration
        // Brake released early:
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, 0f, 0f, nowMs = 1200L)

        assertFalse("Transient 150ms deceleration must not trigger brake event", SmartBrakeLightDetector.isBraking)
        assertFalse(RadarLightController.isBrakeActive)
    }

    @Test
    fun testDecelerationDetection_belowMinSpeed_doesNotTrigger() {
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 1.5f) // < 2.5 m/s (5.4 km/h)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1500L)

        assertFalse("Deceleration below 2.5 m/s riding speed must not trigger brake light", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testPotholeRejection_highVerticalShock_doesNotTrigger() {
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f)

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
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1400L)

        assertFalse("Auto-bypassed detector must never trigger software brake light", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testDisabledPreference_doesNotTrigger() {
        SmartBrakeLightDetector.isEnabled = false
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f)

        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, -4.0f, 0f, nowMs = 1400L)

        assertFalse("Disabled detector must never trigger software brake light", SmartBrakeLightDetector.isBraking)
    }

    @Test
    fun testAttitudeCompensation_angledMount() {
        // Phone tilted 45 degrees: gravity has equal Y and Z components
        val gComp = (9.81f / Math.sqrt(2.0)).toFloat()
        SmartBrakeLightDetector.onGravityChanged(0f, gComp, gComp)
        SmartBrakeLightDetector.onSpeedChanged(speedMps = 7.0f)

        // Deceleration acting along tilted forward axis
        val aComp = (-2.5f / Math.sqrt(2.0)).toFloat()
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, aComp, -aComp, nowMs = 1000L)
        SmartBrakeLightDetector.onLinearAccelerationChanged(0f, aComp, -aComp, nowMs = 1300L)

        assertTrue("Attitude-compensated forward deceleration must trigger brake event", SmartBrakeLightDetector.isBraking)
    }
}
