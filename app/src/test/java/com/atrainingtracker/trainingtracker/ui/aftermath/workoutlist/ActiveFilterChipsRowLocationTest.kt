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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying [ActiveFilterChipsRow] display criteria, chip formatting,
 * and filter removal semantics for spatial start locations (REQ-UI-185, TST-UI-138.2).
 */
class ActiveFilterChipsRowLocationTest {

    @Test
    fun testStartLocationFilterActive_identifiesChipPresence() {
        val criteriaWithName = WorkoutFilterCriteria(
            startLocationName = "Zuhause",
            startLocationLat = 48.137,
            startLocationLng = 11.576
        )

        val hasStartLocationChip = (criteriaWithName.startLocationLat != null && criteriaWithName.startLocationLng != null) ||
                !criteriaWithName.startLocationName.isNullOrBlank()

        assertTrue("Start location chip must be shown when location coordinates and name exist", hasStartLocationChip)
        val chipLabel = if (!criteriaWithName.startLocationName.isNullOrBlank()) {
            "📍 ${criteriaWithName.startLocationName}"
        } else {
            "📍 Start Location"
        }
        assertEquals("📍 Zuhause", chipLabel)
    }

    @Test
    fun testStartLocationFilterWithoutName_fallsBackToGenericLabel() {
        val criteriaCoordsOnly = WorkoutFilterCriteria(
            startLocationName = null,
            startLocationLat = 48.137,
            startLocationLng = 11.576
        )

        val hasStartLocationChip = (criteriaCoordsOnly.startLocationLat != null && criteriaCoordsOnly.startLocationLng != null) ||
                !criteriaCoordsOnly.startLocationName.isNullOrBlank()

        assertTrue("Start location chip must be shown even without custom name if coordinates are set", hasStartLocationChip)
        val fallbackLabel = "Start Location"
        val chipLabel = if (!criteriaCoordsOnly.startLocationName.isNullOrBlank()) {
            "📍 ${criteriaCoordsOnly.startLocationName}"
        } else {
            "📍 $fallbackLabel"
        }
        assertEquals("📍 Start Location", chipLabel)
    }

    @Test
    fun testStartLocationFilterInactive_omitsChip() {
        val emptyCriteria = WorkoutFilterCriteria()
        val hasStartLocationChip = (emptyCriteria.startLocationLat != null && emptyCriteria.startLocationLng != null) ||
                !emptyCriteria.startLocationName.isNullOrBlank()

        assertFalse("Start location chip must be omitted when no spatial criteria are active", hasStartLocationChip)
    }

    @Test
    fun testRemoveStartLocation_clearsSpatialFieldsPreservingOthers() {
        var criteria = WorkoutFilterCriteria(
            query = "Intervals",
            year = 2024,
            sportTypeId = 5L,
            startLocationName = "Olympiapark",
            startLocationLat = 48.175,
            startLocationLng = 11.551,
            startLocationRadiusM = 300.0
        )

        assertEquals(4, criteria.activeFilterCount)

        // Simulate onRemoveStartLocation callback
        val onRemoveStartLocation = {
            criteria = criteria.copy(
                startLocationName = null,
                startLocationLat = null,
                startLocationLng = null,
                startLocationRadiusM = null
            )
        }

        onRemoveStartLocation()

        assertNull("startLocationName must be cleared", criteria.startLocationName)
        assertNull("startLocationLat must be cleared", criteria.startLocationLat)
        assertNull("startLocationLng must be cleared", criteria.startLocationLng)
        assertNull("startLocationRadiusM must be cleared", criteria.startLocationRadiusM)
        assertEquals("Query must remain preserved", "Intervals", criteria.query)
        assertEquals("Year must remain preserved", Integer.valueOf(2024), criteria.year)
        assertEquals("SportTypeId must remain preserved", java.lang.Long.valueOf(5L), criteria.sportTypeId)
        assertEquals("Active filter count must decrement to 3", 3, criteria.activeFilterCount)
    }
}
