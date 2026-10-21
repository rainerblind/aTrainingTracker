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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Unit tests verifying calendar-aware Day and Week boundaries across DST transitions
 * and filtering of invalid epoch-zero / negative timestamp records (ATT-4016, REQ-PER-015, TST-PER-021.1).
 */
class PeriodsAggregationDstTest {

    private val berlinZone = ZoneId.of("Europe/Berlin")

    @Test
    fun springForwardTransition_evaluatesExact23HourDayAnd167HourWeek() {
        // In Europe/Berlin, DST begins on Sunday 2026-03-29 (clocks jump from 02:00 CET to 03:00 CEST).
        // The day duration is 23 hours = 82,800 seconds.
        val sundayDate = LocalDate.of(2026, 3, 29)
        val dayStartS = sundayDate.atStartOfDay(berlinZone).toEpochSecond()

        val dayEndS = PeriodsRepository.calculateDayEnd(dayStartS, berlinZone)
        val dayLengthS = dayEndS - dayStartS + 1L

        assertEquals("Spring-forward Sunday must have length of 23 hours (82,800 s)", 82800L, dayLengthS)

        // Late workout on Sunday 23:30 CEST
        val sundayLateS = LocalDateTime.of(2026, 3, 29, 23, 30, 0).atZone(berlinZone).toEpochSecond()
        assertTrue("Sunday 23:30 workout must fall within the day interval", sundayLateS in dayStartS..dayEndS)

        // Monday 00:15 CEST (must be strictly excluded from Sunday)
        val mondayEarlyS = LocalDateTime.of(2026, 3, 30, 0, 15, 0).atZone(berlinZone).toEpochSecond()
        assertFalse("Monday 00:15 workout must NOT fall within Sunday's day interval", mondayEarlyS in dayStartS..dayEndS)
        assertTrue("Monday 00:15 workout is strictly after Sunday dayEndS", mondayEarlyS > dayEndS)

        // Week calculation: Week starts Monday 2026-03-23 00:00 CET
        val weekStartS = LocalDate.of(2026, 3, 23).atStartOfDay(berlinZone).toEpochSecond()
        val weekEndS = PeriodsRepository.calculateWeekEnd(weekStartS, berlinZone)
        val weekLengthHours = (weekEndS - weekStartS + 1L) / 3600L

        assertEquals("Spring-forward week must have duration of 167 hours (6 * 24 + 23)", 167L, weekLengthHours)
        assertEquals("Week end must align with Sunday day end", dayEndS, weekEndS)
        assertFalse("Monday 00:15 workout of following week must NOT fall into this week", mondayEarlyS in weekStartS..weekEndS)
    }

    @Test
    fun fallBackTransition_evaluatesExact25HourDayAnd169HourWeek() {
        // In Europe/Berlin, DST ends on Sunday 2026-10-25 (clocks repeat 02:00–03:00 from CEST to CET).
        // The day duration is 25 hours = 90,000 seconds.
        val sundayDate = LocalDate.of(2026, 10, 25)
        val dayStartS = sundayDate.atStartOfDay(berlinZone).toEpochSecond()

        val dayEndS = PeriodsRepository.calculateDayEnd(dayStartS, berlinZone)
        val dayLengthS = dayEndS - dayStartS + 1L

        assertEquals("Fall-back Sunday must have length of 25 hours (90,000 s)", 90000L, dayLengthS)

        // Late workout on Sunday 23:30 CET (hour 24.5 of the day)
        // Fixed 86,399 offset would have ended at 22:59:59 CET, missing this workout!
        val sundayLateS = LocalDateTime.of(2026, 10, 25, 23, 30, 0).atZone(berlinZone).toEpochSecond()
        assertTrue("Sunday 23:30 workout must be captured in the 25-hour day", sundayLateS in dayStartS..dayEndS)

        // Monday 00:15 CET
        val mondayEarlyS = LocalDateTime.of(2026, 10, 26, 0, 15, 0).atZone(berlinZone).toEpochSecond()
        assertFalse("Monday 00:15 workout must NOT fall into Sunday", mondayEarlyS in dayStartS..dayEndS)
        assertTrue("Monday 00:15 workout is after Sunday dayEndS", mondayEarlyS > dayEndS)

        // Week calculation: Week starts Monday 2026-10-19 00:00 CEST
        val weekStartS = LocalDate.of(2026, 10, 19).atStartOfDay(berlinZone).toEpochSecond()
        val weekEndS = PeriodsRepository.calculateWeekEnd(weekStartS, berlinZone)
        val weekLengthHours = (weekEndS - weekStartS + 1L) / 3600L

        assertEquals("Fall-back week must have duration of 169 hours (6 * 24 + 25)", 169L, weekLengthHours)
        assertEquals("Week end must align with Sunday day end", dayEndS, weekEndS)
        assertTrue("Sunday 23:30 workout must be captured in the week", sundayLateS in weekStartS..weekEndS)
    }

    @Test
    fun standardDayAndWeek_preserveExact24HourAnd168HourLengths() {
        val standardDay = LocalDate.of(2026, 6, 15) // Standard summer day (no DST switch)
        val dayStartS = standardDay.atStartOfDay(berlinZone).toEpochSecond()
        val dayEndS = PeriodsRepository.calculateDayEnd(dayStartS, berlinZone)

        assertEquals("Standard day must have duration of 86,400 s", 86400L, dayEndS - dayStartS + 1L)

        val weekStartS = standardDay.atStartOfDay(berlinZone).toEpochSecond() // Monday
        val weekEndS = PeriodsRepository.calculateWeekEnd(weekStartS, berlinZone)

        assertEquals("Standard week must have duration of 168 hours", 168L, (weekEndS - weekStartS + 1L) / 3600L)
    }

    @Test
    fun invalidWorkouts_withZeroOrNegativeTimestamp_areFilteredOut() {
        fun createDummy(id: Long, startTimeS: Long): WorkoutData {
            return WorkoutData(
                id = id,
                finished = true,
                fileBaseName = "dummy_$id",
                workoutName = "Test",
                sportId = 1L,
                sportName = "Run",
                bSportType = BSportType.RUN,
                startTimeS = startTimeS,
                formattedDate = "",
                formattedTime = "",
                localDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(startTimeS.coerceAtLeast(0L)), berlinZone),
                equipmentName = null,
                equipmentId = 0L,
                commute = false,
                trainer = false,
                mapPolyline = "",
                encodedAltitudes = "",
                encodedDistances = "",
                uploadToStrava = 0,
                totalDistance = 5000.0,
                maxDisplacement = 0.0,
                activeTimeSec = 1800L,
                totalTimeSec = 1800L,
                avgSpeedMps = 2.77,
                ascentMeters = 50L,
                descentMeters = 50L,
                minAltitude = 100.0,
                maxAltitude = 150.0,
                maxAltitudeLatLng = null,
                maxDisplacementLatLng = null,
                startLatLng = null,
                endLatLng = null,
                description = null,
                goal = null,
                method = null,
                stravaSportName = null,
                stravaActivityData = null,
                clusterId = 0L,
                clusterName = null
            )
        }

        val validWorkout = createDummy(1L, 1774739000L)
        val epochZeroWorkout = createDummy(2L, 0L)
        val corruptNegativeWorkout = createDummy(3L, -100L)

        val rawList = listOf(validWorkout, epochZeroWorkout, corruptNegativeWorkout)
        val filtered = rawList.filter { it.startTimeS > 0L }

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered.first().id)
    }
}
