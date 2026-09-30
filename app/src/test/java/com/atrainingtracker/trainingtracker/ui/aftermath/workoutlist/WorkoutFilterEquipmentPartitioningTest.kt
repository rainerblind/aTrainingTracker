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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying sport category equipment partitioning and sport-tab context awareness
 * in [WorkoutFilterBottomSheet] (REQ-UI-193, TST-UI-147).
 */
class WorkoutFilterEquipmentPartitioningTest {

    private fun createWorkout(
        id: Long,
        sportId: Long,
        sportName: String,
        bSportType: BSportType,
        equipmentId: Long,
        equipmentName: String?
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Workout $id",
            sportId = sportId,
            sportName = sportName,
            bSportType = bSportType,
            startTimeS = 1718000000L,
            formattedDate = "2024-06-10",
            formattedTime = "08:00",
            localDateTime = LocalDateTime.of(2024, 6, 10, 8, 0),
            equipmentName = equipmentName,
            equipmentId = equipmentId,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 10000.0,
            maxDisplacement = null,
            activeTimeSec = 3600L,
            totalTimeSec = 3600L,
            avgSpeedMps = 5.0,
            ascentMeters = 100L,
            descentMeters = 100L,
            minAltitude = 100.0,
            maxAltitude = 200.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    private val sampleWorkouts = listOf(
        // Bikes
        createWorkout(1L, 10L, "Road Bike", BSportType.BIKE, 101L, "Canyon Aeroad"),
        createWorkout(2L, 11L, "Gravel", BSportType.BIKE, 102L, "Rose Backroad"),
        createWorkout(3L, 10L, "Road Bike", BSportType.BIKE, 101L, "Canyon Aeroad"), // duplicate equipment
        // Shoes
        createWorkout(4L, 20L, "Road Running", BSportType.RUN, 201L, "Nike Pegasus"),
        createWorkout(5L, 21L, "Trail Running", BSportType.RUN, 202L, "Salomon Speedcross"),
        // Other (e.g. Ski / Swim)
        createWorkout(6L, 30L, "Cross Country Skiing", BSportType.UNKNOWN, 301L, "Fischer Skis"),
        // Workout without equipment
        createWorkout(7L, 20L, "Road Running", BSportType.RUN, 0L, null)
    )

    @Test
    fun testBikeTabIsolation() {
        val result = partitionAvailableEquipment(
            allWorkouts = sampleWorkouts,
            activeBSportType = BSportType.BIKE,
            localSportId = null
        )

        assertEquals("Should have 2 distinct bikes", 2, result.bikes.size)
        assertEquals("Bikes should be sorted by name", listOf("Canyon Aeroad", "Rose Backroad"), result.bikes.map { it.second })
        assertTrue("Shoes should be completely empty in Bike tab", result.shoes.isEmpty())
        assertTrue("Other equipment should be completely empty in Bike tab", result.other.isEmpty())
        assertEquals("All available equipment should match bikes", result.bikes, result.all)
    }

    @Test
    fun testRunTabIsolation() {
        val result = partitionAvailableEquipment(
            allWorkouts = sampleWorkouts,
            activeBSportType = BSportType.RUN,
            localSportId = null
        )

        assertTrue("Bikes should be completely empty in Run tab", result.bikes.isEmpty())
        assertEquals("Should have 2 distinct shoes", 2, result.shoes.size)
        assertEquals("Shoes should be sorted by name", listOf("Nike Pegasus", "Salomon Speedcross"), result.shoes.map { it.second })
        assertTrue("Other equipment should be completely empty in Run tab", result.other.isEmpty())
        assertEquals("All available equipment should match shoes", result.shoes, result.all)
    }

    @Test
    fun testOtherTabIsolation() {
        val result = partitionAvailableEquipment(
            allWorkouts = sampleWorkouts,
            activeBSportType = BSportType.UNKNOWN,
            localSportId = null
        )

        assertTrue("Bikes should be completely empty in Other tab", result.bikes.isEmpty())
        assertTrue("Shoes should be completely empty in Other tab", result.shoes.isEmpty())
        assertEquals("Should have 1 other equipment item", 1, result.other.size)
        assertEquals("Fischer Skis", result.other[0].second)
    }

    @Test
    fun testAllWorkoutsTabDistinctPartitioning() {
        val result = partitionAvailableEquipment(
            allWorkouts = sampleWorkouts,
            activeBSportType = null,
            localSportId = null
        )

        assertEquals("Should contain all bikes", 2, result.bikes.size)
        assertEquals("Should contain all shoes", 2, result.shoes.size)
        assertEquals("Should contain all other gear", 1, result.other.size)
        assertEquals("All combined list should have 5 items", 5, result.all.size)
    }

    @Test
    fun testSubSportFilteringWithinCategory() {
        // Filter specifically for "Road Bike" (sportId = 10)
        val result = partitionAvailableEquipment(
            allWorkouts = sampleWorkouts,
            activeBSportType = BSportType.BIKE,
            localSportId = 10L
        )

        assertEquals("Only equipment from Road Bike should be returned", 1, result.bikes.size)
        assertEquals("Canyon Aeroad", result.bikes[0].second)
        assertTrue("Shoes should remain empty", result.shoes.isEmpty())
    }

    @Test
    fun testStateClearingInvariantCheck() {
        // Given an active equipment ID corresponding to a bike (101L)
        val selectedEquipId = 101L

        // In Bike tab, 101L is present
        val bikeTabEquip = partitionAvailableEquipment(sampleWorkouts, BSportType.BIKE, null)
        assertTrue("Selected bike is present in bike tab", bikeTabEquip.all.any { it.first == selectedEquipId })

        // When switching to Run tab
        val runTabEquip = partitionAvailableEquipment(sampleWorkouts, BSportType.RUN, null)
        assertTrue("Selected bike is NOT present in run tab (must trigger state clearing)", runTabEquip.all.none { it.first == selectedEquipId })
    }
}
