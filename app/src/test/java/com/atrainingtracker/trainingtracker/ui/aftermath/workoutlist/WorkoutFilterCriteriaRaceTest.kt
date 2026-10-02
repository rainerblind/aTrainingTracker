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
 * Unit tests verifying WorkoutFilterCriteria matching, active filter count, and JSON serialization for Race workouts (REQ-UI-236, TST-UI-195.4).
 */
class WorkoutFilterCriteriaRaceTest {

    private fun createWorkout(id: Long, race: Boolean): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-10-02",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = -1L,
            commute = false,
            trainer = false,
            race = race,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 25000.0,
            maxDisplacement = null,
            activeTimeSec = 3600L,
            totalTimeSec = 3600L,
            avgSpeedMps = 6.94,
            ascentMeters = 200L,
            descentMeters = 200L,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = "Ride"
        )
    }

    @Test
    fun testMatches_isRaceTrue_filtersOnlyRaceWorkouts() {
        val filter = WorkoutFilterCriteria(isRace = true)
        val raceWorkout = createWorkout(1L, race = true)
        val normalWorkout = createWorkout(2L, race = false)

        assertTrue("Filter with isRace=true must match workout with race=true", filter.matches(raceWorkout))
        assertFalse("Filter with isRace=true must reject workout with race=false", filter.matches(normalWorkout))
    }

    @Test
    fun testMatches_isRaceFalse_filtersOnlyNonRaceWorkouts() {
        val filter = WorkoutFilterCriteria(isRace = false)
        val raceWorkout = createWorkout(1L, race = true)
        val normalWorkout = createWorkout(2L, race = false)

        assertFalse("Filter with isRace=false must reject workout with race=true", filter.matches(raceWorkout))
        assertTrue("Filter with isRace=false must match workout with race=false", filter.matches(normalWorkout))
    }

    @Test
    fun testMatches_isRaceNull_matchesAllWorkouts() {
        val filter = WorkoutFilterCriteria(isRace = null)
        val raceWorkout = createWorkout(1L, race = true)
        val normalWorkout = createWorkout(2L, race = false)

        assertTrue("Filter with isRace=null must match race workout", filter.matches(raceWorkout))
        assertTrue("Filter with isRace=null must match normal workout", filter.matches(normalWorkout))
    }

    @Test
    fun testActiveFilterCount_incrementsWhenIsRaceNotNull() {
        val emptyFilter = WorkoutFilterCriteria()
        assertEquals("Empty filter count must be 0", 0, emptyFilter.activeFilterCount)

        val raceFilter = WorkoutFilterCriteria(isRace = true)
        assertEquals("Filter with isRace=true must have count 1", 1, raceFilter.activeFilterCount)

        val nonRaceFilter = WorkoutFilterCriteria(isRace = false)
        assertEquals("Filter with isRace=false must have count 1", 1, nonRaceFilter.activeFilterCount)
    }

    @Test
    fun testJsonSerialization_roundtripsIsRaceCorrectly() {
        // Test isRace = true
        val criteriaTrue = WorkoutFilterCriteria(isRace = true)
        val jsonTrue = criteriaTrue.toJson()
        val deserializedTrue = WorkoutFilterCriteria.fromJson(jsonTrue)
        assertEquals(true, deserializedTrue.isRace)

        // Test isRace = false
        val criteriaFalse = WorkoutFilterCriteria(isRace = false)
        val jsonFalse = criteriaFalse.toJson()
        val deserializedFalse = WorkoutFilterCriteria.fromJson(jsonFalse)
        assertEquals(false, deserializedFalse.isRace)

        // Test isRace = null
        val criteriaNull = WorkoutFilterCriteria(isRace = null)
        val jsonNull = criteriaNull.toJson()
        val deserializedNull = WorkoutFilterCriteria.fromJson(jsonNull)
        assertNull(deserializedNull.isRace)
    }
}
