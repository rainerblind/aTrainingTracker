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

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BatterySaverStateMachineTest {

    private lateinit var stateMachine: BatterySaverStateMachine

    @Before
    fun setUp() {
        stateMachine = BatterySaverStateMachine(hysteresisMs = 3000L)
    }

    // =========================================================================
    // Test Suite 1: Pure State Machine Unit Verification (TST-UI-126 Suite 1)
    // =========================================================================

    @Test
    fun fullDimmingMatrix_returnsFullDim() {
        // Slope -1.5%, HR Zone 1, Power Zone 1 -> FULL_DIM (0.15f)
        val snapshot1 = TelemetrySnapshot(slopePercent = -1.5f, hrZone = 1, powerZone = 1, isCycling = true)
        assertEquals(DimmingLevel.FULL_DIM, stateMachine.evaluateRawLevel(snapshot1))
        assertEquals(0.15f, DimmingLevel.FULL_DIM.brightness, 0.001f)

        // Slope 0.0%, HR Zone 2, Power Zone 2 -> FULL_DIM (0.15f)
        val snapshot2 = TelemetrySnapshot(slopePercent = 0.0f, hrZone = 2, powerZone = 2, isCycling = true)
        assertEquals(DimmingLevel.FULL_DIM, stateMachine.evaluateRawLevel(snapshot2))

        // Slope 1.9%, HR Zone 2, Power Zone 1 -> FULL_DIM (0.15f)
        val snapshot3 = TelemetrySnapshot(slopePercent = 1.9f, hrZone = 2, powerZone = 1, isCycling = true)
        assertEquals(DimmingLevel.FULL_DIM, stateMachine.evaluateRawLevel(snapshot3))
    }

    @Test
    fun mediumDimmingMatrix_returnsMediumDim() {
        // Slope 2.0%, HR Zone 1, Power Zone 1 -> MEDIUM_DIM (0.50f) (due to slope)
        val snapshot1 = TelemetrySnapshot(slopePercent = 2.0f, hrZone = 1, powerZone = 1, isCycling = true)
        assertEquals(DimmingLevel.MEDIUM_DIM, stateMachine.evaluateRawLevel(snapshot1))
        assertEquals(0.50f, DimmingLevel.MEDIUM_DIM.brightness, 0.001f)

        // Slope 0.0%, HR Zone 3, Power Zone 1 -> MEDIUM_DIM (0.50f) (due to HR)
        val snapshot2 = TelemetrySnapshot(slopePercent = 0.0f, hrZone = 3, powerZone = 1, isCycling = true)
        assertEquals(DimmingLevel.MEDIUM_DIM, stateMachine.evaluateRawLevel(snapshot2))

        // Slope 0.0%, HR Zone 1, Power Zone 3 -> MEDIUM_DIM (0.50f) (due to Power)
        val snapshot3 = TelemetrySnapshot(slopePercent = 0.0f, hrZone = 1, powerZone = 3, isCycling = true)
        assertEquals(DimmingLevel.MEDIUM_DIM, stateMachine.evaluateRawLevel(snapshot3))

        // Slope 4.9%, HR Zone 3, Power Zone 3 -> MEDIUM_DIM (0.50f)
        val snapshot4 = TelemetrySnapshot(slopePercent = 4.9f, hrZone = 3, powerZone = 3, isCycling = true)
        assertEquals(DimmingLevel.MEDIUM_DIM, stateMachine.evaluateRawLevel(snapshot4))
    }

    @Test
    fun noDimmingMatrix_returnsNoDim() {
        // Slope 5.1%, HR Zone 1, Power Zone 1 -> NO_DIM (1.0f) (steep grade)
        val snapshot1 = TelemetrySnapshot(slopePercent = 5.1f, hrZone = 1, powerZone = 1, isCycling = true)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.evaluateRawLevel(snapshot1))
        assertEquals(1.0f, DimmingLevel.NO_DIM.brightness, 0.001f)

        // Slope 0.0%, HR Zone 4, Power Zone 1 -> NO_DIM (1.0f) (threshold HR)
        val snapshot2 = TelemetrySnapshot(slopePercent = 0.0f, hrZone = 4, powerZone = 1, isCycling = true)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.evaluateRawLevel(snapshot2))

        // Slope 0.0%, HR Zone 1, Power Zone 5 -> NO_DIM (1.0f) (anaerobic Power)
        val snapshot3 = TelemetrySnapshot(slopePercent = 0.0f, hrZone = 1, powerZone = 5, isCycling = true)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.evaluateRawLevel(snapshot3))

        // Slope 10.0%, HR Zone 5, Power Zone 6 -> NO_DIM (1.0f)
        val snapshot4 = TelemetrySnapshot(slopePercent = 10.0f, hrZone = 5, powerZone = 6, isCycling = true)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.evaluateRawLevel(snapshot4))
    }

    // =========================================================================
    // Test Suite 2: Sensor Fallback & Edge Case Matrix (TST-UI-126 Suite 2)
    // =========================================================================

    @Test
    fun runningActivityWithoutPower_evaluatesSlopeAndHeartRate() {
        // Slope < 2%, HR Zone 1 -> FULL_DIM
        val snapshot1 = TelemetrySnapshot(slopePercent = 1.0f, hrZone = 1, powerZone = null, isCycling = false)
        assertEquals(DimmingLevel.FULL_DIM, stateMachine.evaluateRawLevel(snapshot1))

        // Slope 3%, HR Zone 1 -> MEDIUM_DIM
        val snapshot2 = TelemetrySnapshot(slopePercent = 3.0f, hrZone = 1, powerZone = null, isCycling = false)
        assertEquals(DimmingLevel.MEDIUM_DIM, stateMachine.evaluateRawLevel(snapshot2))

        // Slope 0%, HR Zone 4 -> NO_DIM
        val snapshot3 = TelemetrySnapshot(slopePercent = 0.0f, hrZone = 4, powerZone = null, isCycling = false)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.evaluateRawLevel(snapshot3))
    }

    @Test
    fun missingHeartRateMonitor_evaluatesSlopeAndPower() {
        // Slope < 2%, Power Zone 2 -> FULL_DIM
        val snapshot1 = TelemetrySnapshot(slopePercent = 1.0f, hrZone = null, powerZone = 2, isCycling = true)
        assertEquals(DimmingLevel.FULL_DIM, stateMachine.evaluateRawLevel(snapshot1))

        // Slope < 2%, Power Zone 3 -> MEDIUM_DIM
        val snapshot2 = TelemetrySnapshot(slopePercent = 1.0f, hrZone = null, powerZone = 3, isCycling = true)
        assertEquals(DimmingLevel.MEDIUM_DIM, stateMachine.evaluateRawLevel(snapshot2))

        // Slope < 2%, Power Zone 4 -> NO_DIM
        val snapshot3 = TelemetrySnapshot(slopePercent = 1.0f, hrZone = null, powerZone = 4, isCycling = true)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.evaluateRawLevel(snapshot3))
    }

    @Test
    fun standaloneGpsDevice_pureSlopeEvaluation() {
        // Slope 1.0% -> FULL_DIM
        val snapshot1 = TelemetrySnapshot(slopePercent = 1.0f, hrZone = null, powerZone = null, isCycling = true)
        assertEquals(DimmingLevel.FULL_DIM, stateMachine.evaluateRawLevel(snapshot1))

        // Slope 3.0% -> MEDIUM_DIM
        val snapshot2 = TelemetrySnapshot(slopePercent = 3.0f, hrZone = null, powerZone = null, isCycling = true)
        assertEquals(DimmingLevel.MEDIUM_DIM, stateMachine.evaluateRawLevel(snapshot2))

        // Slope 6.0% -> NO_DIM
        val snapshot3 = TelemetrySnapshot(slopePercent = 6.0f, hrZone = null, powerZone = null, isCycling = true)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.evaluateRawLevel(snapshot3))
    }

    // =========================================================================
    // Test Suite 3: Temporal Hysteresis & Damping
    // =========================================================================

    @Test
    fun upwardTransition_isImmediateWithoutDelay() {
        stateMachine.reset(initialLevel = DimmingLevel.FULL_DIM)
        assertEquals(DimmingLevel.FULL_DIM, stateMachine.currentLevel)

        // Athlete hits a 7% climb -> immediate NO_DIM
        val steepClimb = TelemetrySnapshot(slopePercent = 7.0f, hrZone = 2, powerZone = 2, isCycling = true)
        val level = stateMachine.update(steepClimb, currentTimeMs = 1000L)
        assertEquals(DimmingLevel.NO_DIM, level)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.currentLevel)
    }

    @Test
    fun downwardTransition_requiresDampingHysteresis() {
        stateMachine.reset(initialLevel = DimmingLevel.NO_DIM)
        assertEquals(DimmingLevel.NO_DIM, stateMachine.currentLevel)

        // Athlete starts cruising on flat road at t=1000ms
        val flatCruising = TelemetrySnapshot(slopePercent = 0.5f, hrZone = 1, powerZone = 1, isCycling = true)

        // At t=1000ms: still NO_DIM
        val level1 = stateMachine.update(flatCruising, currentTimeMs = 1000L)
        assertEquals(DimmingLevel.NO_DIM, level1)

        // At t=2500ms (1.5s elapsed): still NO_DIM
        val level2 = stateMachine.update(flatCruising, currentTimeMs = 2500L)
        assertEquals(DimmingLevel.NO_DIM, level2)

        // At t=4000ms (3.0s elapsed): transitions to FULL_DIM
        val level3 = stateMachine.update(flatCruising, currentTimeMs = 4000L)
        assertEquals(DimmingLevel.FULL_DIM, level3)
    }
}
