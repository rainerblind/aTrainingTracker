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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying dynamic section order rendering and permanent top identity anchors
 * in [WorkoutSummary] (REQ-UI-255, TST-UI-214-D).
 */
class WorkoutSummaryDynamicOrderContractTest {

    private fun findWorkoutSummaryFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("WorkoutSummary.kt not found in candidates: $candidates")
    }

    @Test
    fun testWorkoutSummary_permanentTopIdentityAnchorContract() {
        val file = findWorkoutSummaryFile()
        val content = file.readText()

        // Verify WorkoutHeader and WorkoutDetails are rendered before dynamic section iteration
        val headerIndex = content.indexOf("WorkoutHeader(")
        val detailsIndex = content.indexOf("WorkoutDetails(")
        val loopIndex = content.indexOf("workoutSectionsOrder.forEach")

        assertTrue("WorkoutHeader must be present", headerIndex != -1)
        assertTrue("WorkoutDetails must be present", detailsIndex != -1)
        assertTrue("Dynamic section loop must be present", loopIndex != -1)

        assertTrue("WorkoutHeader must precede details", headerIndex < detailsIndex)
        assertTrue("WorkoutDetails must precede dynamic section loop", detailsIndex < loopIndex)
    }

    @Test
    fun testWorkoutSummary_dynamicOrderIterationContract() {
        val file = findWorkoutSummaryFile()
        val content = file.readText()

        // Verify WorkoutSummary accepts workoutSectionsOrder
        assertTrue(
            "WorkoutSummary must accept workoutSectionsOrder parameter",
            content.contains("workoutSectionsOrder: List<WorkoutSectionType>")
        )

        // Verify loop handles all 8 customizable section types
        WorkoutSectionType.values().forEach { sectionType ->
            assertTrue(
                "Dynamic loop must handle section type $sectionType",
                content.contains("WorkoutSectionType.${sectionType.name}")
            )
        }
    }

    @Test
    fun testOrderResolution_customSequence() {
        val customOrder = listOf(
            WorkoutSectionType.CHARTS,
            WorkoutSectionType.ZONES,
            WorkoutSectionType.MAP,
            WorkoutSectionType.ELEVATION,
            WorkoutSectionType.LAPS,
            WorkoutSectionType.EXPORT_STATUS,
            WorkoutSectionType.STRAVA,
            WorkoutSectionType.DESCRIPTION,
            WorkoutSectionType.EXTREMA
        )

        val prefs = WorkoutCardSectionPreferences(
            showLaps = true,
            showElevationProfile = true,
            showTelemetryCharts = true,
            showZoneAnalysis = true,
            showExportStatus = true
        )
        val renderedSections = mutableListOf<WorkoutSectionType>()

        customOrder.forEach { type ->
            val isEnabled = when (type) {
                WorkoutSectionType.DESCRIPTION -> prefs.showDescription
                WorkoutSectionType.EXTREMA -> prefs.showExtrema
                WorkoutSectionType.LAPS -> prefs.showLaps
                WorkoutSectionType.STRAVA -> prefs.showStrava
                WorkoutSectionType.MAP -> prefs.showMapPreview
                WorkoutSectionType.ELEVATION -> prefs.showElevationProfile
                WorkoutSectionType.CHARTS -> prefs.showTelemetryCharts
                WorkoutSectionType.ZONES -> prefs.showZoneAnalysis
                WorkoutSectionType.EXPORT_STATUS -> prefs.showExportStatus
            }
            if (isEnabled) {
                renderedSections.add(type)
            }
        }

        // When all are enabled by default, rendered sequence matches customOrder exactly
        assertEquals(customOrder, renderedSections)
        assertEquals(WorkoutSectionType.CHARTS, renderedSections[0])
        assertEquals(WorkoutSectionType.ZONES, renderedSections[1])
        assertEquals(WorkoutSectionType.MAP, renderedSections[2])
    }
}
