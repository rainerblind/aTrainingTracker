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
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.trainingtracker.settings.RadarLightControlMode
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
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
        SmartBrakeLightDetector.isEnabled = true
        RadarLightController.reset()
        RadarStateRepository.reset()
        RadarTrafficSimulator.stop()
        SmartRadarLightEngine.reset()
        TuningPreferencesDataStore.setInstanceForTesting(null)
    }

    @After
    fun tearDown() {
        TuningPreferencesDataStore.setInstanceForTesting(null)
        SmartBrakeLightDetector.isEnabled = true
        RadarLightController.reset()
        RadarStateRepository.reset()
        RadarTrafficSimulator.stop()
        SmartRadarLightEngine.reset()
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

        RadarLightController.updateCachedPreferences(autoMode = false, defaultMode = RadarLightMode.DAY_FLASH)

        val mockContext = mockk<Context>(relaxed = true)
        RadarLightController.onWorkoutStarted(mockContext)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.DAY_FLASH) }

        RadarLightController.onWorkoutStopped(mockContext)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.OFF) }
    }

    @Test
    fun testRadarLightController_manualMode_skipsAutoDispatchOnStartAndStop() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        RadarLightController.updateCachedPreferences(RadarLightControlMode.MANUAL, RadarLightMode.DAY_FLASH)

        val mockContext = mockk<Context>(relaxed = true)
        RadarLightController.onWorkoutStarted(mockContext)
        // In MANUAL, light mode is never dispatched on workout start
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)
        verify(exactly = 0) { mockDevice.setLightMode(any()) }
        assertFalse(SmartRadarLightEngine.isAutomationEnabled)

        RadarLightController.onWorkoutStopped(mockContext)
        // In MANUAL, light mode is never dispatched to OFF on workout finish
        verify(exactly = 0) { mockDevice.setLightMode(any()) }
    }

    @Test
    fun testRadarLightController_fixedWorkoutMode_dispatchesOnStartAndOffOnStop() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        RadarLightController.updateCachedPreferences(RadarLightControlMode.FIXED_WORKOUT_MODE, RadarLightMode.DAY_FLASH)

        val mockContext = mockk<Context>(relaxed = true)
        RadarLightController.onWorkoutStarted(mockContext)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.DAY_FLASH) }
        assertFalse(SmartRadarLightEngine.isAutomationEnabled)

        RadarLightController.onWorkoutStopped(mockContext)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.OFF) }
    }

    @Test
    fun testRadarLightController_smartAutomationMode_startsSmartEngineWithoutPreDispatchingDefaultMode() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        RadarLightController.updateCachedPreferences(RadarLightControlMode.SMART_AUTOMATION, RadarLightMode.DAY_FLASH)

        val mockContext = mockk<Context>(relaxed = true)
        RadarLightController.onWorkoutStarted(mockContext)
        // In SMART_AUTOMATION, defaultMode is NOT dispatched on workout start to avoid racing SmartRadarLightEngine
        verify(exactly = 0) { mockDevice.setLightMode(RadarLightMode.DAY_FLASH) }

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

    @Test
    fun testRadarLightController_triggerBrakeLight_underNightConditions_clampedToSolid() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        // Pre-brake mode: NIGHT_FLASH
        RadarLightController.setLightMode(RadarLightMode.NIGHT_FLASH)

        // Trigger brake requesting DAY_FLASH at night (Finding 4):
        RadarLightController.triggerBrakeLight(active = true, brakeMode = RadarLightMode.DAY_FLASH, nowMs = 1000L)
        assertTrue(RadarLightController.isBrakeActive)
        assertEquals("Night brake mode must be strictly clamped to SOLID (25 lm), never DAY_FLASH (100 lm)", RadarLightMode.SOLID, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.SOLID) }
    }

    @Test
    fun testRadarLightController_onWorkoutStopped_cleansUpBrakeHoldAndPreventsResuscitation() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        // Initial mode: SOLID, then trigger brake
        RadarLightController.setLightMode(RadarLightMode.SOLID)
        RadarLightController.triggerBrakeLight(active = true, brakeMode = RadarLightMode.DAY_FLASH, nowMs = 1000L)
        assertTrue(RadarLightController.isBrakeActive)

        // Workout is stopped while brake is active / holding (Finding 3):
        RadarLightController.onWorkoutStopped()
        assertFalse("isBrakeActive must be cleared on workout stop", RadarLightController.isBrakeActive)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)

        // Simulate delayed hold expiration runnable firing after workout stopped:
        val restored = RadarLightController.checkAndRestorePreBrakeMode(nowMs = 4000L)
        assertFalse("Hold expiration after workout stopped must not restore light", restored)
        assertEquals("Light must remain OFF after workout stopped", RadarLightMode.OFF, RadarLightController.currentMode.value)
    }

    @Test
    fun testRadarLightController_onWorkoutPaused_and_onWorkoutResumed() {
        val mockSensorManager = mockk<SensorManager>(relaxed = true)
        val mockLinAcc = mockk<Sensor>(relaxed = true)
        val mockGravity = mockk<Sensor>(relaxed = true)
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) } returns mockLinAcc
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) } returns mockGravity

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockSensorManager

        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true)
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)

        // Set mode to PELOTON (e.g. rider manually switched or group ride mode)
        RadarLightController.setLightMode(RadarLightMode.PELOTON)
        assertEquals(RadarLightMode.PELOTON, RadarLightController.currentMode.value)
        assertTrue("Sensor listening should be active during cycling workout", SmartBrakeLightDetector.isListening)

        RadarLightController.triggerBrakeLight(active = true, nowMs = 1000L)
        assertTrue(RadarLightController.isBrakeActive)

        // Pause workout
        RadarLightController.onWorkoutPaused()
        assertFalse("Active brake must be cleared on pause", RadarLightController.isBrakeActive)
        assertFalse("Sensor listening must be stopped on pause", SmartBrakeLightDetector.isListening)
        assertEquals("Light mode should revert from brake to pre-brake mode on pause", RadarLightMode.PELOTON, RadarLightController.currentMode.value)

        // Resume workout
        RadarLightController.onWorkoutResumed(mockContext)
        assertTrue("Sensor listening must be resumed on unpause", SmartBrakeLightDetector.isListening)
        assertEquals("PELOTON mode must be preserved on resume and not stomped to defaultMode", RadarLightMode.PELOTON, RadarLightController.currentMode.value)
    }

    @Test
    fun testRadarLightController_workoutStoppedFromPausedState_turnsOffTailLight() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true)
        RadarLightController.updateCachedPreferences(autoMode = false, defaultMode = RadarLightMode.DAY_FLASH)

        val mockContext = mockk<Context>(relaxed = true)
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)

        // Workout paused: tail light must NOT turn off (remains DAY_FLASH for road safety)
        RadarLightController.onWorkoutPaused()
        assertEquals("Pausing workout must preserve tail light mode", RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)

        // Workout stopped from paused state (e.g. TrainingApplication.stopTracking when !TrackerService.isRunning)
        RadarLightController.onWorkoutStopped(mockContext)
        assertEquals("Stopping workout from paused state must turn off tail light", RadarLightMode.OFF, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.OFF) }
    }

    @Test
    fun testRadarLightController_onWorkoutResumed_preservesNonCyclingSessionState() {
        val mockSensorManager = mockk<SensorManager>(relaxed = true)
        val mockLinAcc = mockk<Sensor>(relaxed = true)
        val mockGravity = mockk<Sensor>(relaxed = true)
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) } returns mockLinAcc
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) } returns mockGravity

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockSensorManager

        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true)

        // Non-cycling workout started (e.g. Run, Gym)
        RadarLightController.onWorkoutStarted(mockContext, isCycling = false)
        assertFalse("Non-cycling workout must NOT register IMU sensors", SmartBrakeLightDetector.isListening)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)

        // Pause workout
        RadarLightController.onWorkoutPaused()

        // Resume workout without specifying isCycling (e.g. TrainingApplication call)
        RadarLightController.onWorkoutResumed(mockContext)
        assertFalse("Non-cycling workout session must NOT activate IMU sensors on resume", SmartBrakeLightDetector.isListening)
        assertEquals("Light mode must remain OFF for non-cycling session", RadarLightMode.OFF, RadarLightController.currentMode.value)
    }

    @Test
    fun testRadarLightController_onWorkoutStarted_nonCyclingWorkout_doesNotStartListening() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true)

        // Non-cycling workout started (e.g. Run, Gym)
        RadarLightController.onWorkoutStarted(mockContext, isCycling = false)
        assertFalse("Non-cycling workout must NOT register IMU sensors", SmartBrakeLightDetector.isListening)
    }

    @Test
    fun testRadarLightController_onWorkoutStarted_withoutLightAvailable_doesNotStartListening() {
        val mockContext = mockk<Context>(relaxed = true)
        // No radar device connected (_isLightControlAvailable == false)
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        assertFalse("Workout without radar light control must NOT register IMU sensors", SmartBrakeLightDetector.isListening)
    }

    @Test
    fun testRadarLightController_registerRadarDevice_withoutLightSupport_doesNotStartListening() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockRadarOnly = mockk<BTLERadarDevice>(relaxed = true)

        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)

        // Radar without light support connects (e.g. Garmin RVR315)
        RadarLightController.registerRadarDevice(mockRadarOnly, lightSupported = false)
        assertFalse("Radar peripheral without light support must NOT register IMU sensors", SmartBrakeLightDetector.isListening)
    }

    /**
     * TST-CON-031.5: Verify setManualLightMode updates currentMode, flags manual override, and dispatches command.
     */
    @Test
    fun testRadarLightController_setManualLightMode_updatesModeAndSetsManualOverride() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        assertFalse(SmartRadarLightEngine.isManualOverridden)

        val result = RadarLightController.setManualLightMode(RadarLightMode.DAY_FLASH)

        assertTrue(result)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        assertTrue(SmartRadarLightEngine.isManualOverridden)
        verify { mockDevice.setLightMode(RadarLightMode.DAY_FLASH) }
    }

    /**
     * TST-CON-031.5: Verify setManualLightMode prunes non-StVZO modes to SOLID when StVZO hardware is active.
     */
    @Test
    fun testRadarLightController_setManualLightMode_stvzoPruning() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = true)

        val result = RadarLightController.setManualLightMode(RadarLightMode.DAY_FLASH)

        assertTrue(result)
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)
        assertTrue(SmartRadarLightEngine.isManualOverridden)
        verify { mockDevice.setLightMode(RadarLightMode.SOLID) }
    }

    /**
     * TST-CON-031.5: Verify setManualLightMode fails safe without GATT dispatch when light control is unsupported.
     */
    @Test
    fun testRadarLightController_setManualLightMode_withoutLightSupport_failsGracefully() {
        val mockRadarOnly = mockk<BTLERadarDevice>(relaxed = true)
        RadarLightController.registerRadarDevice(mockRadarOnly, lightSupported = false)

        val result = RadarLightController.setManualLightMode(RadarLightMode.SOLID)

        assertFalse(result)
        assertFalse(RadarLightController.isLightControlAvailable.value)
    }

    /**
     * TST-CON-035.5: Sequential workouts hardware auto-bypass persistence.
     * Stopping a workout must not wipe hardware auto-bypass; sequential workouts must not register IMU listeners.
     */
    @Test
    fun testRadarLightController_sequentialWorkouts_hardwareBrakeAutoBypassPersists() {
        val mockSensorManager = mockk<SensorManager>(relaxed = true)
        val mockLinAcc = mockk<Sensor>(relaxed = true)
        val mockGravity = mockk<Sensor>(relaxed = true)
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) } returns mockLinAcc
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) } returns mockGravity
        every { mockSensorManager.registerListener(any(), any<Sensor>(), any()) } returns true

        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockSensorManager

        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true

        // Register Garmin RearVue 820 with hardware brake sensor
        RadarLightController.registerRadarDevice(
            mockDevice,
            lightSupported = true,
            isStvzo = false,
            hasHardwareBrakeSensor = true
        )
        assertTrue(RadarLightController.hasHardwareBrakeSensor.value)
        assertTrue(RadarLightController.isAutoBypassed.value)
        assertTrue(SmartBrakeLightDetector.isAutoBypassed)

        // Workout 1: Start
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        assertFalse("Workout 1 must not register IMU listeners due to auto-bypass", SmartBrakeLightDetector.isListening)

        // Workout 1: Stop
        RadarLightController.onWorkoutStopped(mockContext)
        assertTrue("SmartBrakeLightDetector auto-bypass must persist after workout stop", SmartBrakeLightDetector.isAutoBypassed)
        assertTrue("RadarLightController auto-bypass must persist after workout stop", RadarLightController.isAutoBypassed.value)

        // Workout 2: Start sequential workout
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        assertTrue("Auto-bypass must still be active for sequential workout", SmartBrakeLightDetector.isAutoBypassed)
        assertFalse("Sequential workout must NOT register IMU listeners when auto-bypassed", SmartBrakeLightDetector.isListening)
        verify(exactly = 0) { mockSensorManager.registerListener(any(), any<Sensor>(), any()) }

        RadarLightController.onWorkoutStopped(mockContext)
    }

    /**
     * TST-CON-035.7: Stuck brake light on pause in RADAR_ONLY_LIGHT_OFF restored to OFF.
     * Pausing workout while braking with preBrakeMode == OFF must restore RadarLightMode.OFF.
     */
    @Test
    fun testRadarLightController_onWorkoutPaused_withLightOffPreBrake_restoresOffMode() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true

        val mockContext = mockk<Context>(relaxed = true)
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        RadarLightController.setLightMode(RadarLightMode.OFF)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)

        // Hard braking triggers brake light while light was OFF
        val triggered = RadarLightController.triggerBrakeLight(
            active = true,
            brakeMode = RadarLightMode.DAY_FLASH,
            nowMs = 1000L
        )
        assertTrue(triggered)
        assertTrue(RadarLightController.isBrakeActive)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.DAY_FLASH) }

        // Cyclist stops at intersection; workout auto-pauses
        RadarLightController.onWorkoutPaused()

        assertFalse("Brake flag must be cleared on pause", RadarLightController.isBrakeActive)
        assertFalse("IMU detector must stop listening on pause", SmartBrakeLightDetector.isListening)
        assertEquals("Pre-brake mode OFF must be restored on pause", RadarLightMode.OFF, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.OFF) }
    }

    /**
     * TST-CON-031.9a / REQ-CON-031.8: Debounced mode cycling and StVZO capability filtering.
     * Non-StVZO: AUTO -> DAY_FLASH -> SOLID -> PELOTON -> NIGHT_FLASH -> OFF -> AUTO.
     * StVZO: AUTO -> SOLID -> OFF -> AUTO.
     */
    @Test
    fun testRadarLightController_debouncedCycleNextMode_andStvzo() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        assertTrue("Initial state must be AUTO", RadarLightController.isAutoMode.value)

        // Non-StVZO Cycle
        var selection = RadarLightController.cycleNextMode()
        assertFalse(selection.isAuto)
        assertEquals(RadarLightMode.DAY_FLASH, selection.mode)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        assertFalse(RadarLightController.isAutoMode.value)

        selection = RadarLightController.cycleNextMode()
        assertFalse(selection.isAuto)
        assertEquals(RadarLightMode.SOLID, selection.mode)

        selection = RadarLightController.cycleNextMode()
        assertFalse(selection.isAuto)
        assertEquals(RadarLightMode.PELOTON, selection.mode)

        selection = RadarLightController.cycleNextMode()
        assertFalse(selection.isAuto)
        assertEquals(RadarLightMode.NIGHT_FLASH, selection.mode)

        selection = RadarLightController.cycleNextMode()
        assertFalse(selection.isAuto)
        assertEquals(RadarLightMode.OFF, selection.mode)

        selection = RadarLightController.cycleNextMode()
        assertTrue(selection.isAuto)
        assertTrue(RadarLightController.isAutoMode.value)

        // StVZO Cycle
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = true)
        RadarLightController.setAutoMode()
        assertTrue(RadarLightController.isAutoMode.value)

        selection = RadarLightController.cycleNextMode()
        assertFalse(selection.isAuto)
        assertEquals(RadarLightMode.SOLID, selection.mode)

        selection = RadarLightController.cycleNextMode()
        assertFalse(selection.isAuto)
        assertEquals(RadarLightMode.OFF, selection.mode)

        selection = RadarLightController.cycleNextMode()
        assertTrue(selection.isAuto)
        assertTrue(RadarLightController.isAutoMode.value)
    }

    /**
     * TST-CON-031.4a / REQ-CON-031.4: Workout lifecycle maintains user mode across rides.
     * Stop commands physical OFF without wiping persisted selection; next start restores selection.
     */
    @Test
    fun testRadarLightController_workoutStartStop_persistsAcrossRides() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        val mockContext = mockk<Context>(relaxed = true)

        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)
        RadarLightController.setManualLightMode(RadarLightMode.SOLID)

        assertFalse(RadarLightController.isAutoMode.value)
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)

        // Ride 1 starts
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.SOLID) }

        // Ride 1 stops: hardware dispatched OFF, but internal selection preserved
        RadarLightController.onWorkoutStopped(mockContext)
        assertEquals("Physical mode should reflect OFF on stop", RadarLightMode.OFF, RadarLightController.currentMode.value)
        verify { mockDevice.setLightMode(RadarLightMode.OFF) }

        // Ride 2 starts: resumes persisted selection
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        assertEquals("Ride 2 should restore SOLID preference", RadarLightMode.SOLID, RadarLightController.currentMode.value)
        verify(atLeast = 2) { mockDevice.setLightMode(RadarLightMode.SOLID) }
    }

    /**
     * Finding 2 & Finding 3: Selection outside active workout (e.g. EditDeviceDialog)
     * updates in-memory cache and is NOT stomped on workout start.
     */
    @Test
    fun testRadarLightController_outsideWorkoutSelection_persistsAcrossLifecycleAndWorkoutStart() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        val mockContext = mockk<Context>(relaxed = true)

        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        // User selects SOLID in EditDeviceDialog outside active workout
        RadarLightController.setManualLightMode(RadarLightMode.SOLID)
        assertFalse(RadarLightController.isAutoMode.value)
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)

        // Workout starts: must retain SOLID, not stomp with default AUTO
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        assertFalse(RadarLightController.isAutoMode.value)
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)

        // Workout stops
        RadarLightController.onWorkoutStopped(mockContext)
        assertEquals(RadarLightMode.OFF, RadarLightController.currentMode.value)

        // Outside workout, user switches back to AUTO
        RadarLightController.setAutoMode()
        assertTrue(RadarLightController.isAutoMode.value)

        // Workout starts: must retain AUTO
        RadarLightController.onWorkoutStarted(mockContext, isCycling = true)
        assertTrue(RadarLightController.isAutoMode.value)
    }

    /**
     * TST-CON-031 / TST-CON-031.2 / TST-CON-031.5 / ATT-7737:
     * Reconciling failed hardware GATT write reverts matching optimistic mode to confirmed fallback
     * while ignoring stale failures once user has switched to a newer mode.
     */
    @Test
    fun testRadarLightController_onHardwareLightModeWriteFailed_reconcilesCleanly() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)

        assertTrue(RadarLightController.setManualLightMode(RadarLightMode.NIGHT_FLASH))
        assertEquals(RadarLightMode.NIGHT_FLASH, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.NIGHT_FLASH, RadarStateRepository.getState().lightMode)

        // Write failure while NIGHT_FLASH is still active -> reverts cleanly to SOLID
        RadarLightController.onHardwareLightModeWriteFailed(RadarLightMode.NIGHT_FLASH, RadarLightMode.SOLID)
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.SOLID, RadarStateRepository.getState().lightMode)

        // Stale failure for NIGHT_FLASH must not stomp newer DAY_FLASH selection
        assertTrue(RadarLightController.setManualLightMode(RadarLightMode.DAY_FLASH))
        RadarLightController.onHardwareLightModeWriteFailed(RadarLightMode.NIGHT_FLASH, RadarLightMode.OFF)
        assertEquals(RadarLightMode.DAY_FLASH, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.DAY_FLASH, RadarStateRepository.getState().lightMode)
    }

    /**
     * TST-CON-031.4 / TST-CON-031.5 / ATT-7737:
     * Under StVZO pruning, setManualLightMode synchronizes RadarStateRepository to pruned SOLID mode
     * consistently with EditDeviceDialog's live currentMode selector binding.
     */
    @Test
    fun testRadarLightController_setManualLightMode_stvzoSynchronizesRepositoryState() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = true)

        assertTrue(RadarLightController.setManualLightMode(RadarLightMode.NIGHT_FLASH))
        assertEquals(RadarLightMode.SOLID, RadarLightController.currentMode.value)
        assertEquals(RadarLightMode.SOLID, RadarStateRepository.getState().lightMode)
        assertFalse(RadarLightController.isAutoMode.value)
    }

    /**
     * TST-CON-046.5 / REQ-CON-046.2, REQ-CON-031.8 (ATT-8455):
     * Manual override (setManualLightMode / cycleNextMode) keeps isAutoMode == false and
     * SmartRadarLightEngine suspended across incoming simulator/telemetry frames until setAutoMode().
     */
    @Test
    fun testRadarLightController_manualOverrideSurvivesIncomingFramesUntilSetAutoMode() {
        val mockDevice = mockk<BTLERadarDevice>(relaxed = true)
        every { mockDevice.setLightMode(any()) } returns true
        RadarLightController.registerRadarDevice(mockDevice, lightSupported = true, isStvzo = false)
        SmartRadarLightEngine.isAutomationEnabled = true

        // 1. User selects manual mode via QuickSwitch / setManualLightMode
        assertTrue(RadarLightController.setManualLightMode(RadarLightMode.SOLID))
        assertFalse(RadarLightController.isAutoMode.value)
        assertFalse(RadarStateRepository.getState().isAutoMode)
        assertTrue(SmartRadarLightEngine.isManualOverridden)

        // Incoming simulator tick must NOT revert repository isAutoMode to true
        RadarTrafficSimulator.tick(RadarScenario.SINGLE_CAR, 500L)
        assertFalse(RadarLightController.isAutoMode.value)
        assertFalse(RadarStateRepository.getState().isAutoMode)
        assertTrue(SmartRadarLightEngine.isManualOverridden)

        // 2. User short-taps cycleNextMode() from SOLID -> PELOTON
        val selection = RadarLightController.cycleNextMode()
        assertFalse(selection.isAuto)
        assertEquals(RadarLightMode.PELOTON, selection.mode)
        assertFalse(RadarStateRepository.getState().isAutoMode)

        // Another telemetry tick still preserves isAutoMode == false
        RadarTrafficSimulator.tick(RadarScenario.SINGLE_CAR, 1000L)
        assertFalse(RadarStateRepository.getState().isAutoMode)

        // 3. Explicit setAutoMode() restores isAutoMode == true and resumes SmartRadarLightEngine
        RadarLightController.setAutoMode()
        assertTrue(RadarLightController.isAutoMode.value)
        assertTrue(RadarStateRepository.getState().isAutoMode)
        assertFalse(SmartRadarLightEngine.isManualOverridden)
    }
}
