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
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying [WorkoutFilterCriteria] filtering, count, JSON roundtrip,
 * [WorkoutData.headerData] propagation, and theme token resolution for Epic workouts
 * (`REQ-REV-018`, `TST-REV-019`, `ATT-9022`).
 */
class WorkoutFilterCriteriaEpicTest {

    private fun createWorkout(
        id: Long,
        race: Boolean = false,
        epic: Boolean = false,
        commute: Boolean = false,
        trainer: Boolean = false
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = true,
            fileBaseName = "workout_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-11-24",
            formattedTime = "07:30",
            localDateTime = LocalDateTime.of(2026, 11, 24, 7, 30),
            equipmentName = null,
            equipmentId = -1L,
            commute = commute,
            trainer = trainer,
            race = race,
            epic = epic,
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
    fun matches_isEpicTrue_filtersOnlyEpicWorkouts() {
        val filter = WorkoutFilterCriteria(isEpic = true)
        val epicWorkout = createWorkout(1L, epic = true)
        val epicRaceWorkout = createWorkout(2L, race = true, epic = true)
        val raceOnlyWorkout = createWorkout(3L, race = true, epic = false)
        val normalWorkout = createWorkout(4L, epic = false)

        assertTrue("isEpic=true must match epic workout", filter.matches(epicWorkout))
        assertTrue("isEpic=true must match workout with both race & epic", filter.matches(epicRaceWorkout))
        assertFalse("isEpic=true must reject race-only workout", filter.matches(raceOnlyWorkout))
        assertFalse("isEpic=true must reject normal workout", filter.matches(normalWorkout))
    }

    @Test
    fun matches_isEpicFalse_filtersOnlyNonEpicWorkouts() {
        val filter = WorkoutFilterCriteria(isEpic = false)
        val epicWorkout = createWorkout(1L, epic = true)
        val normalWorkout = createWorkout(2L, epic = false)

        assertFalse("isEpic=false must reject epic workout", filter.matches(epicWorkout))
        assertTrue("isEpic=false must match non-epic workout", filter.matches(normalWorkout))
    }

    @Test
    fun matches_isEpicNull_matchesAllWorkouts() {
        val filter = WorkoutFilterCriteria(isEpic = null)
        assertTrue(filter.matches(createWorkout(1L, epic = true)))
        assertTrue(filter.matches(createWorkout(2L, epic = false)))
    }

    @Test
    fun matches_combinedRaceAndEpic_requiresBothSpecialFlags() {
        val filter = WorkoutFilterCriteria(isRace = true, isEpic = true)
        val both = createWorkout(1L, race = true, epic = true)
        val onlyRace = createWorkout(2L, race = true, epic = false)
        val onlyEpic = createWorkout(3L, race = false, epic = true)

        assertTrue("Combined isRace & isEpic filter must match workout with both flags", filter.matches(both))
        assertFalse("Combined filter must reject race-only workout", filter.matches(onlyRace))
        assertFalse("Combined filter must reject epic-only workout", filter.matches(onlyEpic))
    }

    @Test
    fun activeFilterCount_countsIsEpicIndependently() {
        assertEquals(0, WorkoutFilterCriteria().activeFilterCount)
        assertEquals(1, WorkoutFilterCriteria(isEpic = true).activeFilterCount)
        assertEquals(1, WorkoutFilterCriteria(isEpic = false).activeFilterCount)
        assertEquals(2, WorkoutFilterCriteria(isRace = true, isEpic = true).activeFilterCount)
    }

    @Test
    fun jsonSerialization_roundtripsIsEpicAndHandlesLegacyJson() {
        val trueCriteria = WorkoutFilterCriteria(isEpic = true)
        assertEquals(true, WorkoutFilterCriteria.fromJson(trueCriteria.toJson()).isEpic)

        val falseCriteria = WorkoutFilterCriteria(isEpic = false)
        assertEquals(false, WorkoutFilterCriteria.fromJson(falseCriteria.toJson()).isEpic)

        val nullCriteria = WorkoutFilterCriteria(isEpic = null)
        assertNull(WorkoutFilterCriteria.fromJson(nullCriteria.toJson()).isEpic)

        // Legacy persisted filter JSON predating ATT-9022 has no "isEpic" key
        val legacy = WorkoutFilterCriteria.fromJson("""{"isRace":true}""")
        assertEquals(true, legacy.isRace)
        assertNull(legacy.isEpic)
    }

    @Test
    fun workoutData_headerData_propagatesEpicFlag() {
        val epicWorkout = createWorkout(1L, race = true, epic = true)
        assertTrue("WorkoutData.headerData must propagate epic=true", epicWorkout.headerData.epic)
        assertTrue("WorkoutData.headerData must propagate race=true", epicWorkout.headerData.race)

        val regularWorkout = createWorkout(2L, race = false, epic = false)
        assertFalse("WorkoutData.headerData must propagate epic=false", regularWorkout.headerData.epic)
    }

    @Test
    fun epicBadgeColors_resolveDistinctLightAndDarkTokens() {
        val light = TTColor.resolveEpicBadgeColors(isDark = false)
        val dark = TTColor.resolveEpicBadgeColors(isDark = true)

        assertNotEquals("Light & Dark epic container colors must differ", light.container, dark.container)
        assertNotEquals("Light & Dark epic text colors must differ", light.onContainer, dark.onContainer)
        assertNotEquals("Light & Dark epic accent colors must differ", light.accent, dark.accent)
        assertNotEquals("Light container and onContainer must contrast", light.container, light.onContainer)
        assertNotEquals("Dark container and onContainer must contrast", dark.container, dark.onContainer)
    }
}
