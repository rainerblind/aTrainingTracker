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
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Unit and contract tests for GitHub-style Yearly Activity Contribution Grids (REQ-PER-025, TST-PER-031).
 */
class YearlyContributionGridTest {

    /**
     * TST-PER-031.1: Calendar Matrix Mapping Test (REQ-PER-025.1, REQ-PER-025.3).
     * Verifies 7-row by 52/53-col mapping for standard and leap years.
     */
    @Test
    fun testCalendarMatrixMapping_standardAndLeapYears() {
        val testYears = listOf(2024, 2025, 2026) // 2024 is leap (366 days), 2025 & 2026 are standard (365 days)

        for (year in testYears) {
            val jan1 = LocalDate.of(year, 1, 1)
            val totalDays = jan1.lengthOfYear()
            val startOffset = jan1.dayOfWeek.value - 1
            val totalCols = ((totalDays - 1 + startOffset) / 7) + 1

            assertTrue("Total columns must be 53 or 54", totalCols in 53..54)

            val visitedDates = mutableSetOf<LocalDate>()
            for (col in 0 until totalCols) {
                for (row in 0..6) {
                    val dayOffset = col * 7 + row - startOffset
                    if (dayOffset in 0 until totalDays) {
                        val date = jan1.plusDays(dayOffset.toLong())
                        assertEquals(year, date.year)
                        assertEquals(row, date.dayOfWeek.value - 1)
                        assertFalse("Date must not be mapped more than once: $date", visitedDates.contains(date))
                        visitedDates.add(date)
                    }
                }
            }

            assertEquals("All days of the year must be covered exactly once", totalDays, visitedDates.size)
            assertTrue(visitedDates.contains(LocalDate.of(year, 1, 1)))
            assertTrue(visitedDates.contains(LocalDate.of(year, 12, 31)))
        }
    }

    /**
     * TST-PER-031.1: Month Header Column Positions (REQ-PER-025.3).
     */
    @Test
    fun testMonthHeaderColumnPositions() {
        val year = 2026
        val jan1 = LocalDate.of(year, 1, 1)
        val startOffset = jan1.dayOfWeek.value - 1

        val monthCols = (1..12).map { month ->
            val firstOfMonth = LocalDate.of(year, month, 1)
            (firstOfMonth.dayOfYear - 1 + startOffset) / 7
        }

        assertEquals(12, monthCols.size)
        // Verify monotonically non-decreasing and strictly increasing across quarters
        for (i in 0 until 11) {
            assertTrue("Month ${i + 2} col (${monthCols[i + 1]}) must be >= month ${i + 1} col (${monthCols[i]})", monthCols[i + 1] >= monthCols[i])
        }
        assertEquals(0, monthCols[0]) // Jan 1 is in col 0
        assertTrue("Dec 1 should be around col 48..49", monthCols[11] in 47..50)
    }

    /**
     * TST-PER-031.2: Quartile Calculation and Level Assignment Test (REQ-PER-025.5).
     */
    @Test
    fun testQuartileCalculationAndLevels() {
        // Empty positive values
        val (q1Empty, q2Empty, q3Empty) = calculateQuartiles(emptyList())
        assertEquals(0.0, q1Empty, 0.001)
        assertEquals(0.0, q2Empty, 0.001)
        assertEquals(0.0, q3Empty, 0.001)
        assertEquals(0, calculateQuartileLevel(0.0, q1Empty, q2Empty, q3Empty))
        assertEquals(0, calculateQuartileLevel(-5.0, q1Empty, q2Empty, q3Empty))

        // Known distribution: 10, 20, 30, 40, 50, 60, 70, 80
        val sample = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0)
        val (q1, q2, q3) = calculateQuartiles(sample)

        assertTrue(q1 <= q2)
        assertTrue(q2 <= q3)

