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
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying [SmartRadarLightEngine] (TST-CON-034.1, TST-CON-034.3, TST-CON-034.4, TST-CON-034.5).
 */
class SmartRadarLightEngineTest {

    private lateinit var mockClient: RadarLightDeviceClient

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(android.os.SystemClock::class)
        every { android.os.SystemClock.elapsedRealtime() } returns 1000L

        RadarLightController.reset()
        RadarStateRepository.reset()
        SmartRadarLightEngine.reset()

        mockClient = mockk(relaxed = true)
        every { mockClient.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockClient, lightSupported = true, isStvzo = false)

        SmartRadarLightEngine.isAutomationEnabled = true
    }

    @After
    fun tearDown() {
        SmartRadarLightEngine.reset()
        RadarLightController.reset()
        RadarStateRepository.reset()
        unmockkAll()
    }

    @Test
    fun testPocketAndScreenOffGating_tstCon034_1() {
        // Screen is OFF: 0 lx in pocket should be ignored (no tunnel trigger)
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 0.0f,
            isScreenInteractive = false,
            isProximityNear = false,
            nowMs = 1000L
        )
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 0.0f,
            isScreenInteractive = false,
            isProximityNear = false,
            nowMs = 3000L
        )
        assertFalse("Tunnel must not activate when screen is OFF", SmartRadarLightEngine.isTunnelActive)

        // Screen is ON but phone in jersey pocket (proximity NEAR): 0 lx should be ignored
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 0.0f,
            isScreenInteractive = true,
            isProximityNear = true,
            nowMs = 5000L
        )
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 0.0f,
            isScreenInteractive = true,
            isProximityNear = true,
            nowMs = 7000L
        )
        assertFalse("Tunnel must not activate when proximity is NEAR", SmartRadarLightEngine.isTunnelActive)
    }

    @Test
    fun testFastTunnelEntryAndExitHysteresis_tstCon034_3() {
        // Screen ON and unoccluded in sunlight (> 1000 lx)
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 3000.0f,
            isScreenInteractive = true,
            isProximityNear = false,
            nowMs = 1000L
        )
        assertFalse(SmartRadarLightEngine.isTunnelActive)

        // Enter tunnel: sudden drop to 20 lx
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 20.0f,
            isScreenInteractive = true,
            isProximityNear = false,
            nowMs = 2000L
        )
        // Not active yet (< 1.5s hysteresis)
        assertFalse(SmartRadarLightEngine.isTunnelActive)

        // Sustained < 50 lx for >= 1.5s
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 20.0f,
            isScreenInteractive = true,
            isProximityNear = false,
            nowMs = 3600L
        )
        assertTrue("Tunnel mode must activate within 2s of sustained darkness", SmartRadarLightEngine.isTunnelActive)
        assertEquals(RadarLightMode.PELOTON, SmartRadarLightEngine.lastDispatchedMode)

        // Exit tunnel into daylight: illuminance jumps to 2500 lx
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 2500.0f,
            isScreenInteractive = true,
            isProximityNear = false,
            nowMs = 4000L
        )
        // Hysteresis holds tunnel active (requires 10s bright)
        assertTrue("Tunnel mode must remain active during exit hysteresis", SmartRadarLightEngine.isTunnelActive)

        // After 10s of sustained bright light
        SmartRadarLightEngine.onAmbientLightChanged(
            lux = 2500.0f,
            isScreenInteractive = true,
            isProximityNear = false,
            nowMs = 14100L
        )
        assertFalse("Tunnel mode must deactivate after 10s of sustained bright light", SmartRadarLightEngine.isTunnelActive)
    }

    @Test
    fun test3StageThreatEscalationMatrix_nightAntiGlareCap_tstCon034_4() {
        // Set environment to DUSK_NIGHT
        SmartRadarLightEngine.onLocationAndTimeChanged(52.52, 13.405, epochTimeMs = 1782083160000L, nowMs = 1000L)
        assertEquals(SolarElevationCalculator.SolarProfile.DUSK_NIGHT, SmartRadarLightEngine.currentSolarProfile)

        // Stage 0 (No threat): Night baseline should be PELOTON (8 lm non-dazzling)
        val modeStage0 = SmartRadarLightEngine.computeTargetMode(
            isTunnel = false,
            solarProfile = SolarElevationCalculator.SolarProfile.DUSK_NIGHT,
            threatStage = 0
        )
        assertEquals(RadarLightMode.PELOTON, modeStage0)

        // Stage 1 (Approaching vehicle): Escalate to SOLID (25 lm)
        val modeStage1 = SmartRadarLightEngine.computeTargetMode(
            isTunnel = false,
            solarProfile = SolarElevationCalculator.SolarProfile.DUSK_NIGHT,
            threatStage = 1
        )
        assertEquals(RadarLightMode.SOLID, modeStage1)

        // Stage 2 (Fast approaching / In-lane threat): Escalate to NIGHT_FLASH (40 lm)
        val modeStage2 = SmartRadarLightEngine.computeTargetMode(
            isTunnel = false,
            solarProfile = SolarElevationCalculator.SolarProfile.DUSK_NIGHT,
            threatStage = 2
        )
        assertEquals(RadarLightMode.NIGHT_FLASH, modeStage2)

        // STRICT ANTI-GLARE INVARIANT: Night mode must NEVER be DAY_FLASH (100 lm)
        assertNotEquals(RadarLightMode.DAY_FLASH, modeStage0)
        assertNotEquals(RadarLightMode.DAY_FLASH, modeStage1)
        assertNotEquals(RadarLightMode.DAY_FLASH, modeStage2)

        // In daytime, Stage 2 DOES escalate to DAY_FLASH (100 lm)
        val modeDayStage2 = SmartRadarLightEngine.computeTargetMode(
            isTunnel = false,
            solarProfile = SolarElevationCalculator.SolarProfile.DAY,
            threatStage = 2
        )
        assertEquals(RadarLightMode.DAY_FLASH, modeDayStage2)
    }

    @Test
    fun testCommandRateLimitingAndCooldown_tstCon034_5() {
        var now = 10000L
        SmartRadarLightEngine.onAmbientLightChanged(3000f, true, false, now)

        val threatState = RadarState(
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(RadarTarget(targetId = 0, rangeMeters = 50f, approachSpeedMps = 15f, threatLevel = ThreatLevel.APPROACHING))
        )

        // First dispatch at now = 10000
        SmartRadarLightEngine.onRadarStateChanged(threatState, now)
        assertEquals(RadarLightMode.SOLID, SmartRadarLightEngine.lastDispatchedMode)

        // Rapid state update 500ms later (less than 1.5s rate limit window)
        now += 500L
        val fastThreatState = RadarState(
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(RadarTarget(targetId = 0, rangeMeters = 30f, approachSpeedMps = 25f, threatLevel = ThreatLevel.FAST_APPROACHING))
        )
        SmartRadarLightEngine.onRadarStateChanged(fastThreatState, now)
        // Must NOT have dispatched immediately (still rate-limited to SOLID)
        assertEquals(RadarLightMode.SOLID, SmartRadarLightEngine.lastDispatchedMode)

        // After 1500ms has elapsed since first dispatch
        now += 1100L
        SmartRadarLightEngine.evaluateAndDispatch(now)
        assertEquals(RadarLightMode.DAY_FLASH, SmartRadarLightEngine.lastDispatchedMode)

        // Threat clears to NONE shortly after
        now += 500L
        val clearState = RadarState(overallThreatLevel = ThreatLevel.NONE, targets = emptyList())
        SmartRadarLightEngine.onRadarStateChanged(clearState, now)
        // Cooldown holds Stage 1 (SOLID) before stepping down to OFF
        assertEquals(1, SmartRadarLightEngine.currentThreatStage)

        // After 3.5s cooldown expires
        now += 4000L
        SmartRadarLightEngine.onRadarStateChanged(clearState, now)
        assertEquals(0, SmartRadarLightEngine.currentThreatStage)
        SmartRadarLightEngine.evaluateAndDispatch(now)
        assertEquals(RadarLightMode.OFF, SmartRadarLightEngine.lastDispatchedMode)
    }

    @Test
    fun testManualOverride_suspendsAutomationUntilResumed() {
        SmartRadarLightEngine.onManualOverride(RadarLightMode.PELOTON)
        assertTrue(SmartRadarLightEngine.isManualOverridden)

        // Automation dispatch should be ignored while manual override is active
        SmartRadarLightEngine.evaluateAndDispatch(5000L)
        assertEquals(RadarLightMode.PELOTON, SmartRadarLightEngine.lastDispatchedMode)

        SmartRadarLightEngine.resumeAutomation()
        assertFalse(SmartRadarLightEngine.isManualOverridden)
    }
}
