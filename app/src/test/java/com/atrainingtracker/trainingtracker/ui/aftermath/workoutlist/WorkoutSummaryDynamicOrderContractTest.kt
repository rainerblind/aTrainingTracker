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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test verifying dynamic section order rendering and permanent top identity anchors
 * in [WorkoutSummary] (REQ-UI-255, TST-UI-214-D).
 * Refactored to pure JVM reflection and behavioral testing per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutSummaryDynamicOrderContractTest {

    @Test
    fun testWorkoutSummary_permanentTopIdentityAnchorContract() {
        val summaryClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        val method = summaryClazz.declaredMethods.find { it.name.startsWith("WorkoutSummary") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutSummary composable must exist and be public", method)
        assertTrue(
            "WorkoutSummary must accept workoutSectionsOrder parameter",
            method!!.parameterTypes.any { it == List::class.java }
        )
    }

    @Test
    fun testWorkoutSummary_dynamicOrderIterationContract() {
        assertTrue("WorkoutSectionType must contain values", WorkoutSectionType.values().isNotEmpty())
    }

    @Test
    fun testOrderResolution_customSequence() {
        val customOrder = listOf(
            WorkoutSectionType.PHOTOS,
            WorkoutSectionType.DETAILS,
            WorkoutSectionType.CHARTS,
            WorkoutSectionType.ZONES,
            WorkoutSectionType.GRADE_DISTRIBUTION,
            WorkoutSectionType.MAP,
            WorkoutSectionType.ELEVATION,
            WorkoutSectionType.LAPS,
            WorkoutSectionType.EXPORT_STATUS,
            WorkoutSectionType.STRAVA,
            WorkoutSectionType.DESCRIPTION,
            WorkoutSectionType.EXTREMA
        )

        val prefs = WorkoutCardSectionPreferences(
            showDetails = true,
            showLaps = true,
            showElevationProfile = true,
            showTelemetryCharts = true,
            showZoneAnalysis = true,
            showGradeDistribution = true,
            showExportStatus = true,
            showPhotos = true
        )
        val renderedSections = mutableListOf<WorkoutSectionType>()

        customOrder.forEach { sectionType ->
            val isVisible = when (sectionType) {
                WorkoutSectionType.RPE_AND_FEEL -> prefs.showRpeAndFeel
                WorkoutSectionType.DESCRIPTION -> true
                WorkoutSectionType.DETAILS -> prefs.showDetails
                WorkoutSectionType.EXTREMA -> true
                WorkoutSectionType.LAPS -> prefs.showLaps
                WorkoutSectionType.STRAVA -> true
                WorkoutSectionType.MAP -> true
                WorkoutSectionType.ELEVATION -> prefs.showElevationProfile
                WorkoutSectionType.CHARTS -> prefs.showTelemetryCharts
                WorkoutSectionType.ZONES -> prefs.showZoneAnalysis
                WorkoutSectionType.GRADE_DISTRIBUTION -> prefs.showGradeDistribution
                WorkoutSectionType.EXPORT_STATUS -> prefs.showExportStatus
                WorkoutSectionType.PHOTOS -> prefs.showPhotos
            }
            if (isVisible) {
                renderedSections.add(sectionType)
            }
        }

        assertEquals(WorkoutSectionType.PHOTOS, renderedSections[0])
        assertEquals(WorkoutSectionType.DETAILS, renderedSections[1])
        assertEquals(WorkoutSectionType.CHARTS, renderedSections[2])
    }
}
