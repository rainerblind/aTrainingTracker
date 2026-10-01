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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import com.atrainingtracker.trainingtracker.EditWorkoutFieldPreferences
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying state mutation, defaults, and factory reset restoration for
 * [WorkoutCardSectionPreferences] and [EditWorkoutFieldPreferences] within the
 * Advanced Tuning context (REQ-UI-216, TST-UI-170.3).
 */
class AftermathTuningSettingsTest {

    @Test
    fun testWorkoutCardSectionPreferences_defaultAndMutation() {
        val defaultPrefs = WorkoutCardSectionPreferences()

        // Verify factory defaults
        assertTrue(defaultPrefs.showDescription)
        assertTrue(defaultPrefs.showExtrema)
        assertTrue(defaultPrefs.showLaps)
        assertTrue(defaultPrefs.showStrava)
        assertTrue(defaultPrefs.showMapPreview)
        assertTrue(defaultPrefs.showElevationProfile)
        assertFalse(defaultPrefs.showTelemetryCharts)
        assertFalse(defaultPrefs.showZoneAnalysis)

        // Simulate tuning customization
        val customizedPrefs = defaultPrefs.copy(
            showMapPreview = false,
            showTelemetryCharts = true,
            showZoneAnalysis = true
        )
        assertFalse(customizedPrefs.showMapPreview)
        assertTrue(customizedPrefs.showTelemetryCharts)
        assertTrue(customizedPrefs.showZoneAnalysis)

        // Simulate reset to factory defaults
        val resetPrefs = WorkoutCardSectionPreferences()
        assertEquals(defaultPrefs, resetPrefs)
    }

    @Test
    fun testEditWorkoutFieldPreferences_defaultAndMutation() {
        val defaultPrefs = EditWorkoutFieldPreferences()

        // Verify factory defaults
        assertTrue(defaultPrefs.showCluster)
        assertTrue(defaultPrefs.showCommuteTrainer)
        assertTrue(defaultPrefs.showStravaUpload)
        assertTrue(defaultPrefs.showDescription)
        assertTrue(defaultPrefs.showGoal)
        assertTrue(defaultPrefs.showMethod)

        // Simulate tuning customization
        val customizedPrefs = defaultPrefs.copy(
            showGoal = false,
            showMethod = false,
            showCommuteTrainer = false
        )
        assertFalse(customizedPrefs.showGoal)
        assertFalse(customizedPrefs.showMethod)
        assertFalse(customizedPrefs.showCommuteTrainer)
        assertTrue(customizedPrefs.showCluster)

        // Simulate reset to factory defaults
        val resetPrefs = EditWorkoutFieldPreferences()
        assertEquals(defaultPrefs, resetPrefs)
    }
}
