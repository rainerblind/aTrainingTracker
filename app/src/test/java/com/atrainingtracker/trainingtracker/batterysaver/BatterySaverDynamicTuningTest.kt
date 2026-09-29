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
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying dynamic parameter tuning in [BatterySaverStateMachine] and [BatterySaverController] (TST-SET-062).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatterySaverDynamicTuningTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private var lastAppliedBrightness: Float = -1f
    private val testBaselineBrightness = 0.80f

    @Before
    fun setUp() {
        lastAppliedBrightness = -1f
    }

    @Test
    fun stateMachine_respectsCustomSlopeThresholds() {
        val defaultSm = BatterySaverStateMachine()
        val snapshotSlope2_5 = TelemetrySnapshot(slopePercent = 2.5f, hrZone = 1, powerZone = 1, isCycling = true)
        
        // Default: flat=2.0%, steep=5.0% -> 2.5% is MEDIUM_DIM
        assertEquals(DimmingLevel.MEDIUM_DIM, defaultSm.evaluateRawLevel(snapshotSlope2_5))

        // Custom config: flat=3.0%, steep=7.0%
        val customConfig = BatterySaverTuningConfig(
            slopeFlatThreshold = 3.0f,
            slopeSteepThreshold = 7.0f
        )
        val customSm = BatterySaverStateMachine(tuningConfig = customConfig)
        
        // With flat=3.0%, 2.5% is <= flat threshold, thus FULL_DIM
        assertEquals(DimmingLevel.FULL_DIM, customSm.evaluateRawLevel(snapshotSlope2_5))

        // Snapshot with slope 6.0%:
        val snapshotSlope6_0 = TelemetrySnapshot(slopePercent = 6.0f, hrZone = 1, powerZone = 1, isCycling = true)
        // Default steep=5.0% -> 6.0% is NO_DIM
        assertEquals(DimmingLevel.NO_DIM, defaultSm.evaluateRawLevel(snapshotSlope6_0))
        // Custom steep=7.0% -> 6.0% is MEDIUM_DIM
        assertEquals(DimmingLevel.MEDIUM_DIM, customSm.evaluateRawLevel(snapshotSlope6_0))
    }

    @Test
    fun stateMachine_respectsCustomDimmingFactors() {
        val customConfig = BatterySaverTuningConfig(
            fullDimFactor = 0.10f,
            mediumDimFactor = 0.35f
        )
        val sm = BatterySaverStateMachine(tuningConfig = customConfig)

        assertEquals(0.10f, sm.getBrightnessForLevel(DimmingLevel.FULL_DIM), 0.001f)
        assertEquals(0.35f, sm.getBrightnessForLevel(DimmingLevel.MEDIUM_DIM), 0.001f)
        assertEquals(1.00f, sm.getBrightnessForLevel(DimmingLevel.NO_DIM), 0.001f)
    }

    @Test
    fun controller_appliesCustomDimmingFactorOnUpdate() = testScope.runTest {
        val sm = BatterySaverStateMachine()
        val controller = BatterySaverController(
            activity = null,
            scope = testScope,
            stateMachine = sm,
            systemBrightnessProvider = { testBaselineBrightness },
            brightnessApplier = { brightness -> lastAppliedBrightness = brightness }
        )

        controller.setEnabled(true)
        // Initially in wakeup state (1.0 * baseline)
        assertEquals(testBaselineBrightness, lastAppliedBrightness, 0.001f)

        // Advance past initial wakeup (15s)
        testDispatcher.scheduler.advanceTimeBy(15_001L)
        // With default flat slope & low zones, drops to FULL_DIM (0.25 * 0.80 = 0.20)
        assertEquals(0.20f, lastAppliedBrightness, 0.001f)

        // Now dynamically update tuning config with fullDimFactor = 0.10f
        val newConfig = BatterySaverTuningConfig(fullDimFactor = 0.10f)
        controller.updateTuningConfig(newConfig)

        // Should immediately re-apply new factor: 0.10 * 0.80 = 0.08
        assertEquals(0.08f, lastAppliedBrightness, 0.001f)
    }

    @Test
    fun controller_respectsDynamicDownwardHysteresis() = testScope.runTest {
        val sm = BatterySaverStateMachine()
        val controller = BatterySaverController(
            activity = null,
            scope = testScope,
            stateMachine = sm,
            systemBrightnessProvider = { testBaselineBrightness },
            brightnessApplier = { brightness -> lastAppliedBrightness = brightness }
        )

        controller.setEnabled(true)
        testDispatcher.scheduler.advanceTimeBy(15_001L) // Finish wakeup, now FULL_DIM

        // Move to NO_DIM immediately via steep slope
        controller.updateTelemetry(TelemetrySnapshot(slopePercent = 6.0f, hrZone = 1, isCycling = true))
        assertEquals(testBaselineBrightness, lastAppliedBrightness, 0.001f)

        // Configure custom downward hysteresis to 6000ms
        val customConfig = BatterySaverTuningConfig(downwardHysteresisMs = 6_000L)
        controller.updateTuningConfig(customConfig)

        // Now slope drops back to 0.0% (downward transition to FULL_DIM)
        controller.updateTelemetry(TelemetrySnapshot(slopePercent = 0.0f, hrZone = 1, isCycling = true))

        // At 3000ms (old hysteresis), brightness should NOT have changed yet because new hysteresis is 6000ms
        testDispatcher.scheduler.advanceTimeBy(3_001L)
        assertEquals(testBaselineBrightness, lastAppliedBrightness, 0.001f)

        // Advance remaining 3000ms (total 6001ms)
        testDispatcher.scheduler.advanceTimeBy(3_000L)
        // Now it should have dropped to FULL_DIM (0.25 * 0.80 = 0.20f)
        assertEquals(0.20f, lastAppliedBrightness, 0.001f)
    }

    @Test
    fun controller_respectsDynamicWakeupDuration() = testScope.runTest {
        val sm = BatterySaverStateMachine()
        val controller = BatterySaverController(
            activity = null,
            scope = testScope,
            stateMachine = sm,
            systemBrightnessProvider = { testBaselineBrightness },
            brightnessApplier = { brightness -> lastAppliedBrightness = brightness }
        )

        // Set custom wakeup duration to 8000ms
        controller.updateTuningConfig(BatterySaverTuningConfig(wakeupDurationMs = 8_000L))
        controller.setEnabled(true)

        // Wakeup active (baseline brightness)
        assertEquals(testBaselineBrightness, lastAppliedBrightness, 0.001f)

        // At 7900ms, still in wakeup
        testDispatcher.scheduler.advanceTimeBy(7_900L)
        assertEquals(testBaselineBrightness, lastAppliedBrightness, 0.001f)

        // At 8001ms, wakeup ends and dims to FULL_DIM
        testDispatcher.scheduler.advanceTimeBy(101L)
        assertEquals(0.20f, lastAppliedBrightness, 0.001f)
    }
}
