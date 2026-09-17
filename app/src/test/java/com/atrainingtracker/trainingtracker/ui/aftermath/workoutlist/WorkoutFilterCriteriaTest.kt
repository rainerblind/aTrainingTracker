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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying multi-dimensional predicate matching, active filter count derivation,
 * and JSON serialization/deserialization for [WorkoutFilterCriteria] (REQ-UI-132, TST-UI-085).
 */
class WorkoutFilterCriteriaTest {

    private fun createWorkout(
        id: Long = 1L,
        workoutName: String = "Sunday Morning Ride",
        sportId: Long = 10L,
        sportName: String = "Road Cycling",
        bSportType: BSportType = BSportType.BIKE,
        startTimeS: Long = 1718000000L, // 2024-06-10 06:13:20 UTC
        localDateTime: LocalDateTime = LocalDateTime.of(2024, 6, 10, 8, 13),
        equipmentId: Long = 101L,
        equipmentName: String? = "Canyon Aeroad",
        commute: Boolean = false,
        trainer: Boolean = false,
        mapPolyline: String = "polyline_coordinates_string",
        totalDistance: Double = 65000.0, // 65 km
        activeTimeSec: Long = 7200L, // 2 hours
        description: String? = "Hilly route through the Black Forest",
        goal: String? = "Threshold pace",
        method: String? = "Interval training"
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = workoutName,
            sportId = sportId,
            sportName = sportName,
            bSportType = bSportType,
            startTimeS = startTimeS,
            formattedDate = "2024-06-10",
            formattedTime = "08:13",
            localDateTime = localDateTime,
            equipmentName = equipmentName,
            equipmentId = equipmentId,
            commute = commute,
            trainer = trainer,
            mapPolyline = mapPolyline,
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = totalDistance,
            maxDisplacement = null,
            activeTimeSec = activeTimeSec,
            totalTimeSec = activeTimeSec,
            avgSpeedMps = totalDistance / activeTimeSec,
            ascentMeters = 800L,
            descentMeters = 800L,
            minAltitude = 200.0,
            maxAltitude = 900.0,
            description = description,
            goal = goal,
            method = method,
            stravaSportName = null
        )
    }

    @Test
    fun testEmptyCriteriaMatchesEverything() {
        val criteria = WorkoutFilterCriteria()
        val workout = createWorkout()
        assertTrue("Empty criteria should match any workout", criteria.matches(workout))
        assertTrue("Criteria should report isEmpty", criteria.isEmpty)
        assertFalse("Criteria should not report isNotEmpty", criteria.isNotEmpty)
        assertEquals("Active filter count should be 0", 0, criteria.activeFilterCount)
    }

    @Test
    fun testQueryMatchingAcrossFields() {
        val workout = createWorkout(
            workoutName = "Morning Interval Session",
            description = "High intensity efforts in the park",
            sportName = "Road Bike",
            equipmentName = "Specialized Tarmac"
        )

        // Matches workout name (case-insensitive)
        assertTrue(WorkoutFilterCriteria(query = "interval").matches(workout))
        assertTrue(WorkoutFilterCriteria(query = "MORNING").matches(workout))

        // Matches description
        assertTrue(WorkoutFilterCriteria(query = "park").matches(workout))

        // Matches sport name
        assertTrue(WorkoutFilterCriteria(query = "road").matches(workout))

        // Matches equipment name
        assertTrue(WorkoutFilterCriteria(query = "tarmac").matches(workout))

        // Does not match unrelated string
        assertFalse(WorkoutFilterCriteria(query = "swimming").matches(workout))
    }

    @Test
    fun testYearAndMonthFiltering() {
        val workout2024 = createWorkout(localDateTime = LocalDateTime.of(2024, 6, 15, 10, 0))

        assertTrue(WorkoutFilterCriteria(year = 2024).matches(workout2024))
        assertFalse(WorkoutFilterCriteria(year = 2025).matches(workout2024))

        assertTrue(WorkoutFilterCriteria(month = 6).matches(workout2024))
        assertFalse(WorkoutFilterCriteria(month = 7).matches(workout2024))

        assertTrue(WorkoutFilterCriteria(year = 2024, month = 6).matches(workout2024))
        assertFalse(WorkoutFilterCriteria(year = 2024, month = 5).matches(workout2024))
    }

    @Test
    fun testDateRangeFiltering() {
        val workout = createWorkout(startTimeS = 1000L)

        // Inside range
        assertTrue(WorkoutFilterCriteria(startDateS = 500L, endDateS = 1500L).matches(workout))
        assertTrue(WorkoutFilterCriteria(startDateS = 1000L, endDateS = 1000L).matches(workout))

        // Outside range (too early)
        assertFalse(WorkoutFilterCriteria(startDateS = 1001L).matches(workout))

        // Outside range (too late)
        assertFalse(WorkoutFilterCriteria(endDateS = 999L).matches(workout))
    }

    @Test
    fun testSportAndEquipmentFiltering() {
        val workout = createWorkout(sportId = 5L, equipmentId = 42L)

        assertTrue(WorkoutFilterCriteria(sportTypeId = 5L).matches(workout))
        assertFalse(WorkoutFilterCriteria(sportTypeId = 6L).matches(workout))

        assertTrue(WorkoutFilterCriteria(equipmentId = 42L).matches(workout))
        assertFalse(WorkoutFilterCriteria(equipmentId = 99L).matches(workout))
    }

    @Test
    fun testFlagFiltering_CommuteTrainerGps() {
        val outdoorCommute = createWorkout(commute = true, trainer = false, mapPolyline = "some_track")
        val indoorWorkout = createWorkout(commute = false, trainer = true, mapPolyline = "")

        // Commute checks
        assertTrue(WorkoutFilterCriteria(isCommute = true).matches(outdoorCommute))
        assertFalse(WorkoutFilterCriteria(isCommute = true).matches(indoorWorkout))
        assertTrue(WorkoutFilterCriteria(isCommute = false).matches(indoorWorkout))

        // Trainer checks
        assertTrue(WorkoutFilterCriteria(isTrainer = true).matches(indoorWorkout))
        assertFalse(WorkoutFilterCriteria(isTrainer = true).matches(outdoorCommute))
        assertTrue(WorkoutFilterCriteria(isTrainer = false).matches(outdoorCommute))

        // GPS track presence checks
        assertTrue(WorkoutFilterCriteria(hasGpsTrack = true).matches(outdoorCommute))
        assertFalse(WorkoutFilterCriteria(hasGpsTrack = true).matches(indoorWorkout))
    }

    @Test
    fun testDistanceAndDurationThresholds() {
        val workout = createWorkout(totalDistance = 50000.0, activeTimeSec = 3600L) // 50km, 60min

        // Distance threshold
        assertTrue(WorkoutFilterCriteria(minDistanceMeters = 40000.0).matches(workout))
        assertTrue(WorkoutFilterCriteria(minDistanceMeters = 50000.0).matches(workout))
        assertFalse(WorkoutFilterCriteria(minDistanceMeters = 50001.0).matches(workout))

        // Duration threshold
        assertTrue(WorkoutFilterCriteria(minDurationSec = 1800L).matches(workout))
        assertTrue(WorkoutFilterCriteria(minDurationSec = 3600L).matches(workout))
        assertFalse(WorkoutFilterCriteria(minDurationSec = 3601L).matches(workout))
    }

    @Test
    fun testDistanceIntervalFiltering() {
        val workout20k = createWorkout(totalDistance = 20000.0)
        val workout35k = createWorkout(totalDistance = 35000.0)
        val workout50k = createWorkout(totalDistance = 50000.0)
        val workout60k = createWorkout(totalDistance = 60000.0)
        val workout10k = createWorkout(totalDistance = 10000.0)

        // Bounded interval [20km, 50km]
        val boundedCriteria = WorkoutFilterCriteria(minDistanceMeters = 20000.0, maxDistanceMeters = 50000.0)
        assertTrue("20km workout should match [20km, 50km]", boundedCriteria.matches(workout20k))
        assertTrue("35km workout should match [20km, 50km]", boundedCriteria.matches(workout35k))
        assertTrue("50km workout should match [20km, 50km]", boundedCriteria.matches(workout50k))
        assertFalse("10km workout should be rejected by [20km, 50km]", boundedCriteria.matches(workout10k))
        assertFalse("60km workout should be rejected by [20km, 50km]", boundedCriteria.matches(workout60k))

        // Upper bound only: <= 50km
        val maxOnlyCriteria = WorkoutFilterCriteria(maxDistanceMeters = 50000.0)
        assertTrue(maxOnlyCriteria.matches(workout10k))
        assertTrue(maxOnlyCriteria.matches(workout20k))
        assertTrue(maxOnlyCriteria.matches(workout50k))
        assertFalse(maxOnlyCriteria.matches(workout60k))

        // Lower bound only: >= 35km
        val minOnlyCriteria = WorkoutFilterCriteria(minDistanceMeters = 35000.0)
        assertFalse(minOnlyCriteria.matches(workout10k))
        assertFalse(minOnlyCriteria.matches(workout20k))
        assertTrue(minOnlyCriteria.matches(workout35k))
        assertTrue(minOnlyCriteria.matches(workout60k))
    }

    @Test
    fun testDurationIntervalFiltering() {
        val workout30m = createWorkout(activeTimeSec = 1800L)
        val workout45m = createWorkout(activeTimeSec = 2700L)
        val workout60m = createWorkout(activeTimeSec = 3600L)
        val workout90m = createWorkout(activeTimeSec = 5400L)
        val workout15m = createWorkout(activeTimeSec = 900L)

        // Bounded interval [30min, 60min]
        val boundedCriteria = WorkoutFilterCriteria(minDurationSec = 1800L, maxDurationSec = 3600L)
        assertTrue("30m workout should match [30m, 60m]", boundedCriteria.matches(workout30m))
        assertTrue("45m workout should match [30m, 60m]", boundedCriteria.matches(workout45m))
        assertTrue("60m workout should match [30m, 60m]", boundedCriteria.matches(workout60m))
        assertFalse("15m workout should be rejected by [30m, 60m]", boundedCriteria.matches(workout15m))
        assertFalse("90m workout should be rejected by [30m, 60m]", boundedCriteria.matches(workout90m))

        // Upper bound only: <= 60min
        val maxOnlyCriteria = WorkoutFilterCriteria(maxDurationSec = 3600L)
        assertTrue(maxOnlyCriteria.matches(workout15m))
        assertTrue(maxOnlyCriteria.matches(workout30m))
        assertTrue(maxOnlyCriteria.matches(workout60m))
        assertFalse(maxOnlyCriteria.matches(workout90m))

        // Lower bound only: >= 45min
        val minOnlyCriteria = WorkoutFilterCriteria(minDurationSec = 2700L)
        assertFalse(minOnlyCriteria.matches(workout15m))
        assertFalse(minOnlyCriteria.matches(workout30m))
        assertTrue(minOnlyCriteria.matches(workout45m))
        assertTrue(minOnlyCriteria.matches(workout90m))
    }

    @Test
    fun testActiveFilterCountCalculation() {
        val c1 = WorkoutFilterCriteria()
        assertEquals(0, c1.activeFilterCount)

        val c2 = WorkoutFilterCriteria(query = "Gran Fondo")
        assertEquals(1, c2.activeFilterCount)

        // Distance range with both min and max counts as 1 dimension
        val cDistanceRange = WorkoutFilterCriteria(minDistanceMeters = 10000.0, maxDistanceMeters = 25000.0)
        assertEquals(1, cDistanceRange.activeFilterCount)

        // Duration range with both min and max counts as 1 dimension
        val cDurationRange = WorkoutFilterCriteria(minDurationSec = 1800L, maxDurationSec = 3600L)
        assertEquals(1, cDurationRange.activeFilterCount)

        val c3 = WorkoutFilterCriteria(
            query = "Gran Fondo",
            year = 2025,
            sportTypeId = 10L,
            equipmentId = 20L,
            isCommute = true,
            isTrainer = false,
            hasGpsTrack = true,
            minDistanceMeters = 50000.0,
            maxDistanceMeters = 100000.0,
            minDurationSec = 3600L,
            maxDurationSec = 7200L
        )
        // Dimensions: query (1), time (1), sport (1), equip (1), commute (1), trainer (1), gps (1), dist interval (1), dur interval (1) = 9
        assertEquals(9, c3.activeFilterCount)
    }

    @Test
    fun testJsonSerializationRoundTrip() {
        val original = WorkoutFilterCriteria(
            query = "Alps Climb",
            year = 2024,
            month = 7,
            startDateS = 1720000000L,
            endDateS = 1721000000L,
            sportTypeId = 15L,
            equipmentId = 88L,
            isCommute = false,
            isTrainer = true,
            hasGpsTrack = true,
            minDistanceMeters = 75000.0,
            maxDistanceMeters = 120000.0,
            minDurationSec = 7200L,
            maxDurationSec = 14400L
        )

        val json = original.toJson()
        val restored = WorkoutFilterCriteria.fromJson(json)

        assertEquals("Query must match", original.query, restored.query)
        assertEquals("Year must match", original.year, restored.year)
        assertEquals("Month must match", original.month, restored.month)
        assertEquals("StartDateS must match", original.startDateS, restored.startDateS)
        assertEquals("EndDateS must match", original.endDateS, restored.endDateS)
        assertEquals("SportTypeId must match", original.sportTypeId, restored.sportTypeId)
        assertEquals("EquipmentId must match", original.equipmentId, restored.equipmentId)
        assertEquals("IsCommute must match", original.isCommute, restored.isCommute)
        assertEquals("IsTrainer must match", original.isTrainer, restored.isTrainer)
        assertEquals("HasGpsTrack must match", original.hasGpsTrack, restored.hasGpsTrack)
        assertEquals("MinDistanceMeters must match", original.minDistanceMeters, restored.minDistanceMeters)
        assertEquals("MaxDistanceMeters must match", original.maxDistanceMeters, restored.maxDistanceMeters)
        assertEquals("MinDurationSec must match", original.minDurationSec, restored.minDurationSec)
        assertEquals("MaxDurationSec must match", original.maxDurationSec, restored.maxDurationSec)
    }

    @Test
    fun testJsonDeserialization_LegacyJsonCompatibility() {
        // Legacy JSON without maxDistanceMeters or maxDurationSec
        val legacyJson = """{"query":"Trail Run","minDistanceMeters":5000.0,"minDurationSec":1800}"""
        val parsed = WorkoutFilterCriteria.fromJson(legacyJson)

        assertEquals("Trail Run", parsed.query)
        assertEquals(5000.0, parsed.minDistanceMeters)
        assertEquals(null, parsed.maxDistanceMeters)
        assertEquals(1800L, parsed.minDurationSec)
        assertEquals(null, parsed.maxDurationSec)
        assertEquals(3, parsed.activeFilterCount)
    }

    @Test
    fun testTabAwareSportsSelectionLogic() {
        val workouts = listOf(
            createWorkout(id = 1L, sportId = 1L, sportName = "Road Bike", bSportType = BSportType.BIKE),
            createWorkout(id = 2L, sportId = 2L, sportName = "Mountain Bike", bSportType = BSportType.BIKE),
            createWorkout(id = 3L, sportId = 3L, sportName = "Running", bSportType = BSportType.RUN),
            createWorkout(id = 4L, sportId = 4L, sportName = "Trail Running", bSportType = BSportType.RUN),
            createWorkout(id = 5L, sportId = 5L, sportName = "Swimming", bSportType = BSportType.UNKNOWN),
            createWorkout(id = 6L, sportId = 6L, sportName = "Strength", bSportType = BSportType.UNKNOWN)
        )

        fun getAvailableSports(activeBSportType: BSportType?) = workouts
            .filter { activeBSportType == null || it.bSportType == activeBSportType }
            .map { it.sportId to it.sportName }
            .distinctBy { it.first }
            .sortedBy { it.second }

        // Bike Tab
        val bikeSports = getAvailableSports(BSportType.BIKE).map { it.second }
        assertEquals(listOf("Mountain Bike", "Road Bike"), bikeSports)

        // Run Tab
        val runSports = getAvailableSports(BSportType.RUN).map { it.second }
        assertEquals(listOf("Running", "Trail Running"), runSports)

        // Other Tab
        val otherSports = getAvailableSports(BSportType.UNKNOWN).map { it.second }
        assertEquals(listOf("Strength", "Swimming"), otherSports)

        // All Tab
        val allSports = getAvailableSports(null).map { it.second }
        assertEquals(listOf("Mountain Bike", "Road Bike", "Running", "Strength", "Swimming", "Trail Running"), allSports)
    }

    @Test
    fun testJsonDeserialization_HandlesNullOrMalformedSafely() {
        val empty1 = WorkoutFilterCriteria.fromJson(null)
        assertTrue(empty1.isEmpty)

        val empty2 = WorkoutFilterCriteria.fromJson("")
        assertTrue(empty2.isEmpty)

        val empty3 = WorkoutFilterCriteria.fromJson("{ invalid json")
        assertTrue(empty3.isEmpty)
    }
}
