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

import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningPaceCeilingFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for pace ceiling preferences model, default constants, bounds, and formatting (TST-UI-202.1 / REQ-UI-243).
 */
class TuningPreferencesDataStorePaceCeilingTest {

    @Test
    fun defaultsConstants_matchExpectedBounds() {
        assertEquals(3.0f, TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM, 0.001f)
        assertEquals(2.0f, TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM, 0.001f)
        assertEquals(6.0f, TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM, 0.001f)
    }

    @Test
    fun tuningConfig_defaultsToAthleticPaceCeiling() {
        val config = TuningConfig()
        assertEquals(3.0f, config.paceCeilingMinKm, 0.001f)
    }

    @Test
    fun tuningConfig_customPaceCeiling_retained() {
        val config = TuningConfig(paceCeilingMinKm = 2.5f)
        assertEquals(2.5f, config.paceCeilingMinKm, 0.001f)
    }

    @Test
    fun tuningConfig_clampingBoundsEnforced() {
        val belowMin = 1.0f.coerceIn(
            TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM,
            TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM
        )
        val aboveMax = 10.0f.coerceIn(
            TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM,
            TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM
        )
        val withinRange = 3.5f.coerceIn(
            TuningPreferencesDefaults.MIN_PACE_CEILING_MIN_KM,
            TuningPreferencesDefaults.MAX_PACE_CEILING_MIN_KM
        )

        assertEquals(2.0f, belowMin, 0.001f)
        assertEquals(6.0f, aboveMax, 0.001f)
        assertEquals(3.5f, withinRange, 0.001f)
    }

    @Test
    fun dataStoreKey_hasExpectedName() {
        assertEquals("tuning_pace_ceiling_min_km", TuningPreferencesDataStore.KEY_PACE_CEILING_MIN_KM.name)
    }

    @Test
    fun formatter_metric_formatsCorrectly() {
        assertEquals("3:00 min/km", TuningPaceCeilingFormatter.formatPaceCeiling(3.0f, true))
        assertEquals("2:15 min/km", TuningPaceCeilingFormatter.formatPaceCeiling(2.25f, true))
        assertEquals("2:30 min/km", TuningPaceCeilingFormatter.formatPaceCeiling(2.5f, true))
        assertEquals("3:45 min/km", TuningPaceCeilingFormatter.formatPaceCeiling(3.75f, true))
        assertEquals("6:00 min/km", TuningPaceCeilingFormatter.formatPaceCeiling(6.0f, true))
    }

    @Test
    fun formatter_imperial_formatsCorrectly() {
        // 3.0 min/km * 1.609344 km/mi = 4.828032 min/mi -> 4 min 49.68 sec -> 4:50 min/mi
        val formatted = TuningPaceCeilingFormatter.formatPaceCeiling(3.0f, false)
        assertTrue(formatted.endsWith("min/mi"))
        assertEquals("4:50 min/mi", formatted)
    }
}
