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
}
