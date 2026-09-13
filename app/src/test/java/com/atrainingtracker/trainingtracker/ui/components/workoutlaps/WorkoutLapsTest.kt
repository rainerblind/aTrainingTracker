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

package com.atrainingtracker.trainingtracker.ui.components.workoutlaps

import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsHelper.PerformanceBadge
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying WorkoutLaps logic: rabbit/hedgehog badges, lap naming,
 * descriptions, and expandable list threshold (REQ-UI-141, TST-UI-094, ATT-510).
 */
class WorkoutLapsTest {

    @Test
    fun testSingleLap_noBadgesAwarded() {
        val laps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.33)
        )

        val badges = WorkoutLapsHelper.determineBadges(laps)
        assertTrue("Single lap must not receive any performance highlight badges", badges.isEmpty())
    }

    @Test
    fun testMultipleLaps_differingSpeeds_awardsFastestRabbitAndSlowestHedgehog() {
        val laps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 320, distanceTotalM = 1000.0, speedAverageMps = 3.125),
            LapData(id = 2, workoutId = 10, lapNr = 2, timeTotalS = 250, distanceTotalM = 1000.0, speedAverageMps = 4.0),
            LapData(id = 3, workoutId = 10, lapNr = 3, timeTotalS = 400, distanceTotalM = 1000.0, speedAverageMps = 2.5)
        )

        val badges = WorkoutLapsHelper.determineBadges(laps)

        assertEquals(PerformanceBadge.NONE, badges[1])
        assertEquals(PerformanceBadge.FASTEST_RABBIT, badges[2])
        assertEquals(PerformanceBadge.SLOWEST_HEDGEHOG, badges[3])
    }

    @Test
    fun testMultipleLaps_identicalSpeeds_noBadgesAwarded() {
        val laps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.33),
            LapData(id = 2, workoutId = 10, lapNr = 2, timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.33),
            LapData(id = 3, workoutId = 10, lapNr = 3, timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.33)
        )

        val badges = WorkoutLapsHelper.determineBadges(laps)
        assertTrue("Identical speed laps must not display rabbit or hedgehog badges", badges.isEmpty())
    }

    @Test
    fun testZeroSpeedLaps_ignoredInBadgeCalculations() {
        val laps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 300, distanceTotalM = 0.0, speedAverageMps = 0.0),
            LapData(id = 2, workoutId = 10, lapNr = 2, timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.33)
        )

        val badges = WorkoutLapsHelper.determineBadges(laps)
        assertTrue("When only one non-zero speed exists, no badges should be awarded", badges.isEmpty())
    }

    @Test
    fun testLapDisplayName_fallbackWhenNullOrBlank() {
        val defaultLap = LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0)
        assertEquals("Lap 1", defaultLap.getDisplayName(1))

        val emptyNameLap = LapData(id = 2, workoutId = 10, lapNr = 2, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0, name = "")
        assertEquals("Lap 2", emptyNameLap.getDisplayName(2))

        val blankNameLap = LapData(id = 3, workoutId = 10, lapNr = 3, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0, name = "   ")
        assertEquals("Lap 3", blankNameLap.getDisplayName(3))

        val customNameLap = LapData(id = 4, workoutId = 10, lapNr = 4, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0, name = "Hill Sprint")
        assertEquals("Hill Sprint", customNameLap.getDisplayName(4))
    }

    @Test
    fun testLapDescription_preservesCustomText() {
        val lapWithDesc = LapData(
            id = 1,
            workoutId = 10,
            lapNr = 1,
            timeTotalS = 200,
            distanceTotalM = 800.0,
            speedAverageMps = 4.0,
            name = "Interval 1",
            description = "Target HR > 170 bpm"
        )
        assertEquals("Target HR > 170 bpm", lapWithDesc.description)
    }

    @Test
    fun testDisplayedLaps_collapsibleThreshold() {
        val fiveLaps = (1..5).map { index ->
            LapData(
                id = index.toLong(),
                workoutId = 20,
                lapNr = index.toLong(),
                timeTotalS = 300,
                distanceTotalM = 1000.0,
                speedAverageMps = 3.0 + index * 0.1
            )
        }

        // When collapsed (>3 laps), only first 3 are displayed
        val collapsed = WorkoutLapsHelper.getDisplayedLaps(fiveLaps, isExpanded = false)
        assertEquals(3, collapsed.size)
        assertEquals(1L, collapsed[0].lapNr)
        assertEquals(3L, collapsed[2].lapNr)

        // When expanded, all 5 laps are displayed
        val expanded = WorkoutLapsHelper.getDisplayedLaps(fiveLaps, isExpanded = true)
        assertEquals(5, expanded.size)

        // When <= 3 laps, all laps displayed regardless of isExpanded state
        val twoLaps = fiveLaps.take(2)
        assertEquals(2, WorkoutLapsHelper.getDisplayedLaps(twoLaps, isExpanded = false).size)
        assertEquals(2, WorkoutLapsHelper.getDisplayedLaps(twoLaps, isExpanded = true).size)

        // When empty list, returns empty list
        assertTrue(WorkoutLapsHelper.getDisplayedLaps(emptyList(), isExpanded = false).isEmpty())
    }

    @Test
    fun testColumnWeightsAndBadgeWidth_constantsIntegrity() {
        assertEquals("Lap name column weight must be 1.6f for expanded room", 1.6f, WorkoutLapsHelper.WEIGHT_LAP_NAME, 0.001f)
        assertEquals("Time column weight must be 0.85f", 0.85f, WorkoutLapsHelper.WEIGHT_TIME, 0.001f)
        assertEquals("Distance column weight must be 0.85f", 0.85f, WorkoutLapsHelper.WEIGHT_DISTANCE, 0.001f)
        assertEquals("Pace/Speed column weight must be 1.1f", 1.1f, WorkoutLapsHelper.WEIGHT_PACE_SPEED, 0.001f)
        assertEquals("Badge Box width must be 26dp", 26, WorkoutLapsHelper.BADGE_WIDTH_DP)
    }

    @Test
    fun testNumericalFormatting_pureNumbersWithoutEmbeddedUnits() {
        val mockPrefs = io.mockk.mockk<android.content.SharedPreferences>(relaxed = true)
        io.mockk.every { mockPrefs.getString(com.atrainingtracker.trainingtracker.TrainingApplication.SP_UNITS, any()) } returns "METRIC"
        val field = com.atrainingtracker.trainingtracker.TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        field.isAccessible = true
        val originalPrefs = field.get(null)
        field.set(null, mockPrefs)

        try {
            val paceFormatter = com.atrainingtracker.banalservice.sensor.formater.PaceFormatter()
            val speedFormatter = com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter()

            // 3.3333 m/s ≈ 12 km/h ≈ 5:00 min/km
            val paceNumeric = paceFormatter.format(1.0 / 3.3333333333333335)
            assertFalse("Pace cell must not contain unit text min/km", paceNumeric.contains("min/km"))
            assertFalse("Pace cell must not contain unit text min/mile", paceNumeric.contains("min/mile"))
            assertTrue("Pace numeric string must match mm:ss format: $paceNumeric", paceNumeric.matches(Regex("\\d+:\\d{2}")))

            // 5.0 m/s = 18.0 km/h
            val speedNumeric = speedFormatter.format(5.0)
            assertFalse("Speed cell must not contain unit text km/h", speedNumeric.contains("km/h"))
            assertFalse("Speed cell must not contain unit text mile/h", speedNumeric.contains("mile/h"))
            assertTrue("Speed numeric string must be pure decimal number: $speedNumeric", speedNumeric.matches(Regex("\\d+[.,]\\d+")))
        } finally {
            field.set(null, originalPrefs)
        }
    }
}
