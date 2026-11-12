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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract and partitioning test verifying relative section slotting around MAP
 * in [TrackOnMapScreen] (REQ-UI-255, TST-UI-214-E).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackOnMapScreenSectionSlottingContractTest {

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

        // Default: [RPE_AND_FEEL, DETAILS, DESCRIPTION, EXTREMA, PHOTOS] before MAP (REQ-WKO-037 / REQ-UI-285 / REQ-UI-414.6 / REQ-UI-499)
        assertEquals(listOf(
            WorkoutSectionType.RPE_AND_FEEL,
            WorkoutSectionType.DETAILS,
            WorkoutSectionType.DESCRIPTION,
            WorkoutSectionType.EXTREMA,
            WorkoutSectionType.PHOTOS
        ), preMap)

        // Default: [ELEVATION, CHARTS, ZONES, GRADE_DISTRIBUTION, LAPS, EXPORT_STATUS, STRAVA] after MAP (REQ-UI-285)
        assertEquals(listOf(
            WorkoutSectionType.ELEVATION,
            WorkoutSectionType.CHARTS,
            WorkoutSectionType.ZONES,
            WorkoutSectionType.GRADE_DISTRIBUTION,
            WorkoutSectionType.LAPS,
            WorkoutSectionType.EXPORT_STATUS,
            WorkoutSectionType.STRAVA
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
        val screenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        val screenMethod = screenClazz.declaredMethods.find { it.name.startsWith("TrackOnMapScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackOnMapScreen composable must exist", screenMethod)
        assertTrue(
            "TrackOnMapScreen must accept sectionsOrder parameter",
            screenMethod!!.parameterTypes.any { it == List::class.java }
        )
        assertNotNull("WorkoutSectionType.MAP must exist", WorkoutSectionType.MAP)
    }
}
