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
 * Unit and contract tests for DailyActivityStrip pure calculation helpers (REQ-PER-027, TST-PER-035).
 */
class DailyActivityStripTest {

    /**
     * TST-PER-035.2: Date Sequence Generation for Week and Month ranges (REQ-PER-027.3).
     */
    @Test
    fun testGenerateDateSequence_weekAndMonth() {
        // 7-day week
        val weekStart = LocalDate.of(2026, 5, 11) // Monday
        val weekEnd = LocalDate.of(2026, 5, 17)   // Sunday
        val weekDates = generateDateSequence(weekStart, weekEnd)
        assertEquals(7, weekDates.size)
        assertEquals(weekStart, weekDates.first())
        assertEquals(weekEnd, weekDates.last())

        // Standard February (28 days)
        val feb2025Start = LocalDate.of(2025, 2, 1)
        val feb2025End = LocalDate.of(2025, 2, 28)
        val feb2025Dates = generateDateSequence(feb2025Start, feb2025End)
        assertEquals(28, feb2025Dates.size)

        // Leap February (29 days)
        val feb2024Start = LocalDate.of(2024, 2, 1)
        val feb2024End = LocalDate.of(2024, 2, 29)
        val feb2024Dates = generateDateSequence(feb2024Start, feb2024End)
        assertEquals(29, feb2024Dates.size)

        // 31-day month
        val mayStart = LocalDate.of(2026, 5, 1)
        val mayEnd = LocalDate.of(2026, 5, 31)
        val mayDates = generateDateSequence(mayStart, mayEnd)
        assertEquals(31, mayDates.size)

        // Inverted range
        val invertedDates = generateDateSequence(weekEnd, weekStart)
        assertTrue(invertedDates.isEmpty())
    }

    /**
     * TST-PER-035.3: Proportional Bar Fraction Scaling (REQ-PER-027.4).
     */
    @Test
    fun testCalculateDailyBarFraction_scalingAndClamping() {
        // Zero or negative values
        assertEquals(0f, calculateDailyBarFraction(0.0, 100.0), 0.001f)
        assertEquals(0f, calculateDailyBarFraction(-5.0, 100.0), 0.001f)
        assertEquals(0f, calculateDailyBarFraction(50.0, 0.0), 0.001f)
        assertEquals(0f, calculateDailyBarFraction(50.0, -10.0), 0.001f)

        // Proportional mid values
        assertEquals(0.5f, calculateDailyBarFraction(50.0, 100.0), 0.001f)
        assertEquals(0.75f, calculateDailyBarFraction(75.0, 100.0), 0.001f)

        // Peak / max value clamped to 1.0f
        assertEquals(1.0f, calculateDailyBarFraction(100.0, 100.0), 0.001f)
        assertEquals(1.0f, calculateDailyBarFraction(150.0, 100.0), 0.001f)

        // Small positive values clamped to 0.15f floor for minimum visibility
        assertEquals(0.15f, calculateDailyBarFraction(1.0, 100.0), 0.001f)
        assertEquals(0.15f, calculateDailyBarFraction(0.01, 100.0), 0.001f)
    }

    /**
     * TST-PER-035.3: Metric Extraction Helpers (REQ-PER-027.4).
     */
    @Test
    fun testExtractDailyMetricValue_allMetrics() {
        val activity = DailySportActivity(
            date = LocalDate.of(2026, 5, 12),
            bSportType = BSportType.BIKE,
            count = 1,
            durationSec = 3600L,
            distanceMeters = 25000.0,
            ascentMeters = 350L,
            workoutIds = listOf(101L),
            workoutNames = listOf("Morning Ride")
        )

        assertEquals(3600.0, extractDailyMetricValue(activity, PeriodBarMetric.DURATION), 0.001)
        assertEquals(25000.0, extractDailyMetricValue(activity, PeriodBarMetric.DISTANCE), 0.001)
        assertEquals(350.0, extractDailyMetricValue(activity, PeriodBarMetric.ASCENT), 0.001)

        // Null activity
        assertEquals(0.0, extractDailyMetricValue(null, PeriodBarMetric.DURATION), 0.001)
        assertEquals(0.0, extractDailyMetricValue(null, PeriodBarMetric.DISTANCE), 0.001)
        assertEquals(0.0, extractDailyMetricValue(null, PeriodBarMetric.ASCENT), 0.001)
    }

    /**
     * TST-PER-035.3: Max Metric Extraction Helpers (REQ-PER-027.4).
     */
    @Test
    fun testExtractMaxDailyMetricValue_allMetrics() {
        val contribution = DailySportContribution(
            sportType = BSportType.RUN,
            dailyActivities = emptyMap(),
            maxDailyDurationSec = 5400L,
            maxDailyDistanceMeters = 16000.0,
            maxDailyAscentMeters = 120L
        )

        assertEquals(5400.0, extractMaxDailyMetricValue(contribution, PeriodBarMetric.DURATION), 0.001)
        assertEquals(16000.0, extractMaxDailyMetricValue(contribution, PeriodBarMetric.DISTANCE), 0.001)
        assertEquals(120.0, extractMaxDailyMetricValue(contribution, PeriodBarMetric.ASCENT), 0.001)

        // Null contribution
        assertEquals(0.0, extractMaxDailyMetricValue(null, PeriodBarMetric.DURATION), 0.001)
        assertEquals(0.0, extractMaxDailyMetricValue(null, PeriodBarMetric.DISTANCE), 0.001)
        assertEquals(0.0, extractMaxDailyMetricValue(null, PeriodBarMetric.ASCENT), 0.001)
    }

    /**
     * TST-PER-035.5: Domain Model Integrity (REQ-PER-027.2).
     */
    @Test
    fun testDailySportContributionDataModelDefaults() {
        val contribution = DailySportContribution(sportType = BSportType.BIKE)
        assertEquals(BSportType.BIKE, contribution.sportType)
        assertTrue(contribution.dailyActivities.isEmpty())
        assertEquals(0L, contribution.maxDailyDurationSec)
        assertEquals(0.0, contribution.maxDailyDistanceMeters, 0.001)
        assertEquals(0L, contribution.maxDailyAscentMeters)
    }
}
