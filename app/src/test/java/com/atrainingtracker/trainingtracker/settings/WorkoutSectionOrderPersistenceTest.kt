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

import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailSectionType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDisplayContext
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import com.atrainingtracker.trainingtracker.ui.aftermath.toWorkoutDetailSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WorkoutSectionType] and section order persistence (REQ-UI-255, REQ-UI-499, TST-UI-499).
 */
class WorkoutSectionOrderPersistenceTest {

    @Test
    fun testDefaultWorkoutSectionsOrder() {
        val defaultOrder = WorkoutSectionType.DEFAULT_ORDER
        assertEquals(12, defaultOrder.size)
        assertEquals(WorkoutSectionType.DETAILS, defaultOrder[0])
        assertEquals(WorkoutSectionType.DESCRIPTION, defaultOrder[1])
        assertEquals(WorkoutSectionType.EXTREMA, defaultOrder[2])
        assertEquals(WorkoutSectionType.PHOTOS, defaultOrder[3])
        assertEquals(WorkoutSectionType.MAP, defaultOrder[4])
        assertEquals(WorkoutSectionType.ELEVATION, defaultOrder[5])
        assertEquals(WorkoutSectionType.CHARTS, defaultOrder[6])
        assertEquals(WorkoutSectionType.ZONES, defaultOrder[7])
        assertEquals(WorkoutSectionType.GRADE_DISTRIBUTION, defaultOrder[8])
        assertEquals(WorkoutSectionType.LAPS, defaultOrder[9])
        assertEquals(WorkoutSectionType.EXPORT_STATUS, defaultOrder[10])
        assertEquals(WorkoutSectionType.STRAVA, defaultOrder[11])
    }

    @Test
    fun testWorkoutDisplayContextValues() {
        val contexts = WorkoutDisplayContext.values()
        assertEquals(2, contexts.size)
        assertTrue(contexts.contains(WorkoutDisplayContext.LIST_CARD))
        assertTrue(contexts.contains(WorkoutDisplayContext.FULL_DETAIL))
    }

    @Test
    fun testSerializationAndDeserializationRoundtrip() {
        val customOrder = listOf(
            WorkoutSectionType.PHOTOS,
            WorkoutSectionType.DETAILS,
            WorkoutSectionType.CHARTS,
            WorkoutSectionType.ZONES,
            WorkoutSectionType.GRADE_DISTRIBUTION,
            WorkoutSectionType.MAP,
            WorkoutSectionType.DESCRIPTION,
            WorkoutSectionType.EXTREMA,
            WorkoutSectionType.LAPS,
            WorkoutSectionType.EXPORT_STATUS,
            WorkoutSectionType.STRAVA,
            WorkoutSectionType.ELEVATION
        )

        val serialized = WorkoutSectionType.toSerializedString(customOrder)
        assertEquals("PHOTOS,DETAILS,CHARTS,ZONES,GRADE_DISTRIBUTION,MAP,DESCRIPTION,EXTREMA,LAPS,EXPORT_STATUS,STRAVA,ELEVATION", serialized)

        val deserialized = WorkoutSectionType.fromSerializedString(serialized)
        assertEquals(customOrder, deserialized)
    }

    @Test
    fun testDeserializationFallback_nullOrBlank() {
        assertEquals(WorkoutSectionType.DEFAULT_ORDER, WorkoutSectionType.fromSerializedString(null))
        assertEquals(WorkoutSectionType.DEFAULT_ORDER, WorkoutSectionType.fromSerializedString(""))
        assertEquals(WorkoutSectionType.DEFAULT_ORDER, WorkoutSectionType.fromSerializedString("   "))
    }

    @Test
    fun testDeserializationFallback_invalidCorruptedString() {
        val corrupted = "NON_EXISTENT_SECTION,INVALID_123,???"
        val result = WorkoutSectionType.fromSerializedString(corrupted)
        assertEquals(WorkoutSectionType.DEFAULT_ORDER, result)
    }

    @Test
    fun testSelfHealing_missingSectionsAppended() {
        // Only 3 sections saved previously
        val partial = "CHARTS,ZONES,MAP"
        val healed = WorkoutSectionType.fromSerializedString(partial)

        assertEquals(12, healed.size)
        // Missing DETAILS is inserted at index 0 because PHOTOS was not at index 0
        assertEquals(WorkoutSectionType.DETAILS, healed[0])
        assertEquals(WorkoutSectionType.CHARTS, healed[1])
        assertEquals(WorkoutSectionType.ZONES, healed[2])
        assertEquals(WorkoutSectionType.MAP, healed[3])

        // Verify remaining missing sections are appended without omission
        assertTrue(healed.contains(WorkoutSectionType.DESCRIPTION))
        assertTrue(healed.contains(WorkoutSectionType.EXTREMA))
        assertTrue(healed.contains(WorkoutSectionType.PHOTOS))
        assertTrue(healed.contains(WorkoutSectionType.GRADE_DISTRIBUTION))
        assertTrue(healed.contains(WorkoutSectionType.LAPS))
        assertTrue(healed.contains(WorkoutSectionType.EXPORT_STATUS))
        assertTrue(healed.contains(WorkoutSectionType.STRAVA))
        assertTrue(healed.contains(WorkoutSectionType.ELEVATION))
    }

    @Test
    fun testSelfHealing_deduplication() {
        val duplicates = "MAP,MAP,CHARTS,MAP,ZONES"
        val result = WorkoutSectionType.fromSerializedString(duplicates)

        assertEquals(12, result.size)
        assertEquals(WorkoutSectionType.DETAILS, result[0])
        assertEquals(WorkoutSectionType.MAP, result[1])
        assertEquals(WorkoutSectionType.CHARTS, result[2])
        assertEquals(WorkoutSectionType.ZONES, result[3])
        assertEquals(result.distinct().size, result.size)
    }

    @Test
    fun testMigration_whenPhotosAtZero_insertsDetailsAtOne() {
        val legacy = "PHOTOS,DESCRIPTION,EXTREMA,MAP,ELEVATION,CHARTS,ZONES,GRADE_DISTRIBUTION,LAPS,EXPORT_STATUS,STRAVA"
        val migrated = WorkoutSectionType.fromSerializedString(legacy)

        assertEquals(12, migrated.size)
        assertEquals(WorkoutSectionType.PHOTOS, migrated[0])
        assertEquals(WorkoutSectionType.DETAILS, migrated[1])
        assertEquals(WorkoutSectionType.DESCRIPTION, migrated[2])
    }

    @Test
    fun testMigration_whenPhotosNotAtZero_insertsDetailsAtZero() {
        val legacy = "DESCRIPTION,EXTREMA,PHOTOS,MAP,ELEVATION,CHARTS,ZONES,GRADE_DISTRIBUTION,LAPS,EXPORT_STATUS,STRAVA"
        val migrated = WorkoutSectionType.fromSerializedString(legacy)

        assertEquals(12, migrated.size)
        assertEquals(WorkoutSectionType.DETAILS, migrated[0])
        assertEquals(WorkoutSectionType.DESCRIPTION, migrated[1])
        assertEquals(WorkoutSectionType.EXTREMA, migrated[2])
    }

    @Test
    fun testBidirectionalMappingWithWorkoutDetailSectionType() {
        assertEquals(WorkoutSectionType.DETAILS, WorkoutDetailSectionType.DETAILS.toWorkoutSectionTypeOrNull())
        assertEquals(WorkoutDetailSectionType.DETAILS, WorkoutSectionType.DETAILS.toWorkoutDetailSectionType())
    }
}
