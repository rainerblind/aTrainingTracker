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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class PeriodBarGraphTest {

    private val utcZone = ZoneOffset.UTC

    private fun createSamplePeriod(
        label: String,
        type: PeriodType,
        startS: Long,
        endS: Long,
        durationS: Long,
        distanceM: Double,
        ascentM: Long,
        sportStats: Map<BSportType, SportStats> = emptyMap()
    ): PeriodSummary {
        return PeriodSummary(
            periodLabel = label,
            periodDateRange = "",
            periodType = type,
            startTimestampS = startS,
            endTimestampS = endS,
            totalWorkouts = 1,
            totalDurationSec = durationS,
            totalDistance = distanceM,
            sportStats = sportStats,
            sortKey = label
        )
    }

    @Test
    fun testTimelineGapInterpolationInsertsRestDays() {
        // Day 1: 2026-10-20 00:00:00 UTC (1792454400)
        val day1StartS = 1792454400L
        // Day 4: 2026-10-23 00:00:00 UTC (Day 1 + 3 days = 1792454400 + 3 * 86400)
        val day4StartS = day1StartS + 3 * 86400L

        val pDay4 = createSamplePeriod("2026-10-23", PeriodType.DAY, day4StartS, day4StartS + 86400L, 3600L, 10000.0, 100L)
        val pDay1 = createSamplePeriod("2026-10-20", PeriodType.DAY, day1StartS, day1StartS + 86400L, 1800L, 5000.0, 50L)

        // Incoming list is descending: [Day 4 (idx 0), Day 1 (idx 1)]
        val input = listOf(pDay4, pDay1)

        val items = interpolateTimelineGaps(input, maxGapBuckets = 30, zoneId = utcZone)

        // Expect 4 chronological items: Day 1, Day 2 (Rest), Day 3 (Rest), Day 4
        assertEquals(4, items.size)

        // Item 0: Day 1
        assertFalse(items[0].isRest)
        assertEquals(1, items[0].originalIndex)
        assertEquals(day1StartS, items[0].startTimestampS)
        assertEquals(1800L, items[0].durationSec)

        // Item 1: Day 2 (Rest)
        assertTrue(items[1].isRest)
        assertNull(items[1].originalIndex)
        assertEquals(day1StartS + 86400L, items[1].startTimestampS)
        assertEquals(0L, items[1].durationSec)
        assertEquals(0.0, items[1].distanceMeters, 0.001)

        // Item 2: Day 3 (Rest)
        assertTrue(items[2].isRest)
        assertNull(items[2].originalIndex)
        assertEquals(day1StartS + 2 * 86400L, items[2].startTimestampS)
        assertEquals(0L, items[2].durationSec)

        // Item 3: Day 4
        assertFalse(items[3].isRest)
        assertEquals(0, items[3].originalIndex)
        assertEquals(day4StartS, items[3].startTimestampS)
        assertEquals(3600L, items[3].durationSec)
    }

    @Test
    fun testTimelineGapCappingPreventsRunawayAllocation() {
        val start1 = 1700000000L
        val start2 = start1 + 100 * 86400L // 100 days gap

        val p1 = createSamplePeriod("P1", PeriodType.DAY, start1, start1 + 86400L, 1000L, 5000.0, 50L)
        val p2 = createSamplePeriod("P2", PeriodType.DAY, start2, start2 + 86400L, 2000L, 10000.0, 100L)

        val items = interpolateTimelineGaps(listOf(p2, p1), maxGapBuckets = 10, zoneId = utcZone)

        // 2 active periods + at most 10 rest buckets = 12 items
        assertEquals(12, items.size)
    }

    @Test
    fun testSubHourDurationFormatting() {
        // Less than 60 seconds
        assertEquals("<1m", formatMetricValue(45.0, PeriodBarMetric.DURATION, PeriodType.DAY))

        // Sub-hour: 45 minutes (2700s)
        assertEquals("45m", formatMetricValue(2700.0, PeriodBarMetric.DURATION, PeriodType.DAY))

        // 1.5 hours: 90 minutes (5400s)
        assertEquals("1.5h", formatMetricValue(5400.0, PeriodBarMetric.DURATION, PeriodType.DAY))

        // Exact hours: 2 hours (7200s)
        assertEquals("2h", formatMetricValue(7200.0, PeriodBarMetric.DURATION, PeriodType.DAY))

        // Long duration: 12 hours (43200s)
        assertEquals("12h", formatMetricValue(43200.0, PeriodBarMetric.DURATION, PeriodType.DAY))
    }

    @Test
    fun testDistanceAndAscentMetricFormatting() {
        // Distance
        assertEquals("850m", formatMetricValue(850.0, PeriodBarMetric.DISTANCE, PeriodType.DAY))
        assertEquals("8.5k", formatMetricValue(8500.0, PeriodBarMetric.DISTANCE, PeriodType.DAY))
        assertEquals("42k", formatMetricValue(42195.0, PeriodBarMetric.DISTANCE, PeriodType.DAY))

        // Ascent
        assertEquals("350m", formatMetricValue(350.0, PeriodBarMetric.ASCENT, PeriodType.DAY))
        assertEquals("1250m", formatMetricValue(1250.0, PeriodBarMetric.ASCENT, PeriodType.DAY))
        assertEquals("", formatMetricValue(0.0, PeriodBarMetric.ASCENT, PeriodType.DAY))
    }

    @Test
    fun testXAxisPeriodLabelFormatting() {
        // 2026-10-23 12:00:00 UTC = 1792756800
        val timestamp = 1792756800L

        val dayLabel = formatPeriodXLabel(timestamp, PeriodType.DAY, Locale.US, utcZone)
        assertEquals("23.10", dayLabel)

        val weekLabel = formatPeriodXLabel(timestamp, PeriodType.WEEK, Locale.US, utcZone)
        assertTrue(weekLabel.startsWith("W"))

        val monthLabel = formatPeriodXLabel(timestamp, PeriodType.MONTH, Locale.US, utcZone)
        assertEquals("Oct", monthLabel)

        val yearLabel = formatPeriodXLabel(timestamp, PeriodType.YEAR, Locale.US, utcZone)
        assertEquals("2026", yearLabel)
    }

    @Test
    fun testSportSegmentsProportions() {
        val bikeStats = SportStats(1, 4000L, 30000.0, 300L, emptyMap(), null)
        val runStats = SportStats(1, 1000L, 5000.0, 50L, emptyMap(), null)

        val period = createSamplePeriod(
            label = "Sample",
            type = PeriodType.WEEK,
            startS = 1792454400L,
            endS = 1793059200L,
            durationS = 5000L,
            distanceM = 35000.0,
            ascentM = 350L,
            sportStats = mapOf(
                BSportType.BIKE to bikeStats,
                BSportType.RUN to runStats
            )
        )

        // DURATION metric
        val durationSegments = calculateSportSegments(period, PeriodBarMetric.DURATION)
        assertEquals(2, durationSegments.size)
        val bikeDurSeg = durationSegments.first { it.sportType == BSportType.BIKE }
        val runDurSeg = durationSegments.first { it.sportType == BSportType.RUN }
        assertEquals(0.8f, bikeDurSeg.fraction, 0.001f)
        assertEquals(0.2f, runDurSeg.fraction, 0.001f)
        assertEquals(BikeSportBarColor, bikeDurSeg.color)
        assertEquals(RunSportBarColor, runDurSeg.color)

        // DISTANCE metric
        val distSegments = calculateSportSegments(period, PeriodBarMetric.DISTANCE)
        assertEquals(2, distSegments.size)
        val bikeDistSeg = distSegments.first { it.sportType == BSportType.BIKE }
        assertEquals((30000.0 / 35000.0).toFloat(), bikeDistSeg.fraction, 0.001f)
    }

    @Test
    fun testEmptyAndBoundaryEdgeCases() {
        assertTrue(interpolateTimelineGaps(emptyList()).isEmpty())
        assertTrue(calculateSportSegments(null, PeriodBarMetric.DURATION).isEmpty())

        val single = listOf(
            createSamplePeriod("Single", PeriodType.DAY, 1792454400L, 1792540800L, 1800L, 5000.0, 50L)
        )
        val items = interpolateTimelineGaps(single)
        assertEquals(1, items.size)
        assertFalse(items[0].isRest)
        assertEquals(0, items[0].originalIndex)
    }
}