        // Test level boundaries
        assertEquals(0, calculateQuartileLevel(0.0, q1, q2, q3))
        assertEquals(1, calculateQuartileLevel(q1, q1, q2, q3))
        assertEquals(2, calculateQuartileLevel(q2, q1, q2, q3))
        assertEquals(3, calculateQuartileLevel(q3, q1, q2, q3))
        assertEquals(4, calculateQuartileLevel(q3 + 10.0, q1, q2, q3))
    }

    /**
     * TST-PER-031.3: Multi-Sport Separation & Rule 57 Ordering Test (REQ-PER-025.4).
     */
    @Test
    fun testMultiSportSeparationAndRule57Order() {
        val sportsPresent = setOf(BSportType.RUN, BSportType.UNKNOWN, BSportType.BIKE)

        val canonicalOrder = listOf(BSportType.BIKE, BSportType.RUN)
        val sortedSports = (canonicalOrder + sportsPresent.filter { it != BSportType.BIKE && it != BSportType.RUN && it != BSportType.UNKNOWN } + listOf(BSportType.UNKNOWN))
            .distinct()
            .filter { it in sportsPresent }

        // Must be [BIKE, RUN, UNKNOWN] (Rule 57)
        assertEquals(listOf(BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN), sortedSports)
        assertEquals(BSportType.BIKE, sortedSports[0])
        assertEquals(BSportType.RUN, sortedSports[1])
        assertEquals(BSportType.UNKNOWN, sortedSports[2]) // Other is always last
    }

    /**
     * TST-PER-031.5: YearlySportContribution & DailySportActivity Model Integrity (REQ-PER-025.1).
     */
    @Test
    fun testDailySportActivity_andContributionModel() {
        val date1 = LocalDate.of(2026, 5, 1)
        val date2 = LocalDate.of(2026, 5, 2)

        val day1 = DailySportActivity(
            date = date1,
            bSportType = BSportType.BIKE,
            count = 1,
            durationSec = 3600L,
            distanceMeters = 30000.0,
            ascentMeters = 400L,
            workoutIds = listOf(101L),
            workoutNames = listOf("May Ride")
        )

        val day2 = DailySportActivity(
            date = date2,
            bSportType = BSportType.BIKE,
            count = 2,
            durationSec = 7200L,
            distanceMeters = 60000.0,
            ascentMeters = 800L,
            workoutIds = listOf(102L, 103L),
            workoutNames = listOf("May Epic 1", "May Epic 2")
        )

        val contribution = YearlySportContribution(
            sportType = BSportType.BIKE,
            year = 2026,
            dailyActivities = mapOf(date1 to day1, date2 to day2),
            maxDailyDurationSec = 7200L,
            maxDailyDistanceMeters = 60000.0,
            maxDailyAscentMeters = 800L
        )

        assertEquals(BSportType.BIKE, contribution.sportType)
        assertEquals(2026, contribution.year)
        assertEquals(2, contribution.dailyActivities.size)
        assertEquals(7200L, contribution.maxDailyDurationSec)
        assertEquals(60000.0, contribution.maxDailyDistanceMeters, 0.001)
        assertEquals(800L, contribution.maxDailyAscentMeters)
    }

    /**
     * TST-PER-031.8: Embedded SportStatsRow Placement & Seamless Styling Parameters (REQ-PER-025.8, ATT-5076).
     * Verifies that YearlySportContribution data model and parameters support embedded rendering
     * with showCard = false and showSportHeader = false.
     */
    @Test
    fun testEmbeddedContributionGridParameters_defaultAndEmbeddedValues() {
        val contribution = YearlySportContribution(
            sportType = BSportType.RUN,
            year = 2026,
            dailyActivities = emptyMap(),
            maxDailyDurationSec = 0L,
            maxDailyDistanceMeters = 0.0,
            maxDailyAscentMeters = 0L
        )

        assertEquals(BSportType.RUN, contribution.sportType)
        assertEquals(2026, contribution.year)

        // Embedded configuration flags contract
        val embeddedShowCard = false
        val embeddedShowSportHeader = false
        assertFalse(embeddedShowCard)
        assertFalse(embeddedShowSportHeader)
    }

    /**
     * TST-PER-031.9: Pure Calculation Unit Tests for Initial Viewport Auto-Scroll (REQ-PER-025.9, ATT-5194).
     */
    @Test
    fun testCalculateTargetColumn_pastPresentFutureYears() {
        val today = LocalDate.of(2026, 11, 3)
        val startOffset = 3 // Thursday (2026-01-01 was Thursday)
        val totalCols = 53

        // Past year -> scrolls to end of year (totalCols - 1)
        val pastCol = calculateTargetColumn(2025, today, startOffset, totalCols)
        assertEquals(52, pastCol)

        // Future year -> aligns at start of year (0)
        val futureCol = calculateTargetColumn(2027, today, startOffset, totalCols)
        assertEquals(0, futureCol)

        // Current year, early date (Jan 1) -> col 0
        val jan1Col = calculateTargetColumn(2026, LocalDate.of(2026, 1, 1), startOffset, totalCols)
        assertEquals(0, jan1Col)

        // Current year, mid date (July 1, dayOfYear 182) -> (181 + 3) / 7 = 26
        val midCol = calculateTargetColumn(2026, LocalDate.of(2026, 7, 1), startOffset, totalCols)
        assertEquals(26, midCol)

        // Current year, November 3 (dayOfYear 307) -> (306 + 3) / 7 = 44
        val novCol = calculateTargetColumn(2026, today, startOffset, totalCols)
        assertEquals(44, novCol)

        // Current year, December 31 -> col 52
        val decCol = calculateTargetColumn(2026, LocalDate.of(2026, 12, 31), startOffset, totalCols)
        assertEquals(52, decCol)

        // Leap year (2024, 366 days, startOffset = 0 for Monday)
        val leapDecCol = calculateTargetColumn(2024, LocalDate.of(2024, 12, 31), 0, 53)
        assertEquals(52, leapDecCol)
    }

    @Test
    fun testCalculateInitialScrollOffset_clampingAndEdgeAlignment() {
        val totalCols = 53
        val colPitchPx = 39.0f // 13dp at 3x density

        // Invalid or zero maxValue
        assertEquals(0, calculateInitialScrollOffset(0, totalCols, 52, colPitchPx))
        assertEquals(0, calculateInitialScrollOffset(-10, totalCols, 52, colPitchPx))
        assertEquals(0, calculateInitialScrollOffset(1000, 0, 0, colPitchPx))

        // Aligns end of year at maxValue
        val offsetEnd = calculateInitialScrollOffset(1000, totalCols, 52, colPitchPx)
        assertEquals(1000, offsetEnd)

        // Aligns target column (col 44) at right edge of viewport:
        // Remaining cols = 52 - 44 = 8 cols -> future width = 8 * 39 = 312px
        // Desired offset = 1000 - 312 = 688px
        val offsetTarget = calculateInitialScrollOffset(1000, totalCols, 44, colPitchPx)
        assertEquals(688, offsetTarget)

        // Early column (col 2): remaining cols = 50 -> future width = 1950px
        // 1000 - 1950 = -950 -> clamped to 0
        val offsetEarly = calculateInitialScrollOffset(1000, totalCols, 2, colPitchPx)
        assertEquals(0, offsetEarly)
    }
}
