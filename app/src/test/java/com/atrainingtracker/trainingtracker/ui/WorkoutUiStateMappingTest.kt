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

package com.atrainingtracker.trainingtracker.ui

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.EquipmentData
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutCardUiState
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaUiState
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.LapRowUiState
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsHelper
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import com.atrainingtracker.trainingtracker.ui.util.MetricFormatterContext
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite verifying UI State models and transformation logic (TST-UI-506, REQ-UI-506).
 */
class WorkoutUiStateMappingTest {

    // --- TST-UI-506.1: LapRowUiState Mapping & Badge Determination ---

    @Test
    fun testBuildLapRowUiStates_cyclingSport_formatsSpeedAndAwardsBadges() {
        val laps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 300, distanceTotalM = 2000.0, speedAverageMps = 6.666),
            LapData(id = 2, workoutId = 10, lapNr = 2, timeTotalS = 240, distanceTotalM = 2000.0, speedAverageMps = 8.333),
            LapData(id = 3, workoutId = 10, lapNr = 3, timeTotalS = 400, distanceTotalM = 2000.0, speedAverageMps = 5.0)
        )

        val states = WorkoutLapsHelper.buildLapRowUiStates(
            laps = laps,
            bSportType = BSportType.BIKE,
            timeFormatter = { "${it}s" },
            distanceFormatter = { "${it.toInt()}m" },
            paceFormatter = { "PACE" },
            speedFormatter = { String.format(java.util.Locale.US, "%.1f km/h", it * 3.6) },
            fastestBadgeDescription = "Schnellste Runde",
            slowestBadgeDescription = "Langsamste Runde"
        )

        assertEquals(3, states.size)

        // Lap 1: Normal
        assertEquals(1L, states[0].lapNr)
        assertEquals("Lap 1", states[0].displayName)
        assertEquals("300s", states[0].timeFormatted)
        assertEquals("2000m", states[0].distanceFormatted)
        assertNull(states[0].badgeEmoji)
        assertNull(states[0].badgeDescription)

        // Lap 2: Fastest -> 🐇
        assertEquals("🐇", states[1].badgeEmoji)
        assertEquals("Schnellste Runde", states[1].badgeDescription)
        assertEquals("30.0 km/h", states[1].speedPaceFormatted)

        // Lap 3: Slowest -> 🦔
        assertEquals("🦔", states[2].badgeEmoji)
        assertEquals("Langsamste Runde", states[2].badgeDescription)
        assertEquals("18.0 km/h", states[2].speedPaceFormatted)
    }

    @Test
    fun testBuildLapRowUiStates_runningSport_formatsPace() {
        val laps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.333)
        )

        val states = WorkoutLapsHelper.buildLapRowUiStates(
            laps = laps,
            bSportType = BSportType.RUN,
            timeFormatter = { "05:00" },
            distanceFormatter = { "1.00 km" },
            paceFormatter = { "5:00" },
            speedFormatter = { "12.0" }
        )

        assertEquals(1, states.size)
        assertEquals("5:00", states[0].speedPaceFormatted)
        assertNull("Single lap should not have badge", states[0].badgeEmoji)
    }

    @Test
    fun testBuildLapRowUiStates_zeroSpeedLap_resolvesDash() {
        val laps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 60, distanceTotalM = 0.0, speedAverageMps = 0.0)
        )

        val states = WorkoutLapsHelper.buildLapRowUiStates(
            laps = laps,
            bSportType = BSportType.RUN,
            timeFormatter = { "01:00" },
            distanceFormatter = { "0 m" },
            paceFormatter = { "PACE" },
            speedFormatter = { "SPEED" }
        )

        assertEquals(1, states.size)
        assertEquals("--", states[0].speedPaceFormatted)
    }

    @Test
    fun testBuildLapRowUiStates_customDescriptionAndClick() {
        var clicked = false
        val laps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0, description = "Hill rep")
        )

        val states = WorkoutLapsHelper.buildLapRowUiStates(
            laps = laps,
            bSportType = BSportType.RUN,
            formatters = MetricFormatterContext(),
            onLapClick = { clicked = true }
        )

        assertEquals("Hill rep", states[0].description)
        assertNotNull(states[0].onClick)
        states[0].onClick!!.invoke()
        assertTrue(clicked)
    }

    private fun createWorkoutData(
        id: Long = 1L,
        workoutName: String = "Test Workout",
        finished: Boolean = true,
        bSportType: BSportType = BSportType.RUN,
        sportName: String = "Running",
        equipmentName: String? = null,
        formattedDate: String = "10.11.2026",
        formattedTime: String = "10:00",
        totalDistance: Double = 5000.0,
        activeTimeSec: Long = 1800L,
        avgSpeedMps: Double = 2.78,
        ascentMeters: Long = 50L
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = "workout_$id",
            workoutName = workoutName,
            sportId = 1L,
            sportName = sportName,
            bSportType = bSportType,
            startTimeS = 1718000000L,
            formattedDate = formattedDate,
            formattedTime = formattedTime,
            localDateTime = java.time.LocalDateTime.of(2026, 11, 10, 10, 0),
            equipmentName = equipmentName,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = totalDistance,
            maxDisplacement = null,
            activeTimeSec = activeTimeSec,
            totalTimeSec = activeTimeSec,
            avgSpeedMps = avgSpeedMps,
            ascentMeters = ascentMeters,
            descentMeters = 50L,
            minAltitude = 200.0,
            maxAltitude = 250.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    // --- TST-UI-506.2: WorkoutCardUiState Transformation ---

    @Test
    fun testWorkoutCardUiState_fromWorkoutData_finishedWorkout() {
        val workoutData = createWorkoutData(
            id = 42L,
            workoutName = "Afternoon Ride",
            bSportType = BSportType.BIKE,
            sportName = "Cycling",
            equipmentName = "Trek Domane",
            formattedDate = "10.11.2026",
            formattedTime = "14:30",
            finished = true,
            totalDistance = 30000.0,
            activeTimeSec = 3600L,
            avgSpeedMps = 8.333,
            ascentMeters = 250L
        )

        val state = WorkoutCardUiState.from(
            workoutData = workoutData,
            formatters = MetricFormatterContext(),
            isActivelyTracked = false
        )

        assertEquals(42L, state.workoutId)
        assertEquals("Afternoon Ride", state.workoutName)
        assertEquals("Cycling", state.sportName)
        assertEquals("Trek Domane", state.equipmentName)
        assertEquals("10.11.2026  14:30", state.formattedDateTime)
        assertTrue(state.isFinished)
        assertFalse(state.isActivelyTracked)
        assertEquals(TTAlpha.High, state.contentAlpha, 0.01f)
        assertTrue(state.canDelete)
        assertFalse("Finished workout cannot mark finished again", state.canMarkFinished)
        assertTrue(state.hasContextMenu)
    }

    @Test
    fun testWorkoutCardUiState_unfinishedWorkout_reducesAlphaAndEnablesMarkFinished() {
        val workoutData = createWorkoutData(
            id = 43L,
            workoutName = "Unfinished Workout",
            finished = false
        )

        val state = WorkoutCardUiState.from(
            workoutData = workoutData,
            isActivelyTracked = false
        )

        assertFalse(state.isFinished)
        assertEquals(0.5f, state.contentAlpha, 0.01f)
        assertTrue("Unfinished workout can be marked as finished", state.canMarkFinished)
        assertTrue(state.canDelete)
    }

    @Test
    fun testWorkoutCardUiState_activelyTracked_disablesContextMenuActions() {
        val workoutData = createWorkoutData(
            id = 44L,
            workoutName = "Live Workout",
            finished = false
        )

        val state = WorkoutCardUiState.from(
            workoutData = workoutData,
            isActivelyTracked = true
        )

        assertTrue(state.isActivelyTracked)
        assertFalse("Actively tracked workout cannot be deleted", state.canDelete)
        assertFalse("Actively tracked workout cannot be manually marked finished from list", state.canMarkFinished)
        assertFalse("No menu options available when actively tracked", state.hasContextMenu)
    }

    // --- TST-UI-506.3: ExtremaUiState Transformation ---

    @Test
    fun testExtremaUiState_fromExtremaData_resolvesNullsAndMinRelevance() {
        val extremaData = ExtremaData(
            workoutId = 99L,
            dataRows = listOf(
                ExtremaDataRow(
                    sensorLabel = "Power",
                    unitLabel = "W",
                    minValue = "0",
                    avgValue = "220",
                    maxValue = "650",
                    isMinRelevant = false,
                    boldAvg = true,
                    boldMax = true
                ),
                ExtremaDataRow(
                    sensorLabel = "Heart Rate",
                    unitLabel = "bpm",
                    minValue = "60",
                    avgValue = "140",
                    maxValue = "180",
                    isMinRelevant = true,
                    boldAvg = true,
                    boldMax = true
                ),
                ExtremaDataRow(
                    sensorLabel = "Altitude",
                    unitLabel = "m",
                    minValue = null,
                    avgValue = "300",
                    maxValue = null,
                    isMinRelevant = true
                ),
                ExtremaDataRow(
                    sensorLabel = "Empty Sensor",
                    unitLabel = "x",
                    minValue = null,
                    avgValue = null,
                    maxValue = null
                )
            )
        )

        val state = ExtremaUiState.from(extremaData)

        assertEquals(99L, state.workoutId)
        // 4th row has no data, so it must be filtered out
        assertEquals(3, state.rows.size)

        // Row 1: Power (isMinRelevant = false -> min is "-")
        assertEquals("Power", state.rows[0].label)
        assertEquals("-", state.rows[0].min)
        assertEquals("220", state.rows[0].avg)
        assertEquals("650", state.rows[0].max)
        assertEquals("W", state.rows[0].unit)
        assertTrue(state.rows[0].boldAvg)
        assertTrue(state.rows[0].boldMax)
        assertFalse(state.rows[0].boldMin)

        // Row 2: Heart Rate (isMinRelevant = true -> min is "60")
        assertEquals("Heart Rate", state.rows[1].label)
        assertEquals("60", state.rows[1].min)
        assertEquals("140", state.rows[1].avg)
        assertEquals("180", state.rows[1].max)

        // Row 3: Altitude (min and max null -> "-")
        assertEquals("Altitude", state.rows[2].label)
        assertEquals("-", state.rows[2].min)
        assertEquals("300", state.rows[2].avg)
        assertEquals("-", state.rows[2].max)
    }

    @Test
    fun testExtremaUiState_emptyData_producesEmptyRows() {
        val extremaData = ExtremaData(workoutId = 1L, dataRows = emptyList())
        val state = ExtremaUiState.from(extremaData)
        assertTrue(state.rows.isEmpty())
    }
}
