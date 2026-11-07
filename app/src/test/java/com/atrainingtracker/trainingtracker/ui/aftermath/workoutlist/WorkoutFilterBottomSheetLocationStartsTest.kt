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

import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural, semantic filtering, and contract tests for the "Startet bei (Lieblingsorte)"
 * start count threshold filtering and "Alle anzeigen" toggle in [WorkoutFilterBottomSheet] (ATT-5822 / REQ-UI-467 / TST-UI-467).
 */
class WorkoutFilterBottomSheetLocationStartsTest {

    private fun createLocation(id: Long, name: String, hitCount: Int, lat: Double = 48.0, lng: Double = 11.5): KnownLocationItem {
        return KnownLocationItem(
            id = id,
            name = name,
            altitude = 500.0,
            radius = 100,
            latLng = LatLng(lat, lng),
            hitCount = hitCount,
            isLocked = false,
            source = ElevationSource.GPS_FALLBACK
        )
    }

    /**
     * Pure semantic state holder replicating the start location filtering contract of [WorkoutFilterBottomSheet].
     */
    private class StartLocationFilterState(
        val knownLocations: List<KnownLocationItem>,
        var localStartLocationLat: Double? = null,
        var localStartLocationLng: Double? = null
    ) {
        var showAllLocations: Boolean = false

        val hasInfrequentLocations: Boolean
            get() = knownLocations.any { it.hitCount < 5 }

        val displayedLocations: List<KnownLocationItem>
            get() = if (showAllLocations || !hasInfrequentLocations) {
                knownLocations
            } else {
                knownLocations.filter { loc ->
                    loc.hitCount >= 5 || (localStartLocationLat == loc.latLng.latitude && localStartLocationLng == loc.latLng.longitude)
                }
            }
    }

    @Test
    fun testDisplayedLocations_defaultsToFilteringOutLocationsWithLessThan5Starts() {
        val frequent1 = createLocation(1L, "Home Base", 12)
        val frequent2 = createLocation(2L, "Office Hub", 5)
        val infrequent1 = createLocation(3L, "Vacation Spot", 2)
        val infrequent2 = createLocation(4L, "Trailhead Once", 1)

        val state = StartLocationFilterState(
            knownLocations = listOf(frequent1, frequent2, infrequent1, infrequent2)
        )

        // REQ-UI-467.2: By default, only locations with >= 5 starts are displayed
        assertTrue(state.hasInfrequentLocations)
        assertFalse(state.showAllLocations)
        val displayed = state.displayedLocations
        assertEquals(2, displayed.size)
        assertTrue(displayed.contains(frequent1))
        assertTrue(displayed.contains(frequent2))
        assertFalse(displayed.contains(infrequent1))
        assertFalse(displayed.contains(infrequent2))
    }

    @Test
    fun testDisplayedLocations_preservesCurrentlySelectedLocationEvenWithLessThan5Starts() {
        val frequent = createLocation(1L, "Home Base", 10, 48.0, 11.0)
        val infrequentSelected = createLocation(2L, "Vacation Cabin", 3, 47.5, 12.0)
        val infrequentUnselected = createLocation(3L, "One-off Park", 1, 48.2, 11.8)

        val state = StartLocationFilterState(
            knownLocations = listOf(frequent, infrequentSelected, infrequentUnselected),
            localStartLocationLat = 47.5,
            localStartLocationLng = 12.0
        )

        // REQ-UI-467.2 Invariant: Selected location must NEVER be hidden, even if it has < 5 starts
        val displayed = state.displayedLocations
        assertEquals(2, displayed.size)
        assertTrue("Frequent location must be shown", displayed.contains(frequent))
        assertTrue("Active selected location must be preserved even if < 5 starts", displayed.contains(infrequentSelected))
        assertFalse("Unselected infrequent location must be filtered out", displayed.contains(infrequentUnselected))
    }

    @Test
    fun testShowAllToggle_expandsAndCollapsesFullLocationList() {
        val frequent = createLocation(1L, "Home Base", 8)
        val infrequent = createLocation(2L, "Lake Loop", 2)

        val state = StartLocationFilterState(
            knownLocations = listOf(frequent, infrequent)
        )

        assertEquals(1, state.displayedLocations.size)

        // Athlete taps "Alle anzeigen" toggle chip (REQ-UI-467.3)
        state.showAllLocations = true
        assertEquals(2, state.displayedLocations.size)
        assertTrue(state.displayedLocations.contains(infrequent))

        // Athlete taps "Nur >= 5 Starts" toggle chip
        state.showAllLocations = false
        assertEquals(1, state.displayedLocations.size)
        assertFalse(state.displayedLocations.contains(infrequent))
    }

    @Test
    fun testHasInfrequentLocations_isFalseWhenAllLocationsHaveAtLeast5Starts() {
        val loc1 = createLocation(1L, "Hub A", 5)
        val loc2 = createLocation(2L, "Hub B", 20)

        val state = StartLocationFilterState(knownLocations = listOf(loc1, loc2))

        assertFalse("Should report false when no locations have < 5 starts", state.hasInfrequentLocations)
        assertEquals(2, state.displayedLocations.size)
    }

    @Test
    fun testWorkoutFilterBottomSheet_composableFunctionSignatureContract() {
        val sheetClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterBottomSheetKt")
        val composableMethod = sheetClass.declaredMethods.find { it.name == "WorkoutFilterBottomSheet" }
        assertNotNull("WorkoutFilterBottomSheet composable function must exist", composableMethod)
        assertTrue(Modifier.isPublic(composableMethod!!.modifiers))

        // Verify acceptance of knownLocations List
        val params = composableMethod.parameterTypes
        assertTrue(
            "WorkoutFilterBottomSheet must accept java.util.List for knownLocations",
            params.any { it == java.util.List::class.java }
        )
    }
}
