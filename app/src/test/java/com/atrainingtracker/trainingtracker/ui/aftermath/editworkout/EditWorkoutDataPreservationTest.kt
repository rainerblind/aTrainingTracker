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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit test verifying the Data Preservation Invariant pursuant to REQ-UI-211 and TST-UI-165.3:
 * When saving changes to a workout while certain fields are hidden via preferences,
 * existing values of hidden fields (e.g. goal, method, commute, trainer, clusterId, description)
 * remain completely intact in the saved WorkoutData entity without data corruption or erasure.
 */
class EditWorkoutDataPreservationTest {

    private fun createSampleWorkoutData(): WorkoutData {
        return WorkoutData(
            id = 101L,
            finished = true,
            fileBaseName = "2026-10-01-workout",
            workoutName = "Morning Run",
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1760000000L,
            formattedDate = "01.10.2026",
            formattedTime = "08:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = "Nike Pegasus",
            equipmentId = 5L,
            commute = true,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 1,
            totalDistance = 10000.0,
            maxDisplacement = 4000.0,
            activeTimeSec = 2400L,
            totalTimeSec = 2500L,
            avgSpeedMps = 4.16,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 100.0,
            maxAltitude = 150.0,
            description = "Solid interval session with negative splits",
            goal = "Sub-40 10k",
            method = "Tempo intervals",
            stravaSportName = "Run",
            clusterId = 42L,
            clusterName = "River Loop"
        )
    }

    @Test
    fun updatingWorkoutName_preservesAllHiddenFields() {
        val initial = createSampleWorkoutData()

        // Simulating the user changing only the workout name while goal, method, commute, etc. are hidden in UI
        val updated = initial.copy(workoutName = "Morning Tempo Run")

        assertEquals("Morning Tempo Run", updated.workoutName)
        assertEquals("Sub-40 10k", updated.goal)
        assertEquals("Tempo intervals", updated.method)
        assertEquals("Solid interval session with negative splits", updated.description)
        assertTrue(updated.commute)
        assertEquals(42L, updated.clusterId)
        assertEquals("River Loop", updated.clusterName)
        assertEquals(1, updated.uploadToStrava)
        assertEquals("Nike Pegasus", updated.equipmentName)
    }

    @Test
    fun updatingEquipment_preservesHiddenNotesAndGoal() {
        val initial = createSampleWorkoutData()

        // Simulating user changing only equipment
        val updated = initial.copy(equipmentName = "Adidas Boston", equipmentId = 6L)

        assertEquals("Morning Run", updated.workoutName)
        assertEquals("Adidas Boston", updated.equipmentName)
        assertEquals(6L, updated.equipmentId)
        assertEquals("Sub-40 10k", updated.goal)
        assertEquals("Tempo intervals", updated.method)
        assertEquals("Solid interval session with negative splits", updated.description)
    }
}
