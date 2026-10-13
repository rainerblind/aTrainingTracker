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
 * Unit tests for elevation profile smoothing sigma preference model, default constants, bounds,
 * and key registration (TST-UI-257.3 / REQ-UI-297 / ATT-2746).
 */
class TuningPreferencesDataStoreElevationSmoothingTest {

    @Test
    fun defaultConstants_matchExpectedSpecification() {
        assertEquals(21.0f, TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS, 0.001f)
        assertEquals(10.0f, TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS, 0.001f)
        assertEquals(50.0f, TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS, 0.001f)
        assertEquals(1.0f, TuningPreferencesDefaults.STEP_ELEVATION_SMOOTHING_SIGMA_METERS, 0.001f)
    }

    @Test
    fun tuningConfig_defaultsTo21Meters() {
        val config = TuningConfig()
        assertEquals(21.0f, config.elevationSmoothingSigmaMeters, 0.001f)
    }

    @Test
    fun tuningConfig_customSmoothingSigma_retained() {
        val config = TuningConfig(elevationSmoothingSigmaMeters = 35.0f)
        assertEquals(35.0f, config.elevationSmoothingSigmaMeters, 0.001f)
    }

    @Test
    fun tuningConfig_clampingBoundsEnforced() {
        val belowMin = 5.0f.coerceIn(
            TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS,
            TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS
        )
        val aboveMax = 75.0f.coerceIn(
            TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS,
            TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS
        )
        val withinRange = 25.0f.coerceIn(
            TuningPreferencesDefaults.MIN_ELEVATION_SMOOTHING_SIGMA_METERS,
            TuningPreferencesDefaults.MAX_ELEVATION_SMOOTHING_SIGMA_METERS
        )

        assertEquals(10.0f, belowMin, 0.001f)
        assertEquals(50.0f, aboveMax, 0.001f)
        assertEquals(25.0f, withinRange, 0.001f)
    }

    @Test
    fun allKeys_containsElevationSmoothingSigmaKey() {
        assertTrue(
            "ALL_KEYS must contain KEY_ELEVATION_SMOOTHING_SIGMA_METERS for complete factory reset",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_ELEVATION_SMOOTHING_SIGMA_METERS)
        )
        assertEquals(
            "tuning_elevation_smoothing_sigma_meters",
            TuningPreferencesDataStore.KEY_ELEVATION_SMOOTHING_SIGMA_METERS.name
        )
    }
}
