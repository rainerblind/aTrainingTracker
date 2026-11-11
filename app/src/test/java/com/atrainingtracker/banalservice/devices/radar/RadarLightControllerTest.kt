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
}
