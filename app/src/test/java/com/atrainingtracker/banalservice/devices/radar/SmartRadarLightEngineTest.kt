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

    @Test
    fun testStartAndStopListening_managesSensorsAndLifecycle() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockSensorManager = mockk<android.hardware.SensorManager>(relaxed = true)
        val mockPowerManager = mockk<android.os.PowerManager>(relaxed = true)
        val mockLightSensor = mockk<android.hardware.Sensor>(relaxed = true)
        val mockProxSensor = mockk<android.hardware.Sensor>(relaxed = true)

        every { mockSensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) } returns mockLightSensor
        every { mockSensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY) } returns mockProxSensor
        every { mockSensorManager.registerListener(any(), any<android.hardware.Sensor>(), any()) } returns true

        SmartRadarLightEngine.startListening(mockContext, customSensorManager = mockSensorManager, customPowerManager = mockPowerManager)
        assertTrue(SmartRadarLightEngine.isListening)
        verify { mockSensorManager.registerListener(SmartRadarLightEngine, mockLightSensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) }
        verify { mockSensorManager.registerListener(SmartRadarLightEngine, mockProxSensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) }

        SmartRadarLightEngine.stopListening()
        assertFalse(SmartRadarLightEngine.isListening)
        verify { mockSensorManager.unregisterListener(SmartRadarLightEngine) }
    }

    @Test
    fun testSetManualLightMode_overridesAutomation() {
        SmartRadarLightEngine.isAutomationEnabled = true
        RadarLightController.setManualLightMode(RadarLightMode.PELOTON)
        assertTrue(SmartRadarLightEngine.isManualOverridden)
        assertEquals(RadarLightMode.PELOTON, RadarLightController.currentMode.value)
    }

    @Test
    fun testLocationAndTimeChanged_updatesSolarProfile() {
        SmartRadarLightEngine.onLocationAndTimeChanged(0.0, 0.0, 1700050000000L)
        assertFalse(SmartRadarLightEngine.isTunnelActive)
    }

    /**
     * TST-CON-059.1 / REQ-CON-059.1 (ATT-8673):
     * resumeAutomation() syncs live RadarStateRepository.threatState and dispatches immediately
     * even when elapsedRealtime < RATE_LIMIT_INTERVAL_MS (1000L) or < 1500ms after prior dispatch.
     */
    @Test
    fun testResumeAutomation_syncsLiveThreatAndBypassesRateLimit_tstCon059_1() {
        // setUp() mocks SystemClock.elapsedRealtime() == 1000L (< 1500ms rate-limit interval)
        SmartRadarLightEngine.onManualOverride(RadarLightMode.SOLID)
        assertTrue(SmartRadarLightEngine.isManualOverridden)

        // Publish live APPROACHING threat into RadarStateRepository while engine is NOT observing
        RadarStateRepository.updateThreatState(
            RadarThreatState(
                connectionState = RadarConnectionState.CONNECTED,
                overallThreatLevel = ThreatLevel.APPROACHING,
                targets = listOf(RadarTarget(1, 45f, 14f, ThreatLevel.APPROACHING)),
                lastUpdateRealtimeMs = 1000L,
                source = RadarSource.BLE
            )
        )
        // Override with PELOTON manually, then resumeAutomation() -> must sync Stage 1 (SOLID) immediately
        SmartRadarLightEngine.onManualOverride(RadarLightMode.PELOTON)
        SmartRadarLightEngine.resumeAutomation()
        assertFalse(SmartRadarLightEngine.isManualOverridden)
        assertEquals(1, SmartRadarLightEngine.currentThreatStage)
        assertEquals(RadarLightMode.SOLID, SmartRadarLightEngine.lastDispatchedMode)
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)
        verify { mockClient.setLightMode(RadarLightMode.SOLID) }

        // Clear threat and switch manual -> resumeAutomation() < 1500ms later still dispatches OFF immediately
        every { android.os.SystemClock.elapsedRealtime() } returns 5000L
        RadarStateRepository.updateThreatState(
            RadarThreatState(
                connectionState = RadarConnectionState.CONNECTED,
                overallThreatLevel = ThreatLevel.NONE,
                targets = emptyList(),
                lastUpdateRealtimeMs = 5000L,
                source = RadarSource.BLE
            )
        )
        SmartRadarLightEngine.onManualOverride(RadarLightMode.DAY_FLASH)
        SmartRadarLightEngine.resumeAutomation()
        assertEquals(RadarLightMode.OFF, SmartRadarLightEngine.lastDispatchedMode)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)
        verify { mockClient.setLightMode(RadarLightMode.OFF) }
    }

    /**
     * TST-CON-059.2 / REQ-CON-059.2 (ATT-8673):
     * onManualOverride() cancels pending rate-limit flush, guarded flushPendingDispatch() never overwrites
     * manual/brake mode, stop() resets lastDispatchedMode, and hardware drift is reconciled.
     */
    @Test
    fun testManualOverrideAndStop_cancelPendingRateLimitAndResetDispatchedCache_tstCon059_2() {
        // Dispatch SOLID at t = 10_000ms
        val approach = RadarThreatState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(RadarTarget(1, 50f, 12f, ThreatLevel.APPROACHING))
        )
        SmartRadarLightEngine.onThreatStateChanged(approach, nowMs = 10_000L)
        assertEquals(RadarLightMode.SOLID, SmartRadarLightEngine.lastDispatchedMode)

        // Rapid FAST_APPROACHING at t = 10_500ms queues pending DAY_FLASH
        val fast = RadarThreatState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(RadarTarget(1, 25f, 22f, ThreatLevel.FAST_APPROACHING))
        )
        SmartRadarLightEngine.onThreatStateChanged(fast, nowMs = 10_500L)
        assertTrue("Pending dispatch must be queued inside 1.5s rate limit", SmartRadarLightEngine.hasPendingDispatch())

        // User switches to manual PELOTON -> must cancel queued rate-limit dispatch
        SmartRadarLightEngine.onManualOverride(RadarLightMode.PELOTON)
        assertFalse("onManualOverride must clear pending rate-limit dispatch", SmartRadarLightEngine.hasPendingDispatch())
        assertFalse("Flushing after manual override must not dispatch", SmartRadarLightEngine.flushPendingDispatch(nowMs = 15_000L))
        assertEquals(RadarLightMode.PELOTON, SmartRadarLightEngine.lastDispatchedMode)

        // Hardware drift reconciliation: external physical button changes mode to SOLID while lastDispatchedMode == OFF
        SmartRadarLightEngine.resumeAutomation()
        SmartRadarLightEngine.onThreatStateChanged(RadarThreatState(overallThreatLevel = ThreatLevel.NONE), nowMs = 30_000L)
        assertEquals(RadarLightMode.OFF, SmartRadarLightEngine.lastDispatchedMode)
        RadarLightController.updateHardwareLightMode(RadarLightMode.SOLID)
        SmartRadarLightEngine.evaluateAndDispatch(nowMs = 35_000L)
        assertEquals("Hardware drift must be reconciled back to OFF", RadarLightMode.OFF, RadarLightController.currentMode.value)

        // stop() clears lastDispatchedMode and disables automation cleanly
        SmartRadarLightEngine.stop()
        assertEquals(null, SmartRadarLightEngine.lastDispatchedMode)
        assertFalse(SmartRadarLightEngine.isAutomationEnabled)
        assertFalse(SmartRadarLightEngine.hasPendingDispatch())
    }
}
