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
 */

package com.atrainingtracker.trainingtracker.database

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class WorkoutClusterCorridorDetectorTest {

    private fun createDummyWorkout(id: Long, name: String, distance: Double, polyline: String): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = name,
            sportId = 1L,
            sportName = "Biking",
            bSportType = BSportType.BIKE,
            startTimeS = 1000L * id,
            formattedDate = "2026-11-08",
            formattedTime = "10:00",
            localDateTime = java.time.LocalDateTime.now(),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = polyline,
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = distance,
            maxDisplacement = distance / 2.0,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = distance / 1800.0,
            ascentMeters = 0L,
            descentMeters = 0L,
            minAltitude = null,
            maxAltitude = null,
            startLatLng = LatLng(48.7000, 9.0000),
            endLatLng = LatLng(48.7000, 9.1000),
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun testSamplePolylineStations_producesFiveStations() {
        val path = listOf(
            LatLng(48.7000, 9.0000),
            LatLng(48.7000, 9.0500),
            LatLng(48.7000, 9.1000)
        )

        val stations = WorkoutClusterCorridorDetector.samplePolylineStations(path)
        assertEquals(5, stations.size)
        // Stations should be strictly ordered in longitude from 9.0000 to 9.1000
        for (i in 0 until stations.size - 1) {
            assertTrue(stations[i].longitude < stations[i + 1].longitude)
        }
    }

    @Test
    fun testComputeCorridorDistance_identicalPathsYieldZeroDeviation() {
        val path = listOf(
            LatLng(48.7000, 9.0000),
            LatLng(48.7050, 9.0500),
            LatLng(48.7000, 9.1000)
        )

        val stations = WorkoutClusterCorridorDetector.samplePolylineStations(path)
        val deviation = WorkoutClusterCorridorDetector.computeCorridorDistance(stations, stations)
        assertEquals(0.0, deviation, 0.001)
    }

    @Test
    fun testDetectCorridors_splitsDivergingCorridorsAt500mThreshold() {
        // Path A: straight west-to-east
        val pathA = listOf(
            LatLng(48.7000, 9.0000),
            LatLng(48.7000, 9.0500),
            LatLng(48.7000, 9.1000)
        )

        // Path B: northern detour diverging by ~1100m (0.01 deg latitude ~ 1110m)
        val pathB = listOf(
            LatLng(48.7000, 9.0000),
            LatLng(48.7100, 9.0500),
            LatLng(48.7000, 9.1000)
        )

        val w1 = createDummyWorkout(1L, "Commute 1", 10000.0, "")
        val w2 = createDummyWorkout(2L, "Commute 2", 10050.0, "")
        val w3 = createDummyWorkout(3L, "Commute 3", 10800.0, "")

        val decodedMap = mapOf(
            1L to pathA,
            2L to pathA,
            3L to pathB
        )

        // At 500m threshold: Path A (w1, w2) and Path B (w3) should split into 2 groups
        val variants500 = WorkoutClusterCorridorDetector.detectCorridors(
            workouts = listOf(w1, w2, w3),
            baseClusterName = "Commute",
            corridorThresholdMeters = 500.0,
            decodedPaths = decodedMap
        )

        assertEquals(2, variants500.size)
        assertTrue(variants500[0].isPrimary)
        assertEquals(2, variants500[0].workoutIds.size)
        assertTrue(variants500[0].workoutIds.containsAll(listOf(1L, 2L)))
        assertEquals("Commute", variants500[0].suggestedName)

        assertFalse(variants500[1].isPrimary)
        assertEquals(1, variants500[1].workoutIds.size)
        assertTrue(variants500[1].workoutIds.contains(3L))
        assertEquals("Commute var 2", variants500[1].suggestedName)

        // At 1500m threshold: Path A and Path B should merge into 1 group
        val variants1500 = WorkoutClusterCorridorDetector.detectCorridors(
            workouts = listOf(w1, w2, w3),
            baseClusterName = "Commute",
            corridorThresholdMeters = 1500.0,
            decodedPaths = decodedMap
        )

        assertEquals(1, variants1500.size)
        assertEquals(3, variants1500[0].workoutIds.size)
    }

    @Test
    fun testDetectCorridors_handlesEmptyAndSingleWorkoutGracefully() {
        val emptyResult = WorkoutClusterCorridorDetector.detectCorridors(
            workouts = emptyList(),
            baseClusterName = "Test"
        )
        assertTrue(emptyResult.isEmpty())

        val w1 = createDummyWorkout(1L, "Single", 5000.0, "")
        val singleResult = WorkoutClusterCorridorDetector.detectCorridors(
            workouts = listOf(w1),
            baseClusterName = "Single Cluster"
        )
        assertEquals(1, singleResult.size)
        assertTrue(singleResult[0].isPrimary)
        assertEquals("Single Cluster", singleResult[0].suggestedName)
    }
}
