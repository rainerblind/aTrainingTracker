/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.batterysaver

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatterySaverControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private var lastAppliedBrightness: Float = -1f
    private lateinit var controller: BatterySaverController

    private val testBaselineBrightness = 0.80f

    @Before
    fun setUp() {
        lastAppliedBrightness = -1f
        controller = BatterySaverController(
            activity = null,
            scope = testScope,
            stateMachine = BatterySaverStateMachine(hysteresisMs = 3000L),
            systemBrightnessProvider = { testBaselineBrightness },
            brightnessApplier = { brightness ->
                lastAppliedBrightness = brightness
            }
        )
    }

    @Test
    fun setEnabled_togglesStateAndAppliesBrightness() {
        assertFalse(controller.isEnabled)

        controller.setEnabled(true)
        assertTrue(controller.isEnabled)
        assertTrue(controller.isWakeupActive)
        assertEquals(testBaselineBrightness, lastAppliedBrightness, 0.001f)

        controller.setEnabled(false)
        assertFalse(controller.isEnabled)
        assertFalse(controller.isWakeupActive)
        assertEquals(1.0f, lastAppliedBrightness, 0.001f)
    }

    @Test
    fun wakeupEvent_setsBaselineBrightnessAndRestoresScaledDimmingAfter15Seconds() = testScope.runTest {
        controller.setEnabled(true)

        // Simulate steady telemetry that would result in FULL_DIM (25% of baseline)
        controller.stateMachine.reset(DimmingLevel.FULL_DIM)

        // Wakeup event occurs -> 100% of baseline
        controller.onWakeupEvent()
        assertTrue(controller.isWakeupActive)
        assertEquals(testBaselineBrightness, lastAppliedBrightness, 0.001f)

        // Advance 10 seconds -> still active
        testDispatcher.scheduler.advanceTimeBy(10_000L)
        assertTrue(controller.isWakeupActive)
        assertEquals(testBaselineBrightness, lastAppliedBrightness, 0.001f)

        // Overlapping wakeup event resets the timer
        controller.onWakeupEvent()
        assertTrue(controller.isWakeupActive)

        // Advance 10 more seconds (total 20s from first, 10s from second) -> still active
        testDispatcher.scheduler.advanceTimeBy(10_000L)
        assertTrue(controller.isWakeupActive)

        // Advance 5.1 seconds -> second timer expires (15.1s from second event)
        testDispatcher.scheduler.advanceTimeBy(5_100L)
        assertFalse(controller.isWakeupActive)
        val expectedDimmed = testBaselineBrightness * DimmingLevel.FULL_DIM.factor
        assertEquals(expectedDimmed, lastAppliedBrightness, 0.001f)
    }

    @Test
    fun safetyFloor_isStrictlyEnforced() {
        controller.setMode(DisplayBrightnessMode.AUTO)
        // Even if an absurdly low brightness level is given, safety floor (>= 0.05f) is enforced
        controller.applyBrightness(0.01f)
        assertEquals(DimmingLevel.SAFETY_FLOOR, controller.currentAppliedBrightness, 0.001f)
        assertEquals(0.05f, DimmingLevel.SAFETY_FLOOR, 0.001f)
    }

    @Test
    fun release_resetsBrightnessAndCancelsTimers() = testScope.runTest {
        controller.setEnabled(true)
        controller.onWakeupEvent()
        assertTrue(controller.isWakeupActive)

        controller.release()
        assertEquals(1.0f, lastAppliedBrightness, 0.001f)
    }

    @Test
    fun setMode_customMode_appliesConstantBrightnessAndIgnoresEvents() = testScope.runTest {
        controller.setMode(DisplayBrightnessMode.CUSTOM, 0.45f)
        assertEquals(DisplayBrightnessMode.CUSTOM, controller.brightnessMode)
        assertEquals(0.45f, controller.customBrightness, 0.001f)
        assertEquals(0.45f, lastAppliedBrightness, 0.001f)

        // Wakeup events are ignored in CUSTOM mode
        controller.onWakeupEvent()
        assertFalse(controller.isWakeupActive)
        assertEquals(0.45f, lastAppliedBrightness, 0.001f)

        // Telemetry changes are ignored in CUSTOM mode
        controller.updateTelemetry(TelemetrySnapshot(slopePercent = 12.0f, hrZone = 5))
        assertEquals(0.45f, lastAppliedBrightness, 0.001f)
    }

    @Test
    fun setMode_customMode_clampsToSafetyFloor() {
        controller.setMode(DisplayBrightnessMode.CUSTOM, 0.01f)
        assertEquals(DimmingLevel.SAFETY_FLOOR, controller.customBrightness, 0.001f)
        assertEquals(DimmingLevel.SAFETY_FLOOR, lastAppliedBrightness, 0.001f)
    }

    @Test
    fun setMode_systemMode_disablesDimmingAndIgnoresEvents() {
        controller.setMode(DisplayBrightnessMode.SYSTEM)
        assertEquals(DisplayBrightnessMode.SYSTEM, controller.brightnessMode)
        assertEquals(1.0f, lastAppliedBrightness, 0.001f)

        controller.onWakeupEvent()
        assertFalse(controller.isWakeupActive)
        assertEquals(1.0f, lastAppliedBrightness, 0.001f)
    }

    @Test
    fun relativeSystemBrightnessScaling_scalesAgainstSystemBaseline() {
        var mockedSystemBrightness = 0.40f
        val customController = BatterySaverController(
            activity = null,
            scope = testScope,
            systemBrightnessProvider = { mockedSystemBrightness },
            brightnessApplier = { brightness -> lastAppliedBrightness = brightness }
        )
        customController.setMode(DisplayBrightnessMode.AUTO)

        // Wakeup event -> 100% of system baseline (0.40f)
        customController.onWakeupEvent()
        assertEquals(0.40f, lastAppliedBrightness, 0.001f)

        // Medium dimming (50% of 0.40f = 0.20f)
        customController.applyBrightness(DimmingLevel.MEDIUM_DIM.factor)
        assertEquals(0.20f, lastAppliedBrightness, 0.001f)

        // Full dimming (25% of 0.40f = 0.10f)
        customController.applyBrightness(DimmingLevel.FULL_DIM.factor)
        assertEquals(0.10f, lastAppliedBrightness, 0.001f)

        // If system brightness is very dim (e.g. 0.10f), full dimming enforces safety floor (0.05f)
        mockedSystemBrightness = 0.10f
        customController.applyBrightness(DimmingLevel.FULL_DIM.factor)
        assertEquals(DimmingLevel.SAFETY_FLOOR, lastAppliedBrightness, 0.001f)
    }
}
