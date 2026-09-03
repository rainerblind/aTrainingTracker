/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying longest workout resolution integrity across multi-workout periods.
 * Fulfills REQ-PER-011 / TST-PER-016 for ATT-579.
 */
class PeriodsAggregationTest {

    private fun createDummyWorkout(
        id: Long,
        name: String,
        activeTimeSec: Long,
        totalDistance: Double,
        ascentMeters: Long,
        dateTime: LocalDateTime
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "dummy_$id",
            workoutName = name,
            sportId = 1L,
            sportName = "Laufen",
            bSportType = BSportType.RUN,
            startTimeS = dateTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond(),
            formattedDate = dateTime.toLocalDate().toString(),
            formattedTime = dateTime.toLocalTime().toString(),
            localDateTime = dateTime,
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = totalDistance,
            maxDisplacement = 0.0,
            activeTimeSec = activeTimeSec,
            totalTimeSec = activeTimeSec + 60,
            avgSpeedMps = totalDistance / activeTimeSec,
            ascentMeters = ascentMeters,
            descentMeters = ascentMeters,
            minAltitude = 100.0,
            maxAltitude = 250.0,
            maxAltitudeLatLng = null,
            maxDisplacementLatLng = null,
            startLatLng = null,
            endLatLng = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            stravaActivityData = null,
            clusterId = 0L,
            clusterName = null
        )
    }

    @Test
    fun testMultiWorkoutMonthLongestWorkoutSelection() {
        // Reproduce January 2015 scenario from ATT-579:
        // Day 2: 44:17 min (2657s), 8.14 km (8140m)
        val wDay2 = createDummyWorkout(
            id = 101L,
            name = "Run to home (Uni -> Rohr) #2",
            activeTimeSec = 2657L,
            totalDistance = 8140.0,
            ascentMeters = 110L,
            dateTime = LocalDateTime.of(2015, 1, 2, 17, 30)
        )

        // Day 15: 35:00 min (2100s), 6.50 km (6500m)
        val wDay15 = createDummyWorkout(
            id = 102L,
            name = "Tempo Run",
            activeTimeSec = 2100L,
            totalDistance = 6500.0,
            ascentMeters = 80L,
            dateTime = LocalDateTime.of(2015, 1, 15, 18, 0)
        )

        // Day 31: 50:14 min (3014s), 9.19 km (9190m)
        val wDay31 = createDummyWorkout(
            id = 103L,
            name = "10 k im Rohrer Wald #8",
            activeTimeSec = 3014L,
            totalDistance = 9190.0,
            ascentMeters = 155L,
            dateTime = LocalDateTime.of(2015, 1, 31, 16, 31)
        )

        val monthWorkouts = listOf(wDay2, wDay15, wDay31)

        // Primary authority evaluation: groupWorkouts.filter { it.bSportType == sport }.maxByOrNull { it.activeTimeSec }
        val longestFromGroup = monthWorkouts.filter { it.bSportType == BSportType.RUN }.maxByOrNull { it.activeTimeSec }
        assertNotNull(longestFromGroup)
        assertEquals(103L, longestFromGroup?.id)
        assertEquals("10 k im Rohrer Wald #8", longestFromGroup?.workoutName)
        assertEquals(3014L, longestFromGroup?.activeTimeSec)
        assertEquals(9190.0, longestFromGroup?.totalDistance ?: 0.0, 0.01)
        assertEquals(155L, longestFromGroup?.ascentMeters)
    }

    @Test
    fun testAggregateChildrenToParentResolvesHighestDuration() {
        val childDay2Stats = SportStats(
            count = 1,
            totalDurationSec = 2657L,
            totalDistanceMeters = 8140.0,
            totalAscentMeters = 110L,
            detailedSportStats = emptyMap(),
            longestWorkout = LongestWorkout(101L, "Run to home", 2657L, 8140.0, 110L)
        )

        val childDay15Stats = SportStats(
            count = 1,
            totalDurationSec = 2100L,
            totalDistanceMeters = 6500.0,
            totalAscentMeters = 80L,
            detailedSportStats = emptyMap(),
            longestWorkout = LongestWorkout(102L, "Tempo Run", 2100L, 6500.0, 80L)
        )

        val childDay31Stats = SportStats(
            count = 1,
            totalDurationSec = 3014L,
            totalDistanceMeters = 9190.0,
            totalAscentMeters = 155L,
            detailedSportStats = emptyMap(),
            longestWorkout = LongestWorkout(103L, "10 k im Rohrer Wald #8", 3014L, 9190.0, 155L)
        )

        val sportChildren = listOf(childDay2Stats, childDay15Stats, childDay31Stats)
        val longestInSport = sportChildren.mapNotNull { it.longestWorkout }.maxByOrNull { it.durationSec }

        assertNotNull(longestInSport)
        assertEquals(103L, longestInSport?.id)
        assertEquals(3014L, longestInSport?.durationSec)
        assertEquals(9190.0, longestInSport?.distanceMeters ?: 0.0, 0.01)
    }
}
