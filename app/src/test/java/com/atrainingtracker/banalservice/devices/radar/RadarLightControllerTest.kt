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
import android.util.Log
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
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

/**
 * Unit test suite verifying [RadarLightController] and [RadarLightMode] (TST-CON-031.1, TST-CON-031.2, TST-CON-031.4).
 */
class RadarLightControllerTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        RadarLightController.reset()
        RadarStateRepository.reset()
        RadarTrafficSimulator.stop()
    }

    @After
    fun tearDown() {
        RadarLightController.reset()
        RadarStateRepository.reset()
        RadarTrafficSimulator.stop()
        unmockkStatic(Log::class)
    }

    @Test
    fun testRadarLightMode_commandByteMappingAndResolution() {
        assertEquals(0x00.toByte(), RadarLightMode.OFF.commandByte)
        assertEquals(0x01.toByte(), RadarLightMode.SOLID.commandByte)
        assertEquals(0x05.toByte(), RadarLightMode.PELOTON.commandByte)
        assertEquals(0x06.toByte(), RadarLightMode.NIGHT_FLASH.commandByte)
        assertEquals(0x07.toByte(), RadarLightMode.DAY_FLASH.commandByte)

        assertEquals(RadarLightMode.OFF, RadarLightMode.fromCommandByte(0x00))
        assertEquals(RadarLightMode.SOLID, RadarLightMode.fromCommandByte(0x01))
        assertEquals(RadarLightMode.PELOTON, RadarLightMode.fromCommandByte(0x05))
        assertEquals(RadarLightMode.NIGHT_FLASH, RadarLightMode.fromCommandByte(0x06))
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightMode.fromCommandByte(0x07))
        assertEquals(RadarLightMode.OFF, RadarLightMode.fromCommandByte(0xFF.toByte()))
    }

    @Test
    fun testRadarLightMode_stvzoCapabilityPruning() {
        assertTrue(RadarLightMode.OFF.isStvzoCompliant)
        assertTrue(RadarLightMode.SOLID.isStvzoCompliant)
        assertFalse(RadarLightMode.PELOTON.isStvzoCompliant)
        assertFalse(RadarLightMode.NIGHT_FLASH.isStvzoCompliant)
        assertFalse(RadarLightMode.DAY_FLASH.isStvzoCompliant)

        val stvzoModes = RadarLightMode.getAvailableModes(isStvzo = true)
        assertEquals(listOf(RadarLightMode.OFF, RadarLightMode.SOLID), stvzoModes)

        val allModes = RadarLightMode.getAvailableModes(isStvzo = false)
        assertEquals(5, allModes.size)
        assertTrue(allModes.containsAll(RadarLightMode.entries))
    }

    @Test
    fun testRadarLightController_registerAndSetMode_dispatchesToDevice() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true

        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)
        assertTrue(RadarLightController.isLightControlAvailable.value)
        assertFalse(RadarLightController.isStvzo.value)
        assertTrue(RadarStateRepository.getState().isLightControlSupported)

        val dispatched = RadarLightController.setLightMode(RadarLightMode.DAY_FLASH)
        assertTrue(dispatched)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.DAY_FLASH, RadarStateRepository.getState().lightMode)
        verify { mockDevice.setLightMode(RadarLightMode.DAY_FLASH) }

        RadarLightController.unregisterRadarDevice(mockDevice)
        assertFalse(RadarLightController.isLightControlAvailable.value)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)
        assertFalse(RadarStateRepository.getState().isLightControlSupported)
    }

    @Test
    fun testRadarLightController_stvzoAutoPruning_fallsBackToSolid() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true

        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = true)
        assertTrue(RadarLightController.isStvzo.value)

        // Attempting to set DAY_FLASH when StVZO is active should fallback to SOLID
        RadarLightController.setLightMode(RadarLightMode.DAY_FLASH)
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.SOLID, RadarStateRepository.getState().lightMode)
        verify { mockDevice.setLightMode(RadarLightMode.SOLID) }
    }

    @Test
    fun testRadarLightController_workoutLifecycleHooks_safeWithNullFilesDir() {
        // Plain JVM test with null context
        RadarLightController.onWorkoutStarted(null)
        RadarLightController.onWorkoutStopped(null)

        // Mock context with null filesDir (simulating unmocked JVM context)
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.filesDir } returns null

        RadarLightController.onWorkoutStarted(mockContext)
        RadarLightController.onWorkoutStopped(mockContext)

        // Verifies zero exceptions and state remains uncorrupted
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)
    }

    @Test
    fun testRadarLightController_gatedStateMutation_whenDispatchFails_stateNotMutated() {
        // No device connected, simulator inactive
        assertFalse(RadarLightController.isLightControlAvailable.value)
        assertFalse(RadarTrafficSimulator.isSimulating)

        val dispatched = RadarLightController.setLightMode(RadarLightMode.DAY_FLASH)
        assertFalse(dispatched)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.OFF, RadarStateRepository.getState().lightMode)
    }

    @Test
    fun testRadarLightController_updateHardwareLightMode_reflectsGattRead() {
        RadarLightController.updateHardwareLightMode(RadarLightMode.PELOTON)
        assertEquals(RadarLightMode.PELOTON, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.PELOTON, RadarStateRepository.getState().lightMode)
    }

    @Test
    fun testRadarLightController_synchronousAutoSwitch_startAndStop() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        RadarLightController.updateCachedPreferences(autoControl = true, defaultMode = RadarLightMode.DAY_FLASH)

        val mockContext = mockk<Context>(relaxed = true)
        RadarLightController.onWorkoutStarted(mockContext)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.DAY_FLASH) }

        RadarLightController.onWorkoutStopped(mockContext)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.OFF) }
    }

    @Test
    fun testRadarLightController_trafficSimulatorIntegration() {
        RadarTrafficSimulator.tick(RadarScenario.SINGLE_CAR, 0L)
        RadarLightController.setLightMode(RadarLightMode.NIGHT_FLASH)

        assertEquals(RadarLightMode.NIGHT_FLASH, RadarTrafficSimulator.simulatedLightMode)
        assertEquals(RadarLightMode.NIGHT_FLASH, RadarStateRepository.getState().lightMode)
        assertTrue(RadarStateRepository.getState().isLightControlSupported)
    }

    @Test
    fun testRadarLightController_radarLightDeviceClientInterface_supportsAntPlusClient() {
        val mockClient = mockk<RadarLightDeviceClient>(relaxed = true)
        every { mockClient.setLightMode(any()) } returns true

        RadarLightController.registerRadarDevice(mockClient, lightSupported = true, isStvzo = false)
        assertTrue(RadarLightController.isLightControlAvailable.value)

        val dispatched = RadarLightController.setLightMode(RadarLightMode.DAY_FLASH)
        assertTrue(dispatched)
        verify { mockClient.setLightMode(RadarLightMode.DAY_FLASH) }

        RadarLightController.unregisterRadarDevice(mockClient)
        assertFalse(RadarLightController.isLightControlAvailable.value)
    }

    @Test
    fun testRadarLightController_triggerBrakeLight_dispatchesBrakeModeAndRestoresPreBrakeModeAfterHold() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        // Initial mode: PELOTON (8 lm)
        RadarLightController.setLightMode(RadarLightMode.PELOTON)
        assertEquals(RadarLightMode.PELOTON, RadarLightController.currentMode.value)

        // Trigger brake light at t = 1000ms
        val dispatched = RadarLightController.triggerBrakeLight(active = true, brakeMode = RadarLightMode.DAY_FLASH, nowMs = 1000L)
        assertTrue(dispatched)
        assertTrue(RadarLightController.isBrakeActive)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.DAY_FLASH) }

        // Deceleration ceases at t = 2000ms: brake released, entering 2.0s hold window
        RadarLightController.triggerBrakeLight(active = false, nowMs = 2000L)
        assertTrue("Brake light must remain active during 2.0s hold window", RadarLightController.isBrakeActive)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)

        // Check at t = 3500ms (1.5s into hold window): must still be holding
        val restoredEarly = RadarLightController.checkAndRestorePreBrakeMode(nowMs = 3500L)
        assertFalse(restoredEarly)
        assertTrue(RadarLightController.isBrakeActive)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)

        // Check at t = 4000ms (exactly 2.0s post-brake): must restore PELOTON
        val restored = RadarLightController.checkAndRestorePreBrakeMode(nowMs = 4000L)
        assertTrue(restored)
        assertFalse(RadarLightController.isBrakeActive)
        assertEquals(RadarLightMode.PELOTON, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.PELOTON) }
    }

    @Test
    fun testRadarLightController_triggerBrakeLight_underStvzo_strictlyClampedToSolid() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = true)

        RadarLightController.setLightMode(RadarLightMode.OFF)

        // Request DAY_FLASH during braking on StVZO unit:
        RadarLightController.triggerBrakeLight(active = true, brakeMode = RadarLightMode.DAY_FLASH, nowMs = 1000L)
        assertTrue(RadarLightController.isBrakeActive)
        assertEquals("Brake mode under StVZO must be strictly clamped to SOLID", RadarLightMode.SOLID, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.SOLID) }
    }

    @Test
    fun testRadarLightController_hardwareAutoBypass_rearVue820() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        // Register device with hardware brake sensor present (RearVue 820)
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false, hasHardwareBrakeSensor = true)

        assertTrue(RadarLightController.hasHardwareBrakeSensor.value)
        assertTrue(RadarLightController.isAutoBypassed.value)
        assertTrue(SmartBrakeLightDetector.isAutoBypassed)

        // Software brake trigger must be cleanly bypassed
        val triggered = RadarLightController.triggerBrakeLight(active = true, brakeMode = RadarLightMode.DAY_FLASH, nowMs = 1000L)
        assertFalse(triggered)
        assertFalse(RadarLightController.isBrakeActive)
    }

    @Test
    fun testRadarLightController_isRearVue820Model() {
        assertTrue(RadarLightController.isRearVue820Model("010-03180-00"))
        assertTrue(RadarLightController.isRearVue820Model("010-03180-10"))
        assertTrue(RadarLightController.isRearVue820Model("Garmin RearVue 820"))
        assertTrue(RadarLightController.isRearVue820Model("RearVue820"))
        assertFalse(RadarLightController.isRearVue820Model("RTL515"))
        assertFalse(RadarLightController.isRearVue820Model("RTL516"))
        assertFalse(RadarLightController.isRearVue820Model("Gardia R300L"))
        assertFalse(RadarLightController.isRearVue820Model(null))
    }
}
