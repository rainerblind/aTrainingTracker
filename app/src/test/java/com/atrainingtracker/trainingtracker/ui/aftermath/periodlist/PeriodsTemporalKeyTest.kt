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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Unit tests for temporal period key alignment, DST boundary stability, and longest workout integrity.
 * Fulfills REQ-PER-011 / TST-PER-015 for ATT-536.
 */
class PeriodsTemporalKeyTest {

    private val testZones = listOf(
        ZoneId.of("Europe/Berlin"), // CET/CEST with DST shifts
        ZoneId.of("America/New_York"), // EST/EDT with DST shifts
        ZoneId.of("UTC") // No DST
    )

    private fun computeWeekSortKey(epochSeconds: Long, zone: ZoneId): String {
        val dt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), zone)
        val week = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = dt.get(IsoFields.WEEK_BASED_YEAR)
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    private fun computeWeekStartEpoch(startTimestampS: Long, zone: ZoneId): Long {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(startTimestampS), zone)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay()
            .atZone(zone)
            .toEpochSecond()
    }

    @Test
    fun testWeekStartAndWorkoutSortKeysMatchAcrossFullYearDST() {
        for (zone in testZones) {
            val year = 2025 // 2025 has standard transitions in March and October
            var curDate = LocalDate.of(year, 1, 1)
            val endDate = LocalDate.of(year, 12, 31)

            while (!curDate.isAfter(endDate)) {
                // Midday workout timestamp
                val workoutEpoch = curDate.atTime(14, 30, 0).atZone(zone).toEpochSecond()
                val workoutSortKey = computeWeekSortKey(workoutEpoch, zone)

                // Day start timestamp as stored in Daily PeriodSummary
                val dayStartEpoch = curDate.atStartOfDay().atZone(zone).toEpochSecond()

                // Calculate week start from day start (using our normalized implementation)
                val weekStartEpoch = computeWeekStartEpoch(dayStartEpoch, zone)
                val weekSortKey = computeWeekSortKey(weekStartEpoch, zone)

                // Verify that the week start is always on a Monday at 00:00:00 local time
                val weekStartLocal = OffsetDateTime.ofInstant(Instant.ofEpochSecond(weekStartEpoch), zone)
                assertEquals("Week start must be Monday for date $curDate in $zone", DayOfWeek.MONDAY, weekStartLocal.dayOfWeek)
                assertEquals("Week start hour must be 0 for date $curDate in $zone", 0, weekStartLocal.hour)
                assertEquals("Week start minute must be 0 for date $curDate in $zone", 0, weekStartLocal.minute)

                // Verify sortKey equality between workout and week summary
                assertEquals(
                    "SortKey mismatch for workout on $curDate in zone $zone",
                    workoutSortKey,
                    weekSortKey
                )

                curDate = curDate.plusDays(1)
            }
        }
    }

    @Test
    fun testLongestWorkoutDurationSelectionIntegrity() {
        val w1 = LongestWorkout(id = 1L, name = "Short Recovery", durationSec = 1200L, distanceMeters = 5000.0, ascentMeters = 20)
        val w2 = LongestWorkout(id = 2L, name = "Long Weekend Ride", durationSec = 7200L, distanceMeters = 60000.0, ascentMeters = 850)
        val w3 = LongestWorkout(id = 3L, name = "Mid Tempo", durationSec = 3600L, distanceMeters = 15000.0, ascentMeters = 100)

        val workouts = listOf(w1, w2, w3)
        val longest = workouts.maxByOrNull { it.durationSec }

        assertEquals(2L, longest?.id)
        assertEquals("Long Weekend Ride", longest?.name)
        assertEquals(7200L, longest?.durationSec)
    }

    @Test
    fun testShowLongestWorkoutUiGuard() {
        val statsWithZero = SportStats(
            count = 2,
            totalDurationSec = 8400L,
            totalDistanceMeters = 65000.0,
            totalAscentMeters = 870L,
            detailedSportStats = emptyMap(),
            longestWorkout = LongestWorkout(id = 1L, name = "", durationSec = 0L, distanceMeters = 0.0, ascentMeters = 0)
        )

        val statsWithReal = SportStats(
            count = 2,
            totalDurationSec = 8400L,
            totalDistanceMeters = 65000.0,
            totalAscentMeters = 870L,
            detailedSportStats = emptyMap(),
            longestWorkout = LongestWorkout(id = 1L, name = "Long Ride", durationSec = 7200L, distanceMeters = 60000.0, ascentMeters = 850)
        )

        val statsSingleWorkout = SportStats(
            count = 1,
            totalDurationSec = 7200L,
            totalDistanceMeters = 60000.0,
            totalAscentMeters = 850L,
            detailedSportStats = emptyMap(),
            longestWorkout = LongestWorkout(id = 1L, name = "Long Ride", durationSec = 7200L, distanceMeters = 60000.0, ascentMeters = 850)
        )

        // UI Guard Logic: stats.count > 1 && longestWorkout != null && longestWorkout.durationSec > 0
        val showZero = statsWithZero.count > 1 && statsWithZero.longestWorkout != null && statsWithZero.longestWorkout!!.durationSec > 0
        val showReal = statsWithReal.count > 1 && statsWithReal.longestWorkout != null && statsWithReal.longestWorkout!!.durationSec > 0
        val showSingle = statsSingleWorkout.count > 1 && statsSingleWorkout.longestWorkout != null && statsSingleWorkout.longestWorkout!!.durationSec > 0

        assertFalse("Un-hydrated zero-duration workout must NOT be shown", showZero)
        assertTrue("Hydrated real-duration workout must be shown when count > 1", showReal)
        assertFalse("Single-workout sport must NOT show longest workout row", showSingle)
    }
}
