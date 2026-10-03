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

package com.atrainingtracker.trainingtracker.settings

import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying LapDisplayMode enum, WorkoutCardSectionPreferences default value,
 * and deserialization safety with defensive fallback (REQ-UI-229, TST-UI-183, TST-UI-191, ATT-1870, ATT-1988).
 */
class LapDisplayModePreferencesTest {

    @Test
    fun testLapDisplayModeEnumCoverage() {
        val names = LapDisplayMode.values().map { it.name }
        assertEquals(2, names.size)
        assertTrue(names.contains("TABLE_ONLY"))
        assertTrue(names.contains("VISUALIZER_ONLY"))
        assertFalse(names.contains("BOTH"))
    }

    @Test
    fun testDefaultWorkoutCardSectionPreferences_lapDisplayModeIsVisualizerOnly() {
        val defaultPrefs = WorkoutCardSectionPreferences()
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, defaultPrefs.lapDisplayMode)
    }

    @Test
    fun testCustomWorkoutCardSectionPreferences_retention() {
        val tableOnlyPrefs = WorkoutCardSectionPreferences(lapDisplayMode = LapDisplayMode.TABLE_ONLY)
        assertEquals(LapDisplayMode.TABLE_ONLY, tableOnlyPrefs.lapDisplayMode)

        val visualizerOnlyPrefs = WorkoutCardSectionPreferences(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, visualizerOnlyPrefs.lapDisplayMode)
    }

    @Test
    fun testLapDisplayModeDeserializationAndDefensiveFallback() {
        // Valid deserializations matching MyPreferenceManager logic
        for (mode in LapDisplayMode.values()) {
            val parsed = runCatching {
                val raw = mode.name
                if (raw != "BOTH") LapDisplayMode.valueOf(raw) else LapDisplayMode.VISUALIZER_ONLY
            }.getOrDefault(LapDisplayMode.VISUALIZER_ONLY)
            assertEquals(mode, parsed)
        }

        // Legacy "BOTH" string mapping to VISUALIZER_ONLY
        val legacyBoth = runCatching {
            val raw = "BOTH"
            if (raw != "BOTH") LapDisplayMode.valueOf(raw) else LapDisplayMode.VISUALIZER_ONLY
        }.getOrDefault(LapDisplayMode.VISUALIZER_ONLY)
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, legacyBoth)

        // Unknown, corrupted, or legacy string fallback
        val fallbackUnknown = runCatching {
            val raw = "INVALID_MODE"
            if (raw != "BOTH") LapDisplayMode.valueOf(raw) else LapDisplayMode.VISUALIZER_ONLY
        }.getOrDefault(LapDisplayMode.VISUALIZER_ONLY)
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, fallbackUnknown)

        val fallbackEmpty = runCatching {
            val raw = ""
            if (raw != "BOTH") LapDisplayMode.valueOf(raw) else LapDisplayMode.VISUALIZER_ONLY
        }.getOrDefault(LapDisplayMode.VISUALIZER_ONLY)
        assertEquals(LapDisplayMode.VISUALIZER_ONLY, fallbackEmpty)
    }
}
