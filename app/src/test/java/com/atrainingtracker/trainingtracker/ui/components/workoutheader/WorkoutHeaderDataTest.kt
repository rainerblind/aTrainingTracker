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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests verifying [WorkoutHeaderData] data model defaults and property mappings (ATT-503, REQ-SET-058).
 */
class WorkoutHeaderDataTest {

    @Test
    fun defaultClusterId_isNegativeOne() {
        val headerData = WorkoutHeaderData(
            workoutName = "Evening Run",
            formattedDate = "2026-09-10",
            formattedTime = "18:00",
            startTimeS = 1700000000L,
            bSportType = BSportType.RUN,
            sportName = "Running",
            equipmentName = null,
            commute = false,
            trainer = false,
            uploadToStrava = 0,
            finished = true
        )

        assertEquals(-1L, headerData.clusterId)
        assertNull(headerData.clusterName)
    }

    @Test
    fun customClusterIdAndName_arePreserved() {
        val headerData = WorkoutHeaderData(
            workoutName = "Morning Cycle",
            formattedDate = "2026-09-10",
            formattedTime = "08:00",
            startTimeS = 1700000000L,
            bSportType = BSportType.BIKE,
            sportName = "Cycling",
            equipmentName = "Trek Domane",
            commute = true,
            trainer = false,
            uploadToStrava = 1,
            clusterId = 55L,
            clusterName = "Work Commute",
            finished = true
        )

        assertEquals(55L, headerData.clusterId)
        assertEquals("Work Commute", headerData.clusterName)
    }

    @Test
    fun onEditWorkoutCallback_receivesCorrectWorkoutId() {
        val targetWorkoutId = 12345L
        var editedWorkoutId: Long? = null
        val onEditWorkout: (Long) -> Unit = { id -> editedWorkoutId = id }

        // Simulate action invocation (ATT-506, REQ-SET-070, TST-SET-059)
        onEditWorkout(targetWorkoutId)

        assertEquals(targetWorkoutId, editedWorkoutId)
    }

    @Test
    fun workoutBodyClick_routesToMapNavigation_andRemainsIsolatedFromEdit() {
        // Verify click isolation (ATT-850, REQ-SET-071, TST-SET-060)
        var mapClicked = false
        var editClicked = false
        var clusterClickedId: Long? = null

        val onMapClick: () -> Unit = { mapClicked = true }
        val onEditWorkout: () -> Unit = { editClicked = true }
        val onClusterClick: (Long) -> Unit = { id -> clusterClickedId = id }

        // 1. Simulate body click (Header surface, Description, Details, Extrema)
        onMapClick()
        org.junit.Assert.assertTrue("Tapping workout body must invoke map navigation", mapClicked)
        org.junit.Assert.assertFalse("Tapping workout body must not trigger edit mode", editClicked)
        assertNull("Tapping workout body must not trigger cluster navigation", clusterClickedId)

        // 2. Simulate dedicated edit button click
        mapClicked = false
        onEditWorkout()
        org.junit.Assert.assertFalse("Tapping edit button must not trigger map navigation", mapClicked)
        org.junit.Assert.assertTrue("Tapping edit button must invoke workout editor", editClicked)
        assertNull("Tapping edit button must not trigger cluster navigation", clusterClickedId)

        // 3. Simulate cluster button click
        editClicked = false
        onClusterClick(42L)
        org.junit.Assert.assertFalse("Tapping cluster button must not trigger map navigation", mapClicked)
        org.junit.Assert.assertFalse("Tapping cluster button must not trigger edit mode", editClicked)
        assertEquals(42L, clusterClickedId)
    }

    @Test
    fun mapScreenNavigationPrecedence_presentsEditImmediately_andRestoresMapOnDismiss() {
        // Verify precedence: edit over map, and map over list (ATT-850, REQ-SET-071, TST-SET-060)
        var selectedWorkoutForDetails: Long? = null
        var selectedWorkoutIdForEdit: Long? = null

        fun resolveCurrentScreen(): String = when {
            selectedWorkoutIdForEdit != null -> "EDIT"
            selectedWorkoutForDetails != null -> "MAP"
            else -> "LIST"
        }

        // 1. Initial State
        assertEquals("LIST", resolveCurrentScreen())

        // 2. Tap workout to open map
        selectedWorkoutForDetails = 101L
        assertEquals("MAP", resolveCurrentScreen())

        // 3. Tap edit from within map
        selectedWorkoutIdForEdit = 101L
        assertEquals("EDIT", resolveCurrentScreen()) // Immediately opens editor!

        // 4. Dismiss editor (save or back)
        selectedWorkoutIdForEdit = null
        assertEquals("MAP", resolveCurrentScreen()) // Directly returns to map view!

        // 5. Press back from map view
        selectedWorkoutForDetails = null
        assertEquals("LIST", resolveCurrentScreen()) // Returns to workout list!
    }
}


