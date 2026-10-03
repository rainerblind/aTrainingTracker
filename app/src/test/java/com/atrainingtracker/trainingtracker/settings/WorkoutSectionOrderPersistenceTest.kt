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

import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDisplayContext
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WorkoutSectionType] and section order persistence (REQ-UI-255, TST-UI-214-A, TST-UI-214-B).
 */
class WorkoutSectionOrderPersistenceTest {

    @Test
    fun testDefaultWorkoutSectionsOrder() {
        val defaultOrder = WorkoutSectionType.DEFAULT_ORDER
        assertEquals(8, defaultOrder.size)
        assertEquals(WorkoutSectionType.DESCRIPTION, defaultOrder[0])
        assertEquals(WorkoutSectionType.EXTREMA, defaultOrder[1])
        assertEquals(WorkoutSectionType.LAPS, defaultOrder[2])
        assertEquals(WorkoutSectionType.STRAVA, defaultOrder[3])
        assertEquals(WorkoutSectionType.MAP, defaultOrder[4])
        assertEquals(WorkoutSectionType.ELEVATION, defaultOrder[5])
        assertEquals(WorkoutSectionType.CHARTS, defaultOrder[6])
        assertEquals(WorkoutSectionType.ZONES, defaultOrder[7])
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
            WorkoutSectionType.CHARTS,
            WorkoutSectionType.ZONES,
            WorkoutSectionType.MAP,
            WorkoutSectionType.DESCRIPTION,
            WorkoutSectionType.EXTREMA,
            WorkoutSectionType.LAPS,
            WorkoutSectionType.STRAVA,
            WorkoutSectionType.ELEVATION
        )

        val serialized = WorkoutSectionType.toSerializedString(customOrder)
        assertEquals("CHARTS,ZONES,MAP,DESCRIPTION,EXTREMA,LAPS,STRAVA,ELEVATION", serialized)

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

        assertEquals(8, healed.size)
        assertEquals(WorkoutSectionType.CHARTS, healed[0])
        assertEquals(WorkoutSectionType.ZONES, healed[1])
        assertEquals(WorkoutSectionType.MAP, healed[2])

        // Verify remaining missing sections are appended without omission
        assertTrue(healed.contains(WorkoutSectionType.DESCRIPTION))
        assertTrue(healed.contains(WorkoutSectionType.EXTREMA))
        assertTrue(healed.contains(WorkoutSectionType.LAPS))
        assertTrue(healed.contains(WorkoutSectionType.STRAVA))
        assertTrue(healed.contains(WorkoutSectionType.ELEVATION))
    }

    @Test
    fun testSelfHealing_deduplication() {
        val duplicates = "MAP,MAP,CHARTS,MAP,ZONES"
        val result = WorkoutSectionType.fromSerializedString(duplicates)

        assertEquals(8, result.size)
        assertEquals(WorkoutSectionType.MAP, result[0])
        assertEquals(WorkoutSectionType.CHARTS, result[1])
        assertEquals(WorkoutSectionType.ZONES, result[2])
        assertEquals(result.distinct().size, result.size)
    }
}
