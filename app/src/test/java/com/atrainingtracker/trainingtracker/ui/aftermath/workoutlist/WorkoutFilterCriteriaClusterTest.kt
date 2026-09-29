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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit test suite verifying cluster filtering, active filter count derivation,
 * and JSON serialization/deserialization for [WorkoutFilterCriteria] (REQ-UI-187, TST-UI-141.1).
 */
class WorkoutFilterCriteriaClusterTest {

    private fun createWorkoutWithCluster(clusterId: Long, clusterName: String? = null): WorkoutData {
        return WorkoutData(
            id = 100L,
            finished = true,
            fileBaseName = "workout_100",
            workoutName = "Morning Run",
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1718000000L,
            formattedDate = "2024-06-10",
            formattedTime = "07:30",
            localDateTime = LocalDateTime.of(2024, 6, 10, 7, 30),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "poly",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 10000.0,
            maxDisplacement = 2000.0,
            activeTimeSec = 3000L,
            totalTimeSec = 3000L,
            avgSpeedMps = 3.33,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 500.0,
            maxAltitude = 550.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            clusterId = clusterId,
            clusterName = clusterName
        )
    }

    @Test
    fun matches_clusterMatches_returnsTrue() {
        val workout = createWorkoutWithCluster(clusterId = 42L, clusterName = "Isarrunde")
        val criteria = WorkoutFilterCriteria(clusterId = 42L, clusterName = "Isarrunde")

        assertTrue(criteria.matches(workout))
    }

    @Test
    fun matches_clusterDoesNotMatch_returnsFalse() {
        val workout = createWorkoutWithCluster(clusterId = 99L, clusterName = "Olympiapark")
        val criteria = WorkoutFilterCriteria(clusterId = 42L, clusterName = "Isarrunde")

        assertFalse(criteria.matches(workout))
    }

    @Test
    fun matches_unclusteredWorkoutAgainstClusterFilter_returnsFalse() {
        val unclusteredWorkout = createWorkoutWithCluster(clusterId = -1L, clusterName = null)
        val criteria = WorkoutFilterCriteria(clusterId = 42L, clusterName = "Isarrunde")

        assertFalse(criteria.matches(unclusteredWorkout))
    }

    @Test
    fun matches_noClusterFilterActive_returnsTrueForAnyWorkout() {
        val workout1 = createWorkoutWithCluster(clusterId = 42L)
        val workout2 = createWorkoutWithCluster(clusterId = -1L)
        val criteria = WorkoutFilterCriteria()

        assertTrue(criteria.matches(workout1))
        assertTrue(criteria.matches(workout2))
    }

    @Test
    fun activeFilterCount_incrementsWhenClusterFilterIsActive() {
        val emptyCriteria = WorkoutFilterCriteria()
        assertEquals(0, emptyCriteria.activeFilterCount)
        assertTrue(emptyCriteria.isEmpty)

        val clusterCriteria = WorkoutFilterCriteria(clusterId = 42L, clusterName = "Isarrunde")
        assertEquals(1, clusterCriteria.activeFilterCount)
        assertTrue(clusterCriteria.isNotEmpty)
    }

    @Test
    fun jsonSerialization_roundTrip_preservesClusterFields() {
        val original = WorkoutFilterCriteria(
            query = "tempo",
            clusterId = 42L,
            clusterName = "Isarrunde"
        )

        val jsonStr = original.toJson()
        val deserialized = WorkoutFilterCriteria.fromJson(jsonStr)

        assertEquals(original.query, deserialized.query)
        assertEquals(original.clusterId, deserialized.clusterId)
        assertEquals(original.clusterName, deserialized.clusterName)
        assertEquals(2, deserialized.activeFilterCount)
    }

    @Test
    fun jsonDeserialization_legacyJsonWithoutCluster_defaultsToNull() {
        val legacyJson = """{"query":"test","year":2024}"""
        val deserialized = WorkoutFilterCriteria.fromJson(legacyJson)

        assertEquals("test", deserialized.query)
        assertEquals(2024, deserialized.year)
        assertNull(deserialized.clusterId)
        assertNull(deserialized.clusterName)
    }
}
