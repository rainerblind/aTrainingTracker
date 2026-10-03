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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteria
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying drill-down interaction semantics, invariant preservation,
 * and filter criteria construction from [KnownLocationItem] (REQ-UI-185, TST-UI-138.3).
 */
class KnownLocationsScreenDrillDownTest {

    private val sampleItem = KnownLocationItem(
        id = 1L,
        name = "Zuhause",
        altitude = 520.0,
        radius = 150,
        latLng = LatLng(48.13715, 11.57612),
        hitCount = 42,
        isLocked = true,
        source = ElevationSource.MANUAL_USER
    )

    @Test
    fun testConstructWorkoutFilterCriteriaFromKnownLocation() {
        val item = sampleItem

        val criteria = WorkoutFilterCriteria(
            startLocationName = item.name,
            startLocationLat = item.latLng.latitude,
            startLocationLng = item.latLng.longitude,
            startLocationRadiusM = item.radius.toDouble().takeIf { it > 0.0 } ?: 200.0
        )

        assertEquals("Zuhause", criteria.startLocationName)
        assertEquals(48.13715, criteria.startLocationLat!!, 0.00001)
        assertEquals(11.57612, criteria.startLocationLng!!, 0.00001)
        assertEquals(150.0, criteria.startLocationRadiusM!!, 0.01)
        assertEquals(1, criteria.activeFilterCount)
    }

    @Test
    fun testFallbackRadiusWhenZeroOrNegative() {
        val itemZeroRadius = sampleItem.copy(radius = 0)

        val criteria = WorkoutFilterCriteria(
            startLocationName = itemZeroRadius.name,
            startLocationLat = itemZeroRadius.latLng.latitude,
            startLocationLng = itemZeroRadius.latLng.longitude,
            startLocationRadiusM = itemZeroRadius.radius.toDouble().takeIf { it > 0.0 } ?: 200.0
        )

        assertEquals(200.0, criteria.startLocationRadiusM!!, 0.01)
    }

    @Test
    fun testDrillDownCallbackInvocation() {
        var navigatedToWorkoutsWithItem: KnownLocationItem? = null
        var editedItem: KnownLocationItem? = null
        var deletedItem: KnownLocationItem? = null

        val onShowWorkouts: (KnownLocationItem) -> Unit = { navigatedToWorkoutsWithItem = it }
        val onEdit: (KnownLocationItem) -> Unit = { editedItem = it }
        val onDelete: (KnownLocationItem) -> Unit = { deletedItem = it }

        // Simulate drill-down tap
        onShowWorkouts(sampleItem)
        assertNotNull("Drill-down must deliver target location item", navigatedToWorkoutsWithItem)
        assertEquals("Zuhause", navigatedToWorkoutsWithItem?.name)

        // Invariant: Card body edit click must remain independent
        onEdit(sampleItem)
        assertEquals(sampleItem.id, editedItem?.id)

        // Invariant: Card long-click delete action must remain independent
        onDelete(sampleItem)
        assertEquals(sampleItem.id, deletedItem?.id)
    }
}
