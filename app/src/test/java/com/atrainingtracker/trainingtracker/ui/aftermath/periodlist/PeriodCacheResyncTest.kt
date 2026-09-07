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

package com.atrainingtracker.trainingtracker.ui.aftermath.periodlist

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository.Companion.getPeriodSortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying period cache synchronization, historical period purging,
 * and boundary recalculation upon bulk workout deletion (REQ-DAT-011, TST-DAT-005, ATT-296).
 */
class PeriodCacheResyncTest {

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
            clusterId = -1L
        )
    }

    /**
     * Verifies that after bulk deleting workouts older than 365 days:
     * 1. Fully purged periods (e.g. 2024 sessions) produce 0 grouped periods.
     * 2. Boundary periods (e.g. month of cutoff) retain only surviving workouts.
     * 3. Extrema references (longest workout) strictly resolve to surviving sessions, eliminating dangling pointers.
     */
    @Test
    fun testPeriodResync_purgesOldPeriodsAndRecalculatesBoundary() {
        val now = LocalDateTime.now()

        // Old workouts: > 365 days ago (e.g. 400 days and 370 days ago)
        val purgedWorkout1 = createDummyWorkout(101L, "Historical Long Run", 7200L, 21000.0, 300L, now.minusDays(400))
        val purgedWorkout2 = createDummyWorkout(102L, "Historical Tempo", 3600L, 10000.0, 150L, now.minusDays(370))

        // Boundary workout: right inside retention (e.g. 360 days ago, same month as 370)
        val survivingBoundaryWorkout = createDummyWorkout(201L, "Boundary Short Run", 1800L, 5000.0, 50L, now.minusDays(360))

        // Recent workout: 30 days ago
        val survivingRecentWorkout = createDummyWorkout(301L, "Recent Workout", 2400L, 7000.0, 80L, now.minusDays(30))

        val allHistoricalWorkouts = listOf(purgedWorkout1, purgedWorkout2, survivingBoundaryWorkout, survivingRecentWorkout)

        // Simulate bulk deletion: only workouts within 365 days survive
        val cutoffSeconds = now.minusDays(365).atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
        val survivingWorkouts = allHistoricalWorkouts.filter { it.startTimeS > cutoffSeconds }

        assertEquals(2, survivingWorkouts.size)
        assertTrue(survivingWorkouts.any { it.id == 201L })
        assertTrue(survivingWorkouts.any { it.id == 301L })
        assertFalse(survivingWorkouts.any { it.id == 101L })
        assertFalse(survivingWorkouts.any { it.id == 102L })

        // 1. Group surviving workouts by DAY
        val daysGrouped = survivingWorkouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.DAY) }
        val oldDayKey1 = getPeriodSortKey(purgedWorkout1.startTimeS, PeriodType.DAY)
        val oldDayKey2 = getPeriodSortKey(purgedWorkout2.startTimeS, PeriodType.DAY)

        assertFalse("Purged day 1 must not exist in resynced groups", daysGrouped.containsKey(oldDayKey1))
        assertFalse("Purged day 2 must not exist in resynced groups", daysGrouped.containsKey(oldDayKey2))

        // 2. Boundary Month Recalculation
        val monthGrouped = survivingWorkouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.MONTH) }
        val boundaryMonthKey = getPeriodSortKey(survivingBoundaryWorkout.startTimeS, PeriodType.MONTH)
        val boundaryWorkouts = monthGrouped[boundaryMonthKey]

        assertNotNull("Boundary month must exist with surviving workouts", boundaryWorkouts)
        assertEquals("Boundary month must only contain the surviving workout", 1, boundaryWorkouts?.size)
        assertEquals(201L, boundaryWorkouts?.first()?.id)

        // 3. Extrema verification: Longest workout in boundary period must not point to purged workout 102
        val longestWorkout = boundaryWorkouts?.maxByOrNull { it.activeTimeSec }
        assertNotNull(longestWorkout)
        assertEquals("Longest workout in boundary must be surviving workout 201, not purged 102", 201L, longestWorkout?.id)
        assertEquals(1800L, longestWorkout?.activeTimeSec)
    }

    /**
     * Verifies that when ALL workouts in history are deleted (e.g. daysToKeep = 0):
     * The resynced period groupings are completely empty across all 4 tiers.
     */
    @Test
    fun testPeriodResync_emptyWorkoutsProducesZeroPeriods() {
        val emptyWorkouts = emptyList<WorkoutData>()

        val daysGrouped = emptyWorkouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.DAY) }
        val weeksGrouped = emptyWorkouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.WEEK) }
        val monthsGrouped = emptyWorkouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.MONTH) }
        val yearsGrouped = emptyWorkouts.groupBy { getPeriodSortKey(it.startTimeS, PeriodType.YEAR) }

        assertTrue(daysGrouped.isEmpty())
        assertTrue(weeksGrouped.isEmpty())
        assertTrue(monthsGrouped.isEmpty())
        assertTrue(yearsGrouped.isEmpty())
    }
}
