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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract and partitioning test verifying relative section slotting around MAP
 * in [TrackOnMapScreen] (REQ-UI-255, TST-UI-214-E).
 */
class TrackOnMapScreenSectionSlottingContractTest {

    private fun findTrackOnMapScreenFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("TrackOnMapScreen.kt not found in candidates: $candidates")
    }

    private fun partitionSections(order: List<WorkoutSectionType>): Pair<List<WorkoutSectionType>, List<WorkoutSectionType>> {
        val mapIndex = order.indexOf(WorkoutSectionType.MAP)
        val pre = if (mapIndex >= 0) order.subList(0, mapIndex) else order
        val post = if (mapIndex >= 0) order.subList(mapIndex + 1, order.size) else emptyList()
        return Pair(pre, post)
    }

    @Test
    fun testPartitioningLogic_defaultOrder() {
        val defaultOrder = WorkoutSectionType.DEFAULT_ORDER
        val (preMap, postMap) = partitionSections(defaultOrder)

        // Default: [DESCRIPTION, EXTREMA, LAPS, STRAVA] before MAP
        assertEquals(listOf(
            WorkoutSectionType.DESCRIPTION,
            WorkoutSectionType.EXTREMA,
            WorkoutSectionType.LAPS,
            WorkoutSectionType.STRAVA
        ), preMap)

        // Default: [ELEVATION, CHARTS, ZONES] after MAP
        assertEquals(listOf(
            WorkoutSectionType.ELEVATION,
            WorkoutSectionType.CHARTS,
            WorkoutSectionType.ZONES
        ), postMap)
    }

    @Test
    fun testPartitioningLogic_chartsAndZonesBeforeMap() {
        // Custom scenario from REQ-UI-255 Acceptance Criteria:
        // CHARTS and ZONES precede MAP, while EXTREMA follows MAP
        val customOrder = listOf(
            WorkoutSectionType.CHARTS,
            WorkoutSectionType.ZONES,
            WorkoutSectionType.MAP,
            WorkoutSectionType.EXTREMA,
            WorkoutSectionType.DESCRIPTION,
            WorkoutSectionType.LAPS,
            WorkoutSectionType.STRAVA,
            WorkoutSectionType.ELEVATION
        )

        val (preMap, postMap) = partitionSections(customOrder)

        assertEquals(listOf(
            WorkoutSectionType.CHARTS,
            WorkoutSectionType.ZONES
        ), preMap)

        assertEquals(listOf(
            WorkoutSectionType.EXTREMA,
            WorkoutSectionType.DESCRIPTION,
            WorkoutSectionType.LAPS,
            WorkoutSectionType.STRAVA,
            WorkoutSectionType.ELEVATION
        ), postMap)
    }

    @Test
    fun testTrackOnMapScreen_structuralContract() {
        val file = findTrackOnMapScreenFile()
        val content = file.readText()

        // 1. Accepts sectionsOrder parameter
        assertTrue(
            "TrackOnMapScreen must accept sectionsOrder parameter",
            content.contains("sectionsOrder: List<WorkoutSectionType>? = null")
        )

        // 2. Partitions sections around MAP
        assertTrue(
            "Must locate MAP in activeSectionsOrder",
            content.contains("activeSectionsOrder.indexOf(WorkoutSectionType.MAP)")
        )
        assertTrue(
            "Must calculate preMapSections",
            content.contains("preMapSections")
        )
        assertTrue(
            "Must calculate postMapSections",
            content.contains("postMapSections")
        )

        // 3. Dynamic elevation and charts post-map check
        assertTrue(
            "Must determine isElevationPostMap",
            content.contains("isElevationPostMap")
        )
        assertTrue(
            "Must determine isChartsPostMap",
            content.contains("isChartsPostMap")
        )
    }
}
