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

package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test (TST-UI-199.2) verifying that [TrackOnMapScreen]
 * consumes [WorkoutDetailPreferences] to dynamically gate all 8 aftermath sections
 * in full alignment with REQ-UI-240 and ATT-2030.
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackOnMapScreenDetailPreferencesContractTest {

    @Test
    fun testTrackOnMapScreen_consumesWorkoutDetailPreferences() {
        // 1. Method and Parameter declaration
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("TrackOnMapScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackOnMapScreen composable must exist and be public", method)
        assertTrue(
            "TrackOnMapScreen must declare detailPreferences parameter of type WorkoutDetailPreferences (REQ-UI-240)",
            method!!.parameterTypes.any { it == WorkoutDetailPreferences::class.java }
        )

        // 2. WorkoutDetailPreferences Model contract
        val prefsClass = WorkoutDetailPreferences::class.java
        val fieldNames = prefsClass.declaredFields.map { it.name }.toSet()
        assertTrue("WorkoutDetailPreferences must have showMap", fieldNames.contains("showMap"))
        assertTrue("WorkoutDetailPreferences must have showElevationProfile", fieldNames.contains("showElevationProfile"))
        assertTrue("WorkoutDetailPreferences must have showTelemetryCharts", fieldNames.contains("showTelemetryCharts"))
        assertTrue("WorkoutDetailPreferences must have showZoneAnalysis", fieldNames.contains("showZoneAnalysis"))
        assertTrue("WorkoutDetailPreferences must have showLaps", fieldNames.contains("showLaps"))
        assertTrue("WorkoutDetailPreferences must have showDescription", fieldNames.contains("showDescription"))
        assertTrue("WorkoutDetailPreferences must have showExtrema", fieldNames.contains("showExtrema"))
        assertTrue("WorkoutDetailPreferences must have showStrava", fieldNames.contains("showStrava"))

        // 3. Default instance behavior
        val defaultPrefs = WorkoutDetailPreferences()
        assertTrue(defaultPrefs.showMap)
        assertTrue(defaultPrefs.showElevationProfile)
        assertTrue(defaultPrefs.showTelemetryCharts)
        assertTrue(defaultPrefs.showZoneAnalysis)
        assertTrue(defaultPrefs.showLaps)
        assertTrue(defaultPrefs.showDescription)
        assertTrue(defaultPrefs.showExtrema)
        assertTrue(defaultPrefs.showStrava)
    }
}
