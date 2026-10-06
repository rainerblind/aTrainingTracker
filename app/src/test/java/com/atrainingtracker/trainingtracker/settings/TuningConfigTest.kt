/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TuningConfig] and [TuningPreferencesDefaults] (TST-SET-062).
 * Verifies default parameter values, clamping boundaries, and safety invariants.
 */
class TuningConfigTest {

    @Test
    fun defaultValues_conformToEstablishedBehavior() {
        val config = TuningConfig()
        assertEquals(0.25f, config.fullDimFactor, 0.001f)
        assertEquals(0.50f, config.mediumDimFactor, 0.001f)
        assertEquals(2.0f, config.slopeFlatThreshold, 0.001f)
        assertEquals(5.0f, config.slopeSteepThreshold, 0.001f)
        assertEquals(15, config.wakeupDurationSec)
        assertEquals(3, config.downwardDelaySec)
        assertEquals(50.0f, config.gpsAccuracyThresholdMeters, 0.001f)
        assertEquals(50.0f, TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M, 0.001f)
        assertEquals(21, config.altitudeFilterWindowSec)
        assertEquals(0.5f, config.slopeMinSpeedMps, 0.001f)
        assertEquals(1.0f, config.routeSelectionRadiusKm, 0.001f)
        assertEquals(1.0f, TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM, 0.001f)
    }

    @Test
    fun defaultsConstants_matchExpectedBounds() {
        assertEquals(0.05f, TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR, 0.001f)
        assertEquals(0.50f, TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR, 0.001f)
        assertEquals(0.20f, TuningPreferencesDefaults.MIN_MEDIUM_DIM_FACTOR, 0.001f)
        assertEquals(0.90f, TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR, 0.001f)

        assertEquals(0.5f, TuningPreferencesDefaults.MIN_ROUTE_SELECTION_RADIUS_KM, 0.001f)
        assertEquals(10.0f, TuningPreferencesDefaults.MAX_ROUTE_SELECTION_RADIUS_KM, 0.001f)

        assertEquals(0.0f, TuningPreferencesDefaults.MIN_SLOPE_FLAT, 0.001f)
        assertEquals(5.0f, TuningPreferencesDefaults.MAX_SLOPE_FLAT, 0.001f)
        assertEquals(2.0f, TuningPreferencesDefaults.MIN_SLOPE_STEEP, 0.001f)
        assertEquals(15.0f, TuningPreferencesDefaults.MAX_SLOPE_STEEP, 0.001f)

        assertEquals(5, TuningPreferencesDefaults.MIN_WAKEUP_SEC)
        assertEquals(60, TuningPreferencesDefaults.MAX_WAKEUP_SEC)
        assertEquals(1, TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC)
        assertEquals(15, TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC)

        assertEquals(10.0f, TuningPreferencesDefaults.MIN_GPS_ACCURACY_M, 0.001f)
        assertEquals(500.0f, TuningPreferencesDefaults.MAX_GPS_ACCURACY_M, 0.001f)
        assertEquals(5, TuningPreferencesDefaults.MIN_ALTITUDE_WINDOW_SEC)
        assertEquals(60, TuningPreferencesDefaults.MAX_ALTITUDE_WINDOW_SEC)
        assertEquals(0.2f, TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS, 0.001f)
        assertEquals(2.0f, TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS, 0.001f)
    }

    @Test
    fun relationalInvariants_holdForDefaults() {
        val config = TuningConfig()
        assertTrue("mediumDimFactor must be >= fullDimFactor", config.mediumDimFactor >= config.fullDimFactor)
        assertTrue("slopeSteepThreshold must be >= slopeFlatThreshold", config.slopeSteepThreshold >= config.slopeFlatThreshold)
        assertTrue("fullDimFactor must be >= 0.05 safety floor", config.fullDimFactor >= 0.05f)
        assertTrue("slopeMinSpeedMps must be >= 0.2 m/s floor", config.slopeMinSpeedMps >= 0.2f)
    }

    @Test
    fun clampingSimulation_enforcesBoundsAndCrossRelations() {
        // Test lower out-of-bounds
        val rawFullDim = 0.01f
        val clampedFullDim = rawFullDim.coerceIn(
            TuningPreferencesDefaults.MIN_FULL_DIM_FACTOR,
            TuningPreferencesDefaults.MAX_FULL_DIM_FACTOR
        )
        assertEquals(0.05f, clampedFullDim, 0.001f)

        // Test medium dim clamped above full dim
        val rawMediumDim = 0.03f
        val clampedMediumDim = rawMediumDim.coerceIn(
            clampedFullDim,
            TuningPreferencesDefaults.MAX_MEDIUM_DIM_FACTOR
        )
        assertEquals(0.05f, clampedMediumDim, 0.001f)

        // Test upper out-of-bounds
        val rawWakeup = 120
        val clampedWakeup = rawWakeup.coerceIn(
            TuningPreferencesDefaults.MIN_WAKEUP_SEC,
            TuningPreferencesDefaults.MAX_WAKEUP_SEC
        )
        assertEquals(60, clampedWakeup)

        val rawDownward = 0
        val clampedDownward = rawDownward.coerceIn(
            TuningPreferencesDefaults.MIN_DOWNWARD_DELAY_SEC,
            TuningPreferencesDefaults.MAX_DOWNWARD_DELAY_SEC
        )
        assertEquals(1, clampedDownward)

        val rawSlopeFlat = 6.0f
        val clampedSlopeFlat = rawSlopeFlat.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_FLAT,
            TuningPreferencesDefaults.MAX_SLOPE_FLAT
        )
        assertEquals(5.0f, clampedSlopeFlat, 0.001f)

        val rawSlopeSteep = 3.0f // lower than flat 5.0f
        val clampedSlopeSteep = rawSlopeSteep.coerceIn(
            clampedSlopeFlat,
            TuningPreferencesDefaults.MAX_SLOPE_STEEP
        )
        assertEquals(5.0f, clampedSlopeSteep, 0.001f)

        val rawSlopeSpeed = 0.05f
        val clampedSlopeSpeed = rawSlopeSpeed.coerceIn(
            TuningPreferencesDefaults.MIN_SLOPE_SPEED_MPS,
            TuningPreferencesDefaults.MAX_SLOPE_SPEED_MPS
        )
        assertEquals(0.2f, clampedSlopeSpeed, 0.001f)
    }
}
