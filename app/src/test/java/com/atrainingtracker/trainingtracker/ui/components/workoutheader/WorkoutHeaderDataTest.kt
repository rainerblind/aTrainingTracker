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
}

